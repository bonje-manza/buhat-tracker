package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentRulesTest {

    private fun draft(
        exerciseId: String = "bench",
        sets: Int = 3,
        repMin: Int = 6,
        repMax: Int = 8,
        rir: Int? = null,
    ) = TemplateExerciseDraft(
        exerciseId = exerciseId,
        workingSets = sets,
        repMin = repMin,
        repMax = repMax,
        targetRir = rir,
    )

    @Test
    fun templateNameIsRequired() {
        assertEquals("Name is required.", validateTemplate("  ", emptyList(), listOf(draft())))
    }

    @Test
    fun templateNameIsUniqueCaseInsensitive() {
        assertEquals(
            "That name is already used.",
            validateTemplate("Push Day A", listOf("push day a"), listOf(draft())),
        )
        assertNull(validateTemplate("Leg Day", listOf("push day a"), listOf(draft())))
    }

    @Test
    fun templateNeedsAtLeastOneExercise() {
        assertEquals("Add at least one exercise.", validateTemplate("Leg Day", emptyList(), emptyList()))
    }

    @Test
    fun templateCannotRepeatAnExercise() {
        assertEquals(
            "Each exercise can appear once.",
            validateTemplate("Leg Day", emptyList(), listOf(draft(), draft())),
        )
    }

    @Test
    fun workingSetsAreOneToTen() {
        assertEquals("Sets must be 1 to 10.", validateTemplate("Leg Day", emptyList(), listOf(draft(sets = 0))))
        assertEquals("Sets must be 1 to 10.", validateTemplate("Leg Day", emptyList(), listOf(draft(sets = 11))))
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(sets = 1))))
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(sets = 10))))
    }

    @Test
    fun repsAreOneToThirty() {
        assertEquals("Reps must be 1 to 30.", validateTemplate("Leg Day", emptyList(), listOf(draft(repMin = 0))))
        assertEquals("Reps must be 1 to 30.", validateTemplate("Leg Day", emptyList(), listOf(draft(repMax = 31))))
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(repMin = 30, repMax = 30))))
    }

    @Test
    fun minimumRepsCannotExceedMaximum() {
        assertEquals(
            "Minimum reps cannot exceed maximum reps.",
            validateTemplate("Leg Day", emptyList(), listOf(draft(repMin = 12, repMax = 8))),
        )
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(repMin = 8, repMax = 8))))
    }

    @Test
    fun targetRirIsZeroToFiveOrEmpty() {
        assertEquals("RIR must be 0 to 5.", validateTemplate("Leg Day", emptyList(), listOf(draft(rir = 6))))
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(rir = 0))))
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(rir = 5))))
        assertNull(validateTemplate("Leg Day", emptyList(), listOf(draft(rir = null))))
    }

    @Test
    fun exerciseNameIsRequiredAndUnique() {
        assertEquals("Name is required.", validateExerciseName(" ", emptyList()))
        assertEquals("That name is already used.", validateExerciseName("Belt Squat", listOf("belt squat")))
        assertNull(validateExerciseName("Belt Squat", listOf("back squat")))
    }

    @Test
    fun namesAreCappedAtTwoHundredCharacters() {
        val max = "a".repeat(200)
        val over = "a".repeat(201)
        assertNull(validateExerciseName(max, emptyList()))
        assertEquals("Name must be 200 characters or fewer.", validateExerciseName(over, emptyList()))
        assertEquals(
            "Name must be 200 characters or fewer.",
            validateTemplate(over, emptyList(), listOf(draft())),
        )
    }
}
