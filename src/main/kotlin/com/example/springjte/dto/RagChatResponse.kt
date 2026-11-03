package com.example.springjte.dto

/**
 * One streamed or complete chunk of an assistant reply.
 */
data class RagChatResponse(
    val sessionId: String,
    val content: String,
    val done: Boolean = false,
    val sources: List<RagSource> = emptyList()
)

/**
 * A source document chunk cited by the assistant.
 */
data class RagSource(
    val documentId: Long,
    val documentTitle: String,
    val chunkIndex: Int,
    val snippet: String
)
