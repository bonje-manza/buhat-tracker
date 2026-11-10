package com.buhat.tracker.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    // Captured from repository actions on SM-S711W, not a hand-invented JSON schema.
    private val captured = requireNotNull(javaClass.getResource("/phase6-backup.json")).readText()
    private val createdAt = 1793694030009L
    private val finishedAt = 1793694030218L
    private val otherId = "00000000-0000-4000-8000-000000000001"
    private fun validBackup(): Backup = decodeBackup(captured)

    private fun assertRejected(text: String, fragment: String) {
        val error = assertThrows(BackupException::class.java) { decodeBackup(text) }
        assertTrue(
            "Expected message containing '$fragment', was '${error.message}'",
            error.message.orEmpty().contains(fragment),
        )
    }

    private fun withoutKeys(json: String, vararg keys: String): String {
        fun strip(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(
                element.filterKeys { it !in keys }.mapValues { strip(it.value) },
            )
            is JsonArray -> JsonArray(element.map(::strip))
            else -> element
        }
        return strip(Json.parseToJsonElement(json)).toString()
    }

    private val secondSessionId = "00000000-0000-4000-8000-000000000002"
    private val secondSessionExerciseId = "00000000-0000-4000-8000-000000000003"

    private fun twoSessionBackup(): Backup {
        val original = validBackup()
        return original.copy(
            sessions = listOf(
                original.sessions.single().copy(importKey = "duplicate"),
                original.sessions.single().copy(id = secondSessionId, importKey = "duplicate"),
            ),
            sessionExercises = listOf(
                original.sessionExercises.single(),
                original.sessionExercises.single().copy(
                    id = secondSessionExerciseId,
                    sessionId = secondSessionId,
                    exerciseId = otherId,
                    position = 1,
                ),
            ),
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
        val json = encodeBackup(validBackup()).replace("\"formatVersion\": 1", "\"formatVersion\": 4")

        assertRejected(json, "newer version")
    }

    @Test
    fun `a format 1 backup imports with null overrides and format 3 defaults`() {
        val decoded = decodeBackup(captured)

        assertEquals(null, decoded.exercises.single().weightUnitOverride)
        assertEquals(null, decoded.exercises.single().restWorkingSecondsOverride)
        assertEquals(null, decoded.exercises.single().note)
        assertFalse(decoded.exercises.single().isUnilateral)
        assertEquals(null, decoded.sessions.single().note)
        assertEquals(null, decoded.sessions.single().importKey)
        assertEquals(null, decoded.sessionExercises.single().note)
        assertEquals(null, decoded.sessionExercises.single().replacesSessionExerciseId)
        assertTrue(decoded.sets.all { it.side == null })
    }

    @Test
    fun `a format 1 backup discards keys that format 2 added`() {
        val json = captured.replace(
            """"isCustom": true,""",
            """"isCustom": true,
            "weightUnitOverride": "stone",
            "restWorkingSecondsOverride": 601,""",
        )

        val decoded = decodeBackup(json)

        assertEquals(null, decoded.exercises.single().weightUnitOverride)
        assertEquals(null, decoded.exercises.single().restWorkingSecondsOverride)
    }

    @Test
    fun `a format 2 backup round trips overrides exactly`() {
        val original = validBackup()
        val backup = original.copy(
            formatVersion = 2,
            exercises = listOf(
                original.exercises.single().copy(
                    weightUnitOverride = "LB",
                    restWorkingSecondsOverride = 180,
                ),
            ),
        )

        val decoded = decodeBackup(encodeBackup(backup))

        assertEquals(2, decoded.formatVersion)
        assertEquals("LB", decoded.exercises.single().weightUnitOverride)
        assertEquals(180, decoded.exercises.single().restWorkingSecondsOverride)
    }

    @Test
    fun `format 2 requires the override keys`() {
        val json = encodeBackup(validBackup().copy(formatVersion = 2))

        assertRejected(json.replace(Regex(""",\s*"weightUnitOverride": null"""), ""), "corrupt")
        assertRejected(json.replace(Regex(""",\s*"restWorkingSecondsOverride": null"""), ""), "corrupt")
    }

    @Test
    fun `invalid exercise overrides are rejected`() {
        val original = validBackup()
        val exercise = original.exercises.single()
        for (invalid in listOf(
            original.copy(exercises = listOf(exercise.copy(weightUnitOverride = "stone"))),
            original.copy(exercises = listOf(exercise.copy(restWorkingSecondsOverride = -1))),
            original.copy(exercises = listOf(exercise.copy(restWorkingSecondsOverride = 601))),
        )) {
            assertRejected(encodeBackup(invalid.copy(formatVersion = 2)), "outside the allowed range")
        }
    }

    @Test
    fun `format 3 requires the new keys`() {
        val json = encodeBackup(validBackup().copy(formatVersion = 3))

        for (key in listOf("note", "isUnilateral", "importKey", "replacesSessionExerciseId", "side")) {
            assertRejected(withoutKeys(json, key), "corrupt")
        }
    }

    @Test
    fun `a format 3 backup round trips every new field`() {
        val original = validBackup()
        val firstRow = original.sessionExercises.single()
        val backup = original.copy(
            formatVersion = 3,
            exercises = listOf(
                original.exercises.single().copy(note = "Keep elbows tucked", isUnilateral = true),
            ),
            sessions = listOf(
                original.sessions.single().copy(note = "Felt strong", importKey = "strong:2026-06-24:FBEOD"),
            ),
            sessionExercises = listOf(
                firstRow.copy(note = "Left felt weaker"),
                firstRow.copy(
                    id = secondSessionExerciseId,
                    exerciseId = otherId,
                    position = 2,
                    note = "Replacement note",
                    replacesSessionExerciseId = firstRow.id,
                ),
            ),
            sets = original.sets.mapIndexed { index, set -> if (index == 0) set.copy(side = "RIGHT") else set },
        )

        val decoded = decodeBackup(encodeBackup(backup))

        assertEquals(3, decoded.formatVersion)
        assertEquals("Keep elbows tucked", decoded.exercises.single().note)
        assertTrue(decoded.exercises.single().isUnilateral)
        assertEquals("Felt strong", decoded.sessions.single().note)
        assertEquals("strong:2026-06-24:FBEOD", decoded.sessions.single().importKey)
        assertEquals("Left felt weaker", decoded.sessionExercises.first().note)
        assertEquals(firstRow.id, decoded.sessionExercises[1].replacesSessionExerciseId)
        assertEquals("RIGHT", decoded.sets.first().side)
    }

    @Test
    fun `a format 2 backup defaults every format 3 field`() {
        val original = validBackup()
        val backup = original.copy(
            formatVersion = 2,
            exercises = listOf(original.exercises.single().copy(note = "ignored", isUnilateral = true)),
            sessions = listOf(original.sessions.single().copy(note = "ignored", importKey = "ignored")),
            sessionExercises = listOf(original.sessionExercises.single().copy(note = "ignored")),
            sets = original.sets.map { it.copy(side = "LEFT") },
        )

        val decoded = decodeBackup(
            withoutKeys(
                encodeBackup(backup),
                "note", "isUnilateral", "importKey", "replacesSessionExerciseId", "side",
            ),
        )

        assertEquals(2, decoded.formatVersion)
        assertNull(decoded.exercises.single().note)
        assertFalse(decoded.exercises.single().isUnilateral)
        assertNull(decoded.sessions.single().note)
        assertNull(decoded.sessions.single().importKey)
        assertNull(decoded.sessionExercises.single().note)
        assertNull(decoded.sessionExercises.single().replacesSessionExerciseId)
        assertTrue(decoded.sets.all { it.side == null })
    }

    @Test
    fun `notes accept 2000 characters and reject more`() {
        val original = validBackup()
        val exercise = original.exercises.single()
        val session = original.sessions.single()
        val sessionExercise = original.sessionExercises.single()
        val accepted = original.copy(
            formatVersion = 3,
            exercises = listOf(exercise.copy(note = "x".repeat(2000))),
            sessions = listOf(session.copy(note = "x".repeat(2000))),
            sessionExercises = listOf(sessionExercise.copy(note = "x".repeat(2000))),
        )
        decodeBackup(encodeBackup(accepted))

        val long = "x".repeat(2001)
        for (invalid in listOf(
            original.copy(formatVersion = 3, exercises = listOf(exercise.copy(note = long))),
            original.copy(formatVersion = 3, sessions = listOf(session.copy(note = long))),
            original.copy(formatVersion = 3, sessionExercises = listOf(sessionExercise.copy(note = long))),
        )) assertRejected(encodeBackup(invalid), "outside the allowed range")
    }

    @Test
    fun `an invalid side is rejected`() {
        val original = validBackup()
        for (side in listOf("UP", "", "left")) {
            assertRejected(
                encodeBackup(original.copy(formatVersion = 3, sets = listOf(original.sets.first().copy(side = side)))),
                "corrupt",
            )
        }
    }

    @Test
    fun `duplicate import keys are rejected`() {
        assertRejected(encodeBackup(twoSessionBackup().copy(formatVersion = 3)), "duplicate import key")
    }

    @Test
    fun `import keys must be nonblank and at most 200 characters`() {
        val original = validBackup().copy(formatVersion = 3)
        fun withKey(key: String) = encodeBackup(
            original.copy(sessions = listOf(original.sessions.single().copy(importKey = key))),
        )

        assertEquals("x".repeat(200), decodeBackup(withKey("x".repeat(200))).sessions.single().importKey)
        for (key in listOf("", " \t\n", "x".repeat(201))) {
            assertRejected(withKey(key), "outside the allowed range")
        }
    }

    @Test
    fun `a dangling replacement link is rejected`() {
        val original = validBackup()
        val row = original.sessionExercises.single()
        for (invalid in listOf(
            row.copy(replacesSessionExerciseId = row.id),
            row.copy(replacesSessionExerciseId = otherId),
        )) {
            val backup = original.copy(formatVersion = 3, sessionExercises = listOf(invalid))
            assertRejected(encodeBackup(backup), "dangling replacement link")
        }

        val twoSessions = twoSessionBackup().copy(
            formatVersion = 3,
            sessions = twoSessionBackup().sessions.map { it.copy(importKey = null) },
        )
        assertRejected(
            encodeBackup(
                twoSessions.copy(
                    sessionExercises = twoSessions.sessionExercises.mapIndexed { index, exercise ->
                        if (index == 0) exercise.copy(replacesSessionExerciseId = secondSessionExerciseId) else exercise
                    },
                ),
            ),
            "dangling replacement link",
        )
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
