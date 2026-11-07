package com.buhat.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class BackupTest {

    // Captured from repository actions on SM-S711W, not a hand-invented JSON schema.
    private val captured = requireNotNull(javaClass.getResource("/phase6-backup.json")).readText()
    private val createdAt = 1793694030009L
    private val finishedAt = 1793694030218L
    private val otherId = "00000000-0000-4000-8000-000000000001"
    private fun validBackup(): Backup = Json.decodeFromString(captured)

    private fun assertRejected(text: String, fragment: String) {
        val error = assertThrows(BackupException::class.java) { decodeBackup(text) }
        assertTrue(
            "Expected message containing '$fragment', was '${error.message}'",
            error.message.orEmpty().contains(fragment),
        )
    }

    @Test
    fun `the export size guard accepts the limit and rejects one byte more`() {
        requireBackupFits(0)
        requireBackupFits(MAX_BACKUP_BYTES - 1)
        requireBackupFits(MAX_BACKUP_BYTES)

        val error = assertThrows(BackupException::class.java) { requireBackupFits(MAX_BACKUP_BYTES + 1) }

        assertEquals("This backup is larger than 20 MB. Export is not possible.", error.message)
    }

    @Test
    fun `the export size guard honours an injected limit`() {
        requireBackupFits(10, maxBytes = 10)

        val error = assertThrows(BackupException::class.java) { requireBackupFits(11, maxBytes = 10) }

        assertEquals("This backup is larger than 20 MB. Export is not possible.", error.message)
    }

    @Test
    fun `a valid backup round trips every row`() {
        val decoded = decodeBackup(encodeBackup(validBackup()))

        assertEquals(1, decoded.formatVersion)
        assertEquals(1793694030308L, decoded.exportedAt)
        assertEquals("Avery Kamp", decoded.profile.name)
        assertEquals("LIGHT", decoded.settings.theme)
        assertEquals(120, decoded.settings.restWorkingSeconds)
        assertEquals(1, decoded.exercises.size)
        assertEquals("Belt Squat", decoded.exercises.single().name)
        assertEquals("f7ef1617-6935-41f8-9065-91c61a7f70e3", decoded.templates.single().id)
        assertEquals("066d2cee-ed3e-4f84-9ca5-996b3785634a", decoded.templateExercises.single().exerciseId)
        assertEquals("FINISHED", decoded.sessions.single().status)
        assertEquals("Belt Squat", decoded.sessionExercises.single().exerciseNameSnapshot)
        assertEquals(80.0, decoded.sets[1].weightKg, 0.0)
        assertEquals(2, decoded.sets[1].rir)
        assertEquals(null, decoded.sets.first().workingIndex)
    }

    @Test
    fun `a file without a format version is not a backup`() {
        assertRejected("""{"hello":"world"}""", "not a Buhat backup")
    }

    @Test
    fun `a newer format version is rejected`() {
        val json = encodeBackup(validBackup()).replace("\"formatVersion\": 1", "\"formatVersion\": 2")

        assertRejected(json, "newer version")
    }

    @Test
    fun `a truncated file is corrupt`() {
        val json = encodeBackup(validBackup())
        assertRejected(json.take(json.length / 2), "corrupt")
    }

    @Test
    fun `a missing exercise reference is rejected`() {
        val backup = validBackup().copy(
            templateExercises = listOf(
                validBackup().templateExercises.single().copy(exerciseId = otherId),
            ),
        )

        assertRejected(encodeBackup(backup), "missing exercise")
    }

    @Test
    fun `duplicate ids are rejected`() {
        val backup = validBackup().copy(exercises = validBackup().exercises + validBackup().exercises.first())

        assertRejected(encodeBackup(backup), "duplicate IDs")
    }

    @Test
    fun `duplicate exercise names are rejected case-insensitively`() {
        val backup = validBackup().copy(
            exercises = validBackup().exercises + BackupExercise(
                otherId, "belt squat", "Quads", "Machine", true, null, createdAt,
            ),
        )

        assertRejected(encodeBackup(backup), "duplicate exercise names")
    }

    @Test
    fun `a set outside the allowed ranges is rejected`() {
        val backup = validBackup().copy(
            sets = listOf(validBackup().sets.first().copy(weightKg = 1001.0)),
        )

        assertRejected(encodeBackup(backup), "outside the allowed range")
    }

    @Test
    fun `an active session is rejected`() {
        val backup = validBackup().copy(
            sessions = listOf(validBackup().sessions.single().copy(status = "ACTIVE")),
        )

        assertRejected(encodeBackup(backup), "still active")
    }

    @Test
    fun `working set positions must be contiguous`() {
        val backup = validBackup().copy(
            sets = listOf(
                validBackup().sets[1],
                validBackup().sets[2].copy(workingIndex = 3),
            ),
        )

        assertRejected(encodeBackup(backup), "contiguous")
    }

    @Test
    fun `required nullable fields cannot silently disappear`() {
        val json = encodeBackup(validBackup()).replace("\"age\": 29,", "")
        assertRejected(json, "corrupt")
    }

    @Test
    fun `rest increment matches the controls bounds`() {
        for (increment in listOf(0, 4, 61, 600)) {
            assertRejected(encodeBackup(validBackup().copy(
                settings = validBackup().settings.copy(restIncrementSeconds = increment),
            )), "outside the allowed range")
        }
    }

    @Test
    fun `timestamps must describe a possible session and template`() {
        val backup = validBackup()
        for (invalid in listOf(
            backup.copy(templates = listOf(backup.templates.single().copy(updatedAt = createdAt - 1))),
            backup.copy(exercises = backup.exercises.map { it.copy(archivedAt = createdAt - 1) }),
            backup.copy(sets = listOf(backup.sets.first().copy(completedAt = createdAt - 1))),
            backup.copy(sets = listOf(backup.sets.first().copy(completedAt = finishedAt + 1))),
        )) assertRejected(encodeBackup(invalid), "corrupt")
    }

    @Test
    fun `empty templates and sessions are rejected`() {
        val backup = validBackup()
        assertRejected(encodeBackup(backup.copy(templateExercises = emptyList())), "corrupt")
        assertRejected(encodeBackup(backup.copy(sessionExercises = emptyList(), sets = emptyList())), "corrupt")
    }

    @Test
    fun `invalid enums references and numeric types are rejected`() {
        val json = encodeBackup(validBackup())
        for (invalid in listOf(
            json.replace("\"LIGHT\"", "\"SYSTEM\""),
            json.replace("\"LB\"", "\"stone\""),
            json.replace("\"WORKING\"", "\"INVALID\""),
            json.replace("\"age\": 29", "\"age\": true"),
            json.replace("\"age\": 29", "\"age\": \"29\""),
            json.replace("\"name\": \"Avery Kamp\"", "\"name\": 123"),
        )) assertRejected(invalid, "corrupt")
    }

    @Test fun `malformed IDs and snapshot IDs are rejected`() {
        val backup = validBackup()
        assertRejected(encodeBackup(backup.copy(sets = listOf(backup.sets[1].copy(id = "bad-id")))), "corrupt")
        assertRejected(encodeBackup(backup.copy(sessions = backup.sessions.map { it.copy(templateIdSnapshot = "") })), "corrupt")
    }

    @Test fun `positions and working indices must follow chronology`() {
        val backup = validBackup()
        assertRejected(encodeBackup(backup.copy(templateExercises = backup.templateExercises.map { it.copy(position = 2) })), "corrupt")
        assertRejected(encodeBackup(backup.copy(sessionExercises = backup.sessionExercises.map { it.copy(position = 2) })), "corrupt")
        assertRejected(encodeBackup(backup.copy(sets = backup.sets.map {
            if (it.workingIndex != null) it.copy(workingIndex = 3 - it.workingIndex) else it
        })), "contiguous")
    }

    @Test fun `deleted exercise and template snapshots remain valid`() {
        val backup = validBackup().copy(exercises = emptyList(), templates = emptyList(), templateExercises = emptyList())
        assertEquals("Belt Squat", decodeBackup(encodeBackup(backup)).sessionExercises.single().exerciseNameSnapshot)
    }

    @Test fun `unbounded text is rejected`() {
        val backup = validBackup()
        val long = "x".repeat(3 * 1024 * 1024)
        val exercise = backup.exercises.single()
        val template = backup.templates.single()
        val session = backup.sessions.single()
        val sessionExercise = backup.sessionExercises.single()
        for (invalid in listOf(
            backup.copy(profile = backup.profile.copy(name = long)),
            backup.copy(profile = backup.profile.copy(gender = long)),
            backup.copy(exercises = listOf(exercise.copy(name = long))),
            backup.copy(exercises = listOf(exercise.copy(muscleGroup = long))),
            backup.copy(exercises = listOf(exercise.copy(equipment = long))),
            backup.copy(templates = listOf(template.copy(name = long))),
            backup.copy(sessions = listOf(session.copy(templateNameSnapshot = long))),
            backup.copy(sessionExercises = listOf(sessionExercise.copy(exerciseNameSnapshot = long))),
        )) assertRejected(encodeBackup(invalid), "outside the allowed range")
    }
}
