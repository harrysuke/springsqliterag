package com.example.springjte.model

/**
 * One row of the `documents` table — an uploaded knowledge-base source.
 */
data class Document(
    var id: Long? = null,
    var title: String = "",
    var source: String? = null,
    var content: String = "",
    var contentType: String = "text/plain",
    var chunkCount: Int = 0,
    var createdBy: String = "",
    var createdAt: String? = null
)
