package com.example.springjte.dto

/**
 * Lightweight view of a document in the knowledge base.
 */
data class DocumentResponse(
    val id: Long,
    val title: String,
    val source: String?,
    val contentType: String,
    val chunkCount: Int,
    val createdBy: String,
    val createdAt: String?
)
