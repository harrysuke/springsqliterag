package com.example.springjte.model

/**
 * One row of the `document_chunks` table — a semantic slice with a
 * pre-computed embedding vector stored as a [FloatArray].
 */
data class DocumentChunk(
    var id: Long? = null,
    var documentId: Long = 0,
    var chunkIndex: Int = 0,
    var content: String = "",
    var embedding: FloatArray = floatArrayOf(),
    var vectorDim: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DocumentChunk
        return id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
