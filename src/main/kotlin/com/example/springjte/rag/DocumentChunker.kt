package com.example.springjte.rag

/**
 * Splits text into overlapping chunks suitable for embedding.
 *
 * Splits on paragraph boundaries when possible; falls back to sentences,
 * then to hard character boundaries if a paragraph exceeds [chunkSize].
 */
object DocumentChunker {

    fun chunk(
        text: String,
        chunkSize: Int = 500,
        overlap: Int = 50
    ): List<String> {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        val paragraphs = normalized.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }

        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (para in paragraphs) {
            if (current.length + para.length + 2 <= chunkSize) {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(para)
            } else {
                // Flush current buffer
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    // Carry over overlap
                    val overlapText = extractOverlap(current.toString(), overlap)
                    current.clear()
                    current.append(overlapText)
                }
                // If paragraph itself is too long, split it
                if (para.length > chunkSize) {
                    val splits = splitBySentences(para, chunkSize, overlap)
                    chunks.addAll(splits.dropLast(1))
                    current.clear()
                    current.append(splits.last())
                } else {
                    current.append(para)
                }
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString())
        }
        return chunks
    }

    private fun splitBySentences(text: String, chunkSize: Int, overlap: Int): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val result = mutableListOf<String>()
        val current = StringBuilder()

        for (sentence in sentences) {
            if (current.length + sentence.length + 1 <= chunkSize) {
                if (current.isNotEmpty()) current.append(" ")
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    val overlapText = extractOverlap(current.toString(), overlap)
                    current.clear()
                    current.append(overlapText)
                }
                if (sentence.length > chunkSize) {
                    // Hard split for extremely long sentences
                    var start = 0
                    while (start < sentence.length) {
                        val end = minOf(start + chunkSize, sentence.length)
                        result.add(sentence.substring(start, end))
                        start = end - overlap
                        if (start < 0) start = 0
                        if (start >= sentence.length - 1) break
                    }
                } else {
                    current.append(sentence)
                }
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }

    private fun extractOverlap(text: String, overlap: Int): String {
        if (text.length <= overlap) return text
        // Try to start at a word boundary
        var start = text.length - overlap
        while (start > 0 && text[start - 1] != ' ' && text[start - 1] != '\n') {
            start--
        }
        return text.substring(start)
    }
}
