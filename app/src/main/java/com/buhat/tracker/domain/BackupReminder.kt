package com.buhat.tracker.domain

const val BACKUP_REMINDER_AFTER_DAYS = 14
const val BACKUP_SNOOZE_DAYS = 7
const val DAY_MILLIS = 24L * 60 * 60 * 1000

fun shouldRemindBackup(
    lastExportAt: Long?,
    snoozedUntil: Long?,
    now: Long,
    hasWorkouts: Boolean,
): Boolean {
    if (!hasWorkouts) return false
    if (snoozedUntil != null && snoozedUntil > now) return false
    if (lastExportAt == null) return true
    return now - lastExportAt >= BACKUP_REMINDER_AFTER_DAYS * DAY_MILLIS
}
