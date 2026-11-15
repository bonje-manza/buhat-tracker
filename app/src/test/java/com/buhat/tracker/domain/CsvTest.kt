package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {

    @Test
    fun `quoted delimiter escaped quote and multiline field stay in one record`() {
        val rows = parseCsv("a;\"b;c\";\"say \"\"hi\"\"\";\"line one\nline two\"")

        assertEquals(1, rows.size)
        assertEquals(listOf("a", "b;c", "say \"hi\"", "line one\nline two"), rows.single().fields)
        assertEquals(1, rows.single().line)
    }

    @Test
    fun `bom and crlf are accepted`() {
        val rows = parseCsv("\uFEFFa;b\r\nc;d\r\n")

        assertEquals(listOf("a", "b"), rows[0].fields)
        assertEquals(1, rows[0].line)
        assertEquals(listOf("c", "d"), rows[1].fields)
        assertEquals(2, rows[1].line)
    }

    @Test
    fun `empty fields and blank lines are handled`() {
        val rows = parseCsv("a;;b\r\n\r\nc;\"\";d")

        assertEquals(2, rows.size)
        assertEquals(listOf("a", "", "b"), rows[0].fields)
        assertEquals(listOf("c", "", "d"), rows[1].fields)
        assertEquals(3, rows[1].line)
    }

    @Test
    fun `unclosed quote reports the line it started on`() {
        val error = assertThrows(CsvException::class.java) {
            parseCsv("a;b\r\nc;\"unclosed")
        }

        assertTrue("was '${error.message}'", error.message.orEmpty().contains("Row 2"))
    }

    @Test
    fun `text after a closing quote is rejected`() {
        val error = assertThrows(CsvException::class.java) {
            parseCsv("\"a\"x;b")
        }

        assertTrue("was '${error.message}'", error.message.orEmpty().contains("Row 1"))
    }
}
