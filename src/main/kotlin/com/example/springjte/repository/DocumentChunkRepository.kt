package com.example.springjte.repository

import com.example.springjte.model.DocumentChunk

/**
 * Persistence contract for document chunks with embedding vectors.
 */
interface DocumentChunkRepository {
    fun save(chunk: DocumentChunk): Long
    fun saveAll(chunks: List<DocumentChunk>)
    fun findByDocumentId(documentId: Long): List<DocumentChunk>
    fun findAll(): List<DocumentChunk>
    fun deleteByDocumentId(documentId: Long)
}
