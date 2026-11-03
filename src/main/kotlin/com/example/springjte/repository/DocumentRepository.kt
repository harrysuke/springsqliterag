package com.example.springjte.repository

import com.example.springjte.model.Document

/**
 * Persistence contract for the RAG document knowledge base.
 */
interface DocumentRepository {
    fun save(document: Document): Long
    fun findById(id: Long): Document?
    fun findAll(): List<Document>
    fun findByCreatedBy(email: String): List<Document>
    fun deleteById(id: Long)
    fun count(): Long
}
