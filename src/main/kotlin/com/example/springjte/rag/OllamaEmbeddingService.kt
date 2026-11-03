package com.example.springjte.rag

import tools.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Client for Ollama's embedding and generation APIs.
 *
 * Uses Java's built-in [HttpClient] — no extra HTTP library needed.
 */
@Service
class OllamaEmbeddingService(
    private val objectMapper: ObjectMapper,
    @Value("\${rag.ollama.base-url:http://localhost:11434}") private val baseUrl: String,
    @Value("\${rag.ollama.model:llama3.2}") private val chatModel: String,
    @Value("\${rag.ollama.embedding-model:nomic-embed-text}") private val embeddingModel: String,
    @Value("\${rag.ollama.timeout-seconds:60}") private val timeoutSeconds: Long
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
        .build()

    /**
     * Generate an embedding vector for a single text string.
     */
    fun embed(text: String): FloatArray {
        val requestBody = mapOf(
            "model" to embeddingModel,
            "prompt" to text
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/embeddings"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Ollama embedding failed: HTTP ${response.statusCode()} - ${response.body()}")
        }

        val json = objectMapper.readTree(response.body())
        val embeddingNode = json.get("embedding")
        return FloatArray(embeddingNode.size()) { i ->
            embeddingNode.get(i).asDouble().toFloat()
        }
    }

    /**
     * Generate embeddings for multiple texts in batch.
     * Ollama's /api/embeddings endpoint processes one at a time, so we
     * call sequentially with a small throttle to avoid overwhelming the model.
     */
    fun embedBatch(texts: List<String>): List<FloatArray> {
        return texts.mapIndexed { index, text ->
            if (index > 0) Thread.sleep(50) // Tiny throttle between calls
            embed(text)
        }
    }

    /**
     * Generate a completion from the LLM given a prompt.
     * Returns the full response text (non-streaming).
     */
    fun generate(prompt: String): String {
        val requestBody = mapOf(
            "model" to chatModel,
            "prompt" to prompt,
            "stream" to false,
            "options" to mapOf("temperature" to 0.3)
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Ollama generation failed: HTTP ${response.statusCode()} - ${response.body()}")
        }

        val json = objectMapper.readTree(response.body())
        return json.get("response")?.asText() ?: ""
    }

    /**
     * Stream tokens from the LLM as a sequence of strings.
     * Each emitted item is a token chunk.
     */
    fun generateStream(prompt: String): Sequence<String> {
        val requestBody = mapOf(
            "model" to chatModel,
            "prompt" to prompt,
            "stream" to true,
            "options" to mapOf("temperature" to 0.3)
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            throw RuntimeException("Ollama stream failed: HTTP ${response.statusCode()}")
        }

        return sequence {
            response.body().bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    try {
                        val json = objectMapper.readTree(line)
                        val token = json.get("response")?.asText() ?: ""
                        if (token.isNotEmpty()) {
                            yield(token)
                        }
                        if (json.get("done")?.asBoolean() == true) {
                            break
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to parse stream line: $line", e)
                    }
                }
            }
        }
    }

    fun isHealthy(): Boolean {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/tags"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() == 200
        } catch (e: Exception) {
            false
        }
    }
}
