package com.example.springjte.model

/**
 * One row of the `rag_conversations` table — a user chat session.
 */
data class RagConversation(
    var id: Long? = null,
    var sessionId: String = "",
    var userEmail: String = "",
    var title: String? = null,
    var createdAt: String? = null,
    var updatedAt: String? = null
)
