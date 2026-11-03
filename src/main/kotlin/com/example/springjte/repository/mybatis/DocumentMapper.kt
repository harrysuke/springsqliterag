package com.example.springjte.repository.mybatis

import com.example.springjte.model.Document
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface DocumentMapper {
    fun insert(document: Document): Int
    fun findById(id: Long): Document?
    fun findAll(): List<Document>
    fun findByCreatedBy(@Param("email") email: String): List<Document>
    fun deleteById(id: Long): Int
    fun count(): Long
}
