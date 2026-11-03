package com.example.springjte.dto

/**
 * A conversation with its messages.
 */
data class ConversationResponse(
    val sessionId: String,
    val title: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val messages: List<MessageResponse>
)

data class MessageResponse(
    val role: String,
    val content: String,
    val sources: List<RagSource> = emptyList(),
    val createdAt: String?
)
