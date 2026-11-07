package com.buhat.tracker.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun `no finished workouts never reminds`() {
        assertFalse(shouldRemindBackup(null, null, now, hasWorkouts = false))
        assertFalse(shouldRemindBackup(now - 30 * day, null, now, hasWorkouts = false))
    }

    @Test
    fun `never exported reminds after the first workout`() {
        assertTrue(shouldRemindBackup(null, null, now, hasWorkouts = true))
    }

    @Test
    fun `thirteen days is not due but fourteen is`() {
        assertFalse(shouldRemindBackup(now - 13 * day, null, now, hasWorkouts = true))
        assertTrue(shouldRemindBackup(now - 14 * day, null, now, hasWorkouts = true))
        assertFalse(shouldRemindBackup(now - 14 * day + 1, null, now, hasWorkouts = true))
    }

    @Test
    fun `a future snooze hides the reminder`() {
        assertFalse(shouldRemindBackup(null, now + 1, now, hasWorkouts = true))
    }

    @Test
    fun `an expired snooze allows the reminder`() {
        assertTrue(shouldRemindBackup(null, now, now, hasWorkouts = true))
        assertTrue(shouldRemindBackup(null, now - 1, now, hasWorkouts = true))
    }

    @Test
    fun `an export today is not due`() {
        assertFalse(shouldRemindBackup(now, null, now, hasWorkouts = true))
    }
}
