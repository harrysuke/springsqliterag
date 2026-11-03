package com.example.springjte.rag

import com.example.springjte.model.DocumentChunk
import com.example.springjte.repository.DocumentChunkRepository
import org.springframework.stereotype.Component
import kotlin.math.sqrt

/**
 * In-memory brute-force vector store backed by SQLite.
 *
 * Loads all chunk embeddings from the database and computes cosine similarity
 * in Kotlin. This is O(N*d) where N = number of chunks and d = vector dimension.
 * For knowledge bases with tens of thousands of chunks this is still sub-second
 * on modern hardware; beyond that, swap in a dedicated vector database.
 */
@Component
class SqliteVectorStore(private val chunkRepo: DocumentChunkRepository) {

    /**
     * Find the [topK] chunks most similar to [queryVector].
     * Returns chunks sorted by descending similarity, filtered by [minScore].
     */
    fun search(queryVector: FloatArray, topK: Int = 5, minScore: Double = 0.7): List<ScoredChunk> {
        val allChunks = chunkRepo.findAll()
        if (allChunks.isEmpty()) return emptyList()

        val queryNorm = norm(queryVector)
        if (queryNorm == 0.0) return emptyList()

        return allChunks
            .mapNotNull { chunk ->
                val chunkNorm = norm(chunk.embedding)
                if (chunkNorm == 0.0) return@mapNotNull null
                val similarity = dot(queryVector, chunk.embedding) / (queryNorm * chunkNorm)
                if (similarity >= minScore) ScoredChunk(chunk, similarity) else null
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            sum += a[i] * b[i]
        }
        return sum
    }

    private fun norm(v: FloatArray): Double {
        var sum = 0.0
        for (f in v) {
            sum += f * f
        }
        return sqrt(sum)
    }
}

data class ScoredChunk(
    val chunk: DocumentChunk,
    val score: Double
)
