package com.example.springjte.repository.mybatis

import com.example.springjte.model.DocumentChunk
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface DocumentChunkMapper {
    fun insert(chunk: DocumentChunk): Int
    fun insertBatch(@Param("chunks") chunks: List<DocumentChunk>): Int
    fun findByDocumentId(@Param("documentId") documentId: Long): List<DocumentChunk>
    fun findAll(): List<DocumentChunk>
    fun deleteByDocumentId(@Param("documentId") documentId: Long): Int
}
