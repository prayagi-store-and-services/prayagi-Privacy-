package com.example

import com.example.siteblock.DnsAnswers
import com.example.siteblock.DnsQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsAnswersTest {
    private val question = byteArrayOf(7) + "example".toByteArray() + byteArrayOf(3) + "com".toByteArray() + byteArrayOf(0, 0, 1, 0, 1)
    private fun query() = DnsQuery(ByteArray(4), ByteArray(4), 1234, 53,
        byteArrayOf(0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0) + question, "example.com", 20)
    private fun rr(type: Int, data: ByteArray) = byteArrayOf(0xc0.toByte(), 12, 0, type.toByte(), 0, 1, 0, 0, 0, 60, 0, data.size.toByte()) + data
    private fun response(vararg answers: ByteArray) = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, answers.size.toByte(), 0, 0, 0, 0) + question + answers.fold(ByteArray(0)) { a, b -> a + b }
    @Test fun readsCompressedAAndAaaaAnswerRecords() {
        val result = DnsAnswers.parse(query(), response(rr(1, byteArrayOf(1, 2, 3, 4)), rr(28, ByteArray(16) { it.toByte() })))
        assertEquals(2, result.size)
        assertEquals(listOf(1, 2, 3, 4), result[0].bytes)
        assertEquals(4, result[0].family)
        assertEquals(6, result[1].family)
        assertEquals(60L, result[0].ttlSeconds)
    }
    @Test fun rejectsWrongTransaction() {
        val r = response(rr(1, byteArrayOf(1, 2, 3, 4))); r[0] = 0
        assertTrue(DnsAnswers.parse(query(), r).isEmpty())
    }
    @Test fun rejectsDifferentQuestion() {
        val r = response(rr(1, byteArrayOf(1, 2, 3, 4))); r[13] = 'z'.code.toByte()
        assertTrue(DnsAnswers.parse(query(), r).isEmpty())
    }
    @Test fun rejectsTruncatedAndErrorReplies() {
        for (flag in listOf(0x82, 0x83)) {
            val r = response(rr(1, byteArrayOf(1, 2, 3, 4)))
            if (flag == 0x82) r[2] = 0x83.toByte() else r[3] = 0x83.toByte()
            assertTrue(DnsAnswers.parse(query(), r).isEmpty())
        }
    }
    @Test fun rejectsShortRecordsAndInvalidAddressLength() {
        val r = response(rr(1, byteArrayOf(1, 2, 3, 4)))
        assertTrue(DnsAnswers.parse(query(), r.copyOf(r.size - 1)).isEmpty())
        assertTrue(DnsAnswers.parse(query(), response(rr(1, byteArrayOf(1, 2, 3)))).isEmpty())
    }
    @Test fun ignoresAdditionalGlueAndUnsupportedTypes() {
        val r = response(); r[11] = 1
        assertTrue(DnsAnswers.parse(query(), r + rr(1, byteArrayOf(1, 2, 3, 4))).isEmpty())
        assertTrue(DnsAnswers.parse(query(), response(rr(16, byteArrayOf(0)))).isEmpty())
    }
    @Test fun rejectsCompressionLoopAndForwardPointer() {
        val r = response(rr(1, byteArrayOf(1, 2, 3, 4)))
        val start = 12 + question.size; r[start + 1] = start.toByte()
        assertTrue(DnsAnswers.parse(query(), r).isEmpty())
    }
    @Test fun everyPrefixOfAValidReplyIsSafeAndEmpty() {
        val r = response(rr(1, byteArrayOf(1, 2, 3, 4)))
        for (n in 0 until r.size) assertTrue(DnsAnswers.parse(query(), r.copyOf(n)).isEmpty())
    }
}
