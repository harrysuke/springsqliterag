-- ---------------------------------------------------------------------------
-- RAG (Retrieval-Augmented Generation) tables
-- Documents, chunks with embeddings, and conversation history.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Documents — uploaded knowledge-base sources.
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    title       TEXT    NOT NULL,
    source      TEXT,
    content     TEXT    NOT NULL,
    content_type TEXT   NOT NULL DEFAULT 'text/plain',
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_by  TEXT    NOT NULL,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_documents_created_by ON documents(created_by);
CREATE INDEX idx_documents_created_at ON documents(created_at);

-- ---------------------------------------------------------------------------
-- Document Chunks — semantic slices with pre-computed embedding vectors.
-- embedding is stored as a BLOB of little-endian 32-bit floats.
-- vector_dim records how many floats are in the BLOB.
-- ---------------------------------------------------------------------------
CREATE TABLE document_chunks (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id   INTEGER NOT NULL,
    chunk_index   INTEGER NOT NULL,
    content       TEXT    NOT NULL,
    embedding     BLOB    NOT NULL,
    vector_dim    INTEGER NOT NULL,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);
CREATE INDEX idx_document_chunks_doc_id ON document_chunks(document_id);
CREATE INDEX idx_document_chunks_chunk_index ON document_chunks(document_id, chunk_index);

-- ---------------------------------------------------------------------------
-- RAG Conversations — user chat sessions.
-- ---------------------------------------------------------------------------
CREATE TABLE rag_conversations (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  TEXT    NOT NULL UNIQUE,
    user_email  TEXT    NOT NULL,
    title       TEXT,
    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX idx_rag_conversations_user ON rag_conversations(user_email);
CREATE INDEX idx_rag_conversations_session ON rag_conversations(session_id);

-- ---------------------------------------------------------------------------
-- RAG Messages — individual turns within a conversation.
-- sources is a JSON array of chunk ids cited by the assistant.
-- ---------------------------------------------------------------------------
CREATE TABLE rag_messages (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id INTEGER NOT NULL,
    role            TEXT    NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content         TEXT    NOT NULL,
    sources         TEXT,
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (conversation_id) REFERENCES rag_conversations(id) ON DELETE CASCADE
);
CREATE INDEX idx_rag_messages_conversation ON rag_messages(conversation_id);
CREATE INDEX idx_rag_messages_created_at ON rag_messages(created_at);
