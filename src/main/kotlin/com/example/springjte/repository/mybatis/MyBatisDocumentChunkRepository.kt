package com.example.springjte.repository.mybatis

import com.example.springjte.model.DocumentChunk
import com.example.springjte.repository.DocumentChunkRepository
import org.springframework.stereotype.Repository

@Repository
class MyBatisDocumentChunkRepository(private val mapper: DocumentChunkMapper) : DocumentChunkRepository {

    override fun save(chunk: DocumentChunk): Long {
        mapper.insert(chunk)
        return chunk.id ?: throw IllegalStateException("Chunk id not generated")
    }

    override fun saveAll(chunks: List<DocumentChunk>) {
        if (chunks.isNotEmpty()) {
            mapper.insertBatch(chunks)
        }
    }

    override fun findByDocumentId(documentId: Long): List<DocumentChunk> =
        mapper.findByDocumentId(documentId)

    override fun findAll(): List<DocumentChunk> = mapper.findAll()

    override fun deleteByDocumentId(documentId: Long) {
        mapper.deleteByDocumentId(documentId)
    }
}
