package com.buhat.tracker

import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseFailureTest {

    @Test
    fun `a downgrade message is classified as a downgrade`() {
        assertEquals(
            DatabaseFailure.DOWNGRADE,
            classifyDatabaseFailure(RuntimeException("Can't downgrade database from version 2 to 1")),
        )
    }

    @Test
    fun `anything else is unreadable`() {
        assertEquals(
            DatabaseFailure.CORRUPT_OR_UNREADABLE,
            classifyDatabaseFailure(RuntimeException("file is not a database")),
        )
        assertEquals(
            DatabaseFailure.CORRUPT_OR_UNREADABLE,
            classifyDatabaseFailure(RuntimeException()),
        )
    }
}
