package com.example.springjte.repository

import com.example.springjte.model.RagConversation
import com.example.springjte.model.RagMessage

/**
 * Persistence contract for RAG conversations and messages.
 */
interface RagConversationRepository {
    fun saveConversation(conv: RagConversation): Long
    fun saveMessage(message: RagMessage): Long
    fun findConversationBySessionId(sessionId: String): RagConversation?
    fun findConversationsByUser(email: String): List<RagConversation>
    fun findMessagesByConversation(conversationId: Long): List<RagMessage>
    fun updateConversationTimestamp(sessionId: String)
}
