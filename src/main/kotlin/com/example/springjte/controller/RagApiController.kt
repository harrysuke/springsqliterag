package com.example.springjte.controller

import com.example.springjte.dto.*
import com.example.springjte.model.RagMessage
import com.example.springjte.rag.DocumentIngestionService
import com.example.springjte.rag.OllamaEmbeddingService
import com.example.springjte.rag.RagService
import com.example.springjte.service.AuditService
import tools.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.Executors

/**
 * RAG (Retrieval-Augmented Generation) API endpoints.
 *
 * - Chat (blocking and streaming)
 * - Conversation history
 * - Document upload / list / delete
 * - Health check for Ollama backend
 */
@RestController
@RequestMapping("/api/rag")
class RagApiController(
    private val ragService: RagService,
    private val ingestionService: DocumentIngestionService,
    private val embeddingService: OllamaEmbeddingService,
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sseExecutor = Executors.newCachedThreadPool()

    /**
     * Blocking chat endpoint — returns the complete answer.
     */
    @PostMapping("/chat")
    fun chat(
        @Valid @RequestBody request: RagChatRequest,
        @AuthenticationPrincipal user: UserDetails
    ): RagChatResponse {
        val result = ragService.chat(
            userMessage = request.message,
            userEmail = user.username,
            sessionId = request.sessionId
        )
        auditService.log(
            actor = user.username,
            action = "rag.query",
            resource = "rag_conversation",
            resourceId = result.sessionId,
            details = "Question: ${request.message.take(200)}"
        )
        return RagChatResponse(
            sessionId = result.sessionId,
            content = result.answer,
            done = true,
            sources = result.sources
        )
    }

    /**
     * Streaming chat endpoint — returns Server-Sent Events with token chunks.
     */
    @PostMapping("/chat/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chatStream(
        @Valid @RequestBody request: RagChatRequest,
        @AuthenticationPrincipal user: UserDetails
    ): SseEmitter {
        val emitter = SseEmitter(120_000L)
        val result = ragService.chatStream(
            userMessage = request.message,
            userEmail = user.username,
            sessionId = request.sessionId
        )

        sseExecutor.execute {
            try {
                val builder = StringBuilder()
                for (token in result.tokenStream) {
                    builder.append(token)
                    val event = RagChatResponse(
                        sessionId = result.sessionId,
                        content = token,
                        done = false,
                        sources = emptyList()
                    )
                    emitter.send(SseEmitter.event()
                        .name("message")
                        .data(objectMapper.writeValueAsString(event)))
                }
                // Final event with sources
                val finalEvent = RagChatResponse(
                    sessionId = result.sessionId,
                    content = "",
                    done = true,
                    sources = result.sources
                )
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data(objectMapper.writeValueAsString(finalEvent)))
                emitter.complete()

                // Persist after stream completes
                ragService.persistStreamResult(
                    conversationId = result.conversationId,
                    sessionId = result.sessionId,
                    userMessage = request.message,
                    fullAnswer = builder.toString(),
                    sources = result.sources
                )

                auditService.log(
                    actor = user.username,
                    action = "rag.query.stream",
                    resource = "rag_conversation",
                    resourceId = result.sessionId
                )
            } catch (e: Exception) {
                log.error("RAG stream error", e)
                emitter.completeWithError(e)
            }
        }

        emitter.onTimeout { emitter.complete() }
        emitter.onError { emitter.complete() }
        return emitter
    }

    /**
     * Get messages for a conversation.
     */
    @GetMapping("/conversations/{sessionId}")
    fun getConversation(
        @PathVariable sessionId: String,
        @AuthenticationPrincipal user: UserDetails
    ): ConversationResponse {
        val messages = ragService.getConversationHistory(sessionId, user.username)
        val conv = ragService.listConversations(user.username)
            .find { it.sessionId == sessionId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found")

        return ConversationResponse(
            sessionId = conv.sessionId,
            title = conv.title,
            createdAt = conv.createdAt,
            updatedAt = conv.updatedAt,
            messages = messages.map { msg ->
                val sources = try {
                    msg.sources?.let { objectMapper.readValue(it, Array<RagSource>::class.java)?.toList() } ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                MessageResponse(
                    role = msg.role,
                    content = msg.content,
                    sources = sources,
                    createdAt = msg.createdAt
                )
            }
        )
    }

    /**
     * List all conversations for the authenticated user.
     */
    @GetMapping("/conversations")
    fun listConversations(@AuthenticationPrincipal user: UserDetails): List<Map<String, String?>> {
        return ragService.listConversations(user.username).map {
            mapOf(
                "sessionId" to it.sessionId,
                "title" to it.title,
                "createdAt" to it.createdAt,
                "updatedAt" to it.updatedAt
            )
        }
    }

    /**
     * Upload a document into the knowledge base.
     */
    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadDocument(
        @Valid @RequestBody request: DocumentRequest,
        @AuthenticationPrincipal user: UserDetails
    ): DocumentResponse {
        val doc = ingestionService.ingest(
            title = request.title,
            source = request.source,
            content = request.content,
            contentType = request.contentType,
            createdBy = user.username
        )
        auditService.log(
            actor = user.username,
            action = "rag.document.upload",
            resource = "documents",
            resourceId = doc.id?.toString(),
            details = "Title: ${request.title}"
        )
        return DocumentResponse(
            id = doc.id ?: 0L,
            title = doc.title,
            source = doc.source,
            contentType = doc.contentType,
            chunkCount = doc.chunkCount,
            createdBy = doc.createdBy,
            createdAt = doc.createdAt
        )
    }

    /**
     * List documents in the knowledge base.
     */
    @GetMapping("/documents")
    fun listDocuments(@AuthenticationPrincipal user: UserDetails): List<DocumentResponse> {
        return ingestionService.findAllAccessible(user.username)
    }

    /**
     * Delete a document and its chunks.
     */
    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDocument(
        @PathVariable id: Long,
        @AuthenticationPrincipal user: UserDetails
    ) {
        ingestionService.deleteDocument(id)
        auditService.log(
            actor = user.username,
            action = "rag.document.delete",
            resource = "documents",
            resourceId = id.toString()
        )
    }

    /**
     * Health check for the Ollama backend.
     */
    @GetMapping("/health")
    fun health(): Map<String, Any> {
        val healthy = embeddingService.isHealthy()
        return mapOf(
            "ollama" to healthy,
            "status" to if (healthy) "ok" else "unavailable"
        )
    }
}
