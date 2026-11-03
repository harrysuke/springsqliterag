package com.example.springjte.controller

import com.example.springjte.web.WebUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Server-side rendered pages for the RAG chat interface.
 */
@Controller
@RequestMapping("/rag")
class RagPageController {

    @GetMapping("/chat")
    fun chat(model: Model, request: HttpServletRequest): String {
        model.addAttribute("contextPath", request.contextPath)
        model.addAttribute("csrf", WebUtils.csrfToken(request)?.token)
        model.addAttribute("csrfParam", "_csrf")
        model.addAttribute("currentUser", request.userPrincipal?.name ?: "")
        model.addAttribute("title", "RAG Chat")
        return "rag/chat"
    }
}
