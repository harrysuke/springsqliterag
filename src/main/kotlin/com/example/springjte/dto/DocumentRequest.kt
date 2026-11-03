package com.example.springjte.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request to upload/index a new document into the RAG knowledge base.
 */
data class DocumentRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val title: String = "",

    val source: String? = null,

    @field:NotBlank
    @field:Size(max = 100_000)
    val content: String = "",

    val contentType: String = "text/plain"
)
