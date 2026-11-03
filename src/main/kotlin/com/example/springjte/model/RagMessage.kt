package com.example.springjte.model

/**
 * One row of the `rag_messages` table — a single turn in a RAG conversation.
 */
data class RagMessage(
    var id: Long? = null,
    var conversationId: Long = 0,
    var role: String = "user",
    var content: String = "",
    var sources: String? = null,
    var createdAt: String? = null
)
