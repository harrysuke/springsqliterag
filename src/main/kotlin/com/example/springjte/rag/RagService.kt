package com.example.springjte.rag

import com.example.springjte.dto.RagSource
import com.example.springjte.model.RagConversation
import com.example.springjte.model.RagMessage
import com.example.springjte.repository.RagConversationRepository
import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Orchestrates the full RAG pipeline:
 *  1. Embed user question
 *  2. Retrieve relevant document chunks
 *  3. Build augmented prompt
 *  4. Generate answer via LLM
 *  5. Persist conversation history
 */
@Service
class RagService(
    private val embeddingService: OllamaEmbeddingService,
    private val vectorStore: SqliteVectorStore,
    private val conversationRepo: RagConversationRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${rag.retrieval.top-k:5}") private val topK: Int,
    @Value("\${rag.retrieval.similarity-threshold:0.7}") private val minScore: Double
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Execute a single-turn RAG query (non-streaming).
     * Returns the assistant's answer and the sources used.
     */
    fun chat(
        userMessage: String,
        userEmail: String,
        sessionId: String? = null
    ): ChatResult {
        val sid = sessionId ?: UUID.randomUUID().toString()

        // 1. Retrieve or create conversation
        val conversation = getOrCreateConversation(sid, userEmail, userMessage)

        // 2. Embed the question
        val queryEmbedding = embeddingService.embed(userMessage)

        // 3. Retrieve context
        val scoredChunks = vectorStore.search(queryEmbedding, topK, minScore)
        val context = buildContext(scoredChunks)
        val sources = scoredChunks.map {
            RagSource(
                documentId = it.chunk.documentId,
                documentTitle = "Document ${it.chunk.documentId}", // Enhanced below
                chunkIndex = it.chunk.chunkIndex,
                snippet = it.chunk.content.take(200)
            )
        }

        // 4. Build prompt
        val prompt = buildPrompt(context, userMessage)
        log.debug("RAG prompt length: {} chars", prompt.length)

        // 5. Generate answer
        val answer = embeddingService.generate(prompt)

        // 6. Persist messages
        conversationRepo.saveMessage(
            RagMessage(conversationId = conversation.id!!, role = "user", content = userMessage)
        )
        conversationRepo.saveMessage(
            RagMessage(
                conversationId = conversation.id!!,
                role = "assistant",
                content = answer,
                sources = if (sources.isNotEmpty()) objectMapper.writeValueAsString(sources) else null
            )
        )
        conversationRepo.updateConversationTimestamp(sid)

        return ChatResult(sessionId = sid, answer = answer, sources = sources)
    }

    /**
     * Stream a RAG response token-by-token.
     * The caller is responsible for persisting messages after the stream completes.
     */
    fun chatStream(
        userMessage: String,
        userEmail: String,
        sessionId: String? = null
    ): ChatStreamResult {
        val sid = sessionId ?: UUID.randomUUID().toString()
        val conversation = getOrCreateConversation(sid, userEmail, userMessage)

        val queryEmbedding = embeddingService.embed(userMessage)
        val scoredChunks = vectorStore.search(queryEmbedding, topK, minScore)
        val context = buildContext(scoredChunks)
        val sources = scoredChunks.map {
            RagSource(
                documentId = it.chunk.documentId,
                documentTitle = "Document ${it.chunk.documentId}",
                chunkIndex = it.chunk.chunkIndex,
                snippet = it.chunk.content.take(200)
            )
        }

        val prompt = buildPrompt(context, userMessage)
        val tokenStream = embeddingService.generateStream(prompt)

        return ChatStreamResult(
            sessionId = sid,
            conversationId = conversation.id!!,
            tokenStream = tokenStream,
            sources = sources
        )
    }

    fun getConversationHistory(sessionId: String, userEmail: String): List<RagMessage> {
        val conv = conversationRepo.findConversationBySessionId(sessionId)
            ?: return emptyList()
        if (conv.userEmail != userEmail) return emptyList()
        return conversationRepo.findMessagesByConversation(conv.id!!)
    }

    fun listConversations(userEmail: String): List<RagConversation> {
        return conversationRepo.findConversationsByUser(userEmail)
    }

    private fun getOrCreateConversation(
        sessionId: String,
        userEmail: String,
        firstMessage: String
    ): RagConversation {
        val existing = conversationRepo.findConversationBySessionId(sessionId)
        if (existing != null) return existing

        val title = firstMessage.trim().take(60).let {
            if (it.length < firstMessage.trim().length) "$it..." else it
        }
        val conv = RagConversation(
            sessionId = sessionId,
            userEmail = userEmail,
            title = title
        )
        val id = conversationRepo.saveConversation(conv)
        conv.id = id
        return conv
    }

    private fun buildContext(scoredChunks: List<ScoredChunk>): String {
        if (scoredChunks.isEmpty()) return "No relevant documents found."
        return scoredChunks.mapIndexed { i, scored ->
            "[${i + 1}] ${scored.chunk.content}"
        }.joinToString("\n\n---\n\n")
    }

    private fun buildPrompt(context: String, question: String): String {
        return """
You are a helpful assistant with access to the following documents.
Answer based ONLY on the provided context. If the answer is not in the
context, say "I don't have enough information to answer that."
Cite the source numbers [1], [2], etc. when you use them.

Context:
---
$context
---

Question: $question

Answer:
""".trimIndent()
    }

    fun persistStreamResult(
        conversationId: Long,
        sessionId: String,
        userMessage: String,
        fullAnswer: String,
        sources: List<RagSource>
    ) {
        conversationRepo.saveMessage(
            RagMessage(conversationId = conversationId, role = "user", content = userMessage)
        )
        conversationRepo.saveMessage(
            RagMessage(
                conversationId = conversationId,
                role = "assistant",
                content = fullAnswer,
                sources = if (sources.isNotEmpty()) objectMapper.writeValueAsString(sources) else null
            )
        )
        conversationRepo.updateConversationTimestamp(sessionId)
    }

    data class ChatResult(
        val sessionId: String,
        val answer: String,
        val sources: List<RagSource>
    )

    data class ChatStreamResult(
        val sessionId: String,
        val conversationId: Long,
        val tokenStream: Sequence<String>,
        val sources: List<RagSource>
    )
}
