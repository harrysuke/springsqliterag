package com.example.springjte.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Incoming chat message from the user.
 */
data class RagChatRequest(
    @field:NotBlank
    @field:Size(max = 4000)
    val message: String = "",

    /** Client-generated session id; created if absent. */
    val sessionId: String? = null
)
