# RAG Intelligence Layer Design

## Overview

This document describes the AI/ML intelligence layer added to the Spring Boot application to provide **Retrieval-Augmented Generation (RAG)** capabilities. The design prioritizes:

- **Local-first execution** — no external API keys required for basic operation
- **Minimal dependencies** — leverages existing stack (SQLite, Jackson, Spring Web)
- **Clean architecture** — follows the project's Repository → Service → Controller pattern
- **Docker-native** — Ollama runs as a sidecar container with pre-pulled models

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              User Interface                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────────────┐  │
│  │ Book Catalog│  │ User Admin  │  │ RAG Chat (new)                      │  │
│  │  (existing) │  │  (existing) │  │  - Streaming chat UI                │  │
│  │             │  │             │  │  - Document upload / manage         │  │
│  │             │  │             │  │  - Conversation history             │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────────────┬──────────────────┘  │
└─────────┼────────────────┼────────────────────────────┼─────────────────────┘
          │                │                            │
┌─────────┼────────────────┼────────────────────────────┼─────────────────────┐
│         ▼                ▼                            ▼                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────────────┐  │
│  │ BookApiCtrl │  │ UserApiCtrl │  │ RagApiController                    │  │
│  │  (existing) │  │  (existing) │  │  POST /api/rag/chat                 │  │
│  │             │  │             │  │  GET  /api/rag/conversations        │  │
│  │             │  │             │  │  POST /api/rag/documents            │  │
│  │             │  │             │  │  GET  /api/rag/documents            │  │
│  │             │  │             │  │  DELETE /api/rag/documents/{id}     │  │
│  └──────┬──────┘  └──────┬──────┘  └──────────────────┬──────────────────┘  │
│         │                │                            │                     │
│  ┌──────┴────────────────┴────────────────────────────┴──────────────────┐  │
│  │                           Service Layer                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐   │  │
│  │  │ BookService │  │ UserService │  │ RagService                  │   │  │
│  │  │  (existing) │  │  (existing) │  │  - ingestDocument()         │   │  │
│  │  │             │  │             │  │  - chat()                   │   │  │
│  │  │             │  │             │  │  - retrieveContext()        │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┬───────────────┘   │  │
│  │                                                  │                   │  │
│  │  ┌─────────────────────────────┐  ┌──────────────┴────────────────┐  │  │
│  │  │ DocumentIngestionService    │  │ OllamaEmbeddingService        │  │  │
│  │  │  - chunkText()              │  │  - generateEmbedding()        │  │  │
│  │  │  - indexChunks()            │  │  - embedBatch()               │  │  │
│  │  └─────────────────────────────┘  └───────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         Repository Layer                              │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐   │  │
│  │  │ BookRepo    │  │ UserRepo    │  │ DocumentRepository          │   │  │
│  │  │  (existing) │  │  (existing) │  │  - save(doc)                │   │  │
│  │  │             │  │             │  │  - findById(id)             │   │  │
│  │  │             │  │             │  │  - searchSimilar(embedding) │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────────┘   │  │
│  │                                                                      │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐ │  │
│  │  │ SqliteVectorStore                                               │ │  │
│  │  │  - cosineSimilarity(queryVector, topK) → List<Chunk>            │ │  │
│  │  │  - Stored in SQLite BLOB columns with optimized dot-product    │ │  │
│  │  └─────────────────────────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         Data Layer                                    │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐   │  │
│  │  │ SQLite      │  │ SQLite      │  │ SQLite (RAG tables)         │   │  │
│  │  │ users       │  │ books       │  │ documents                   │   │  │
│  │  │ api_keys    │  │ audit_logs  │  │ document_chunks (vectors)   │   │  │
│  │  │ webhooks    │  │ usage_events│  │ rag_conversations           │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────────┘   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  External Service (Docker sidecar)                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │ Ollama (http://ollama:11434)                                        │   │
│  │  - Model: llama3.2 (3B params, fast, good for RAG)                  │   │
│  │  - /api/embeddings  → text → vector (3072d)                         │   │
│  │  - /api/generate    → prompt + context → response                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Data Model

### documents
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| title | TEXT | Document title |
| source | TEXT | Source identifier (filename, URL) |
| content | TEXT | Full text content |
| content_type | TEXT | `text/plain`, `text/markdown`, etc. |
| chunk_count | INTEGER | Number of chunks generated |
| created_by | TEXT | User email who uploaded |
| created_at | TEXT | ISO timestamp |

### document_chunks
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| document_id | INTEGER FK | Parent document |
| chunk_index | INTEGER | Position in document |
| content | TEXT | Chunk text |
| embedding | BLOB | Serialized float[] vector |
| vector_dim | INTEGER | Dimension count (e.g., 3072) |

### rag_conversations
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| session_id | TEXT UUID | Client session identifier |
| user_email | TEXT | Authenticated user |
| title | TEXT | Auto-generated from first query |
| created_at | TEXT | ISO timestamp |
| updated_at | TEXT | ISO timestamp |

### rag_messages
| Column | Type | Description |
|--------|------|-------------|
| id | INTEGER PK | Auto-increment |
| conversation_id | INTEGER FK | Parent conversation |
| role | TEXT | `user` or `assistant` |
| content | TEXT | Message text |
| sources | TEXT | JSON array of cited chunk IDs |
| created_at | TEXT | ISO timestamp |

---

## RAG Pipeline Flow

### 1. Document Ingestion
```
Upload text → Chunk by paragraphs (500 chars, 50 overlap)
              → For each chunk: call Ollama /api/embeddings
              → Store chunk + embedding BLOB in document_chunks
```

### 2. Chat Query
```
User question → Generate embedding for question
              → SqliteVectorStore.cosineSimilarity(queryEmbedding, topK=5)
              → Build prompt: [system] + [retrieved chunks] + [question]
              → POST to Ollama /api/generate with stream=true
              → Stream tokens back to client via SSE
              → Store conversation + message history
```

### 3. Prompt Template
```
You are a helpful assistant with access to the following documents.
Answer based ONLY on the provided context. If the answer is not in the
context, say "I don't have enough information to answer that."

Context:
---
[chunk 1 from "The Pragmatic Programmer"]
---
[chunk 2 from "Clean Code"]
---

Question: What are the best practices for writing clean code?

Answer:
```

---

## Security

- All `/api/rag/**` endpoints require authentication (leverages existing SecurityConfig)
- Document upload restricted to ADMIN or configurable role
- Conversations scoped to `user_email` — users cannot see others' chats
- Audit log integration: `rag.query` and `rag.document.upload` events

---

## Configuration

```properties
# --- RAG / LLM ---
rag.ollama.base-url=http://localhost:11434
rag.ollama.model=llama3.2
rag.ollama.embedding-model=nomic-embed-text
rag.ollama.timeout-seconds=60
rag.chunk.size=500
rag.chunk.overlap=50
rag.retrieval.top-k=5
rag.retrieval.similarity-threshold=0.7
```

---

## Docker Compose

Ollama runs as a named service. On first startup it pulls the configured model:

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama-data:/root/.ollama
    ports:
      - "11434:11434"
```

---

## Technology Choices

| Concern | Choice | Rationale |
|---------|--------|-----------|
| LLM runtime | Ollama | Local, no API keys, Docker-native, CPU-friendly 3B model |
| Embeddings | nomic-embed-text via Ollama | High quality, 768/3072d, Apache-2.0 |
| Vector store | SQLite BLOB + Kotlin cosine sim | Zero new infrastructure, leverages existing DB |
| HTTP client | Spring RestClient | Modern, fluent, part of Spring 6.1+ |
| Streaming | SSE (text/event-stream) | Simple, works with EventSource, no WebSocket needed |
| JSON | Jackson (existing) | No new dependency |
