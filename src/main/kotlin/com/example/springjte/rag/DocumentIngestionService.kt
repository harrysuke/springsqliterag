package com.example.springjte.rag

import com.example.springjte.model.Document
import com.example.springjte.model.DocumentChunk
import com.example.springjte.repository.DocumentChunkRepository
import com.example.springjte.repository.DocumentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Ingests plain-text documents into the RAG knowledge base by
 * chunking, embedding, and persisting them.
 */
@Service
class DocumentIngestionService(
    private val documentRepo: DocumentRepository,
    private val chunkRepo: DocumentChunkRepository,
    private val embeddingService: OllamaEmbeddingService,
    @Value("\${rag.chunk.size:500}") private val chunkSize: Int,
    @Value("\${rag.chunk.overlap:50}") private val overlap: Int
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun ingest(
        title: String,
        source: String?,
        content: String,
        contentType: String = "text/plain",
        createdBy: String
    ): Document {
        // 1. Save document header
        val document = Document(
            title = title.trim(),
            source = source?.trim(),
            content = content,
            contentType = contentType,
            createdBy = createdBy
        )
        val docId = documentRepo.save(document)
        document.id = docId

        // 2. Chunk the text
        val chunks = DocumentChunker.chunk(content, chunkSize, overlap)
        log.info("Document '{}' chunked into {} pieces", title, chunks.size)

        if (chunks.isEmpty()) {
            return document
        }

        // 3. Generate embeddings
        val embeddings = embeddingService.embedBatch(chunks)
        log.info("Generated {} embeddings for '{}'", embeddings.size, title)

        // 4. Persist chunks
        val documentChunks = chunks.mapIndexed { index, text ->
            DocumentChunk(
                documentId = docId,
                chunkIndex = index,
                content = text,
                embedding = embeddings[index],
                vectorDim = embeddings[index].size
            )
        }
        chunkRepo.saveAll(documentChunks)

        // 5. Update chunk count
        document.chunkCount = chunks.size
        // Note: In a full implementation we'd update the document row here.
        // For simplicity the chunkCount is set at creation time in the service consumer.

        return document
    }

    @Transactional
    fun deleteDocument(id: Long) {
        chunkRepo.deleteByDocumentId(id)
        documentRepo.deleteById(id)
        log.info("Deleted document {}", id)
    }

    fun findAllAccessible(userEmail: String): List<com.example.springjte.dto.DocumentResponse> {
        return documentRepo.findByCreatedBy(userEmail).map {
            com.example.springjte.dto.DocumentResponse(
                id = it.id ?: 0L,
                title = it.title,
                source = it.source,
                contentType = it.contentType,
                chunkCount = it.chunkCount,
                createdBy = it.createdBy,
                createdAt = it.createdAt
            )
        }
    }
}
