package com.example.springjte.repository.mybatis

import com.example.springjte.model.RagConversation
import com.example.springjte.model.RagMessage
import com.example.springjte.repository.RagConversationRepository
import org.springframework.stereotype.Repository

@Repository
class MyBatisRagConversationRepository(private val mapper: RagConversationMapper) : RagConversationRepository {

    override fun saveConversation(conv: RagConversation): Long {
        mapper.insertConversation(conv)
        return conv.id ?: throw IllegalStateException("Conversation id not generated")
    }

    override fun saveMessage(message: RagMessage): Long {
        mapper.insertMessage(message)
        return message.id ?: throw IllegalStateException("Message id not generated")
    }

    override fun findConversationBySessionId(sessionId: String): RagConversation? =
        mapper.findConversationBySessionId(sessionId)

    override fun findConversationsByUser(email: String): List<RagConversation> =
        mapper.findConversationsByUser(email)

    override fun findMessagesByConversation(conversationId: Long): List<RagMessage> =
        mapper.findMessagesByConversation(conversationId)

    override fun updateConversationTimestamp(sessionId: String) {
        mapper.updateConversationTimestamp(sessionId)
    }
}
