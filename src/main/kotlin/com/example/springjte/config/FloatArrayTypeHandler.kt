package com.example.springjte.config

import org.apache.ibatis.type.BaseTypeHandler
import org.apache.ibatis.type.JdbcType
import org.apache.ibatis.type.MappedTypes
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.CallableStatement
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * Converts a [FloatArray] to a SQLite BLOB of little-endian 32-bit floats
 * and back again. Registered automatically by MyBatis for properties
 * of type [FloatArray].
 */
@MappedTypes(FloatArray::class)
class FloatArrayTypeHandler : BaseTypeHandler<FloatArray>() {

    override fun setNonNullParameter(ps: PreparedStatement, i: Int, parameter: FloatArray, jdbcType: JdbcType?) {
        val bytes = toBytes(parameter)
        ps.setBytes(i, bytes)
    }

    override fun getNullableResult(rs: ResultSet, columnName: String): FloatArray? {
        val bytes = rs.getBytes(columnName) ?: return null
        return fromBytes(bytes)
    }

    override fun getNullableResult(rs: ResultSet, columnIndex: Int): FloatArray? {
        val bytes = rs.getBytes(columnIndex) ?: return null
        return fromBytes(bytes)
    }

    override fun getNullableResult(cs: CallableStatement, columnIndex: Int): FloatArray? {
        val bytes = cs.getBytes(columnIndex) ?: return null
        return fromBytes(bytes)
    }

    private fun toBytes(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        for (f in array) {
            buffer.putFloat(f)
        }
        return buffer.array()
    }

    private fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val count = bytes.size / 4
        return FloatArray(count) { buffer.getFloat() }
    }
}
