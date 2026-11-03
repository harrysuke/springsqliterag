package com.example.springjte.repository.mybatis

import com.example.springjte.model.RagConversation
import com.example.springjte.model.RagMessage
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface RagConversationMapper {
    fun insertConversation(conv: RagConversation): Int
    fun insertMessage(message: RagMessage): Int
    fun findConversationBySessionId(@Param("sessionId") sessionId: String): RagConversation?
    fun findConversationsByUser(@Param("email") email: String): List<RagConversation>
    fun findMessagesByConversation(@Param("conversationId") conversationId: Long): List<RagMessage>
    fun updateConversationTimestamp(@Param("sessionId") sessionId: String): Int
}
