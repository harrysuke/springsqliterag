package com.example.springjte.repository.mybatis

import com.example.springjte.model.Document
import com.example.springjte.repository.DocumentRepository
import org.springframework.stereotype.Repository

@Repository
class MyBatisDocumentRepository(private val mapper: DocumentMapper) : DocumentRepository {

    override fun save(document: Document): Long {
        mapper.insert(document)
        return document.id ?: throw IllegalStateException("Document id not generated")
    }

    override fun findById(id: Long): Document? = mapper.findById(id)

    override fun findAll(): List<Document> = mapper.findAll()

    override fun findByCreatedBy(email: String): List<Document> = mapper.findByCreatedBy(email)

    override fun deleteById(id: Long) {
        mapper.deleteById(id)
    }

    override fun count(): Long = mapper.count()
}
