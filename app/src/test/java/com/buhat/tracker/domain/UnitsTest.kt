package com.buhat.tracker.domain

import com.buhat.tracker.data.local.Exercise
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    private fun exercise(unit: String? = null) = Exercise(
        id = "bench",
        name = "Bench Press",
        muscleGroup = "Chest",
        equipment = "Barbell",
        isCustom = false,
        createdAt = 0L,
        weightUnitOverride = unit,
    )

    @Test
    fun convertsKilogramsToPounds() {
        assertEquals(176.3696, kgToLb(80.0), 0.0001)
        assertEquals(5.51155, kgToLb(2.5), 0.00001)
    }

    @Test
    fun convertsPoundsBackToKilograms() {
        assertEquals(80.0, lbToKg(176.3696), 0.00001)
        assertEquals(2.5, lbToKg(kgToLb(2.5)), 0.0000001)
        assertEquals(100.0, lbToKg(kgToLb(100.0)), 0.0000001)
    }

    @Test
    fun formatsWeightsInBothUnits() {
        assertEquals("80 kg", formatWeight(80.0, WeightUnit.KG))
        assertEquals("82.5 kg", formatWeight(82.5, WeightUnit.KG))
        assertEquals("0 kg", formatWeight(0.0, WeightUnit.KG))
        assertEquals("176.4 lb", formatWeight(80.0, WeightUnit.LB))
        assertEquals("5.5 lb", formatWeight(2.5, WeightUnit.LB))
        assertEquals("220.5 lb", formatWeight(100.0, WeightUnit.LB))
        assertEquals("0 lb", formatWeight(0.0, WeightUnit.LB))
    }

    @Test
    fun formatsHeightInTheSelectedUnit() {
        // 178 cm / 2.54 = 70.08 in, nearest inch 70 = 5'10".
        assertEquals("178 cm", formatHeight(178, WeightUnit.KG))
        assertEquals("5'10\"", formatHeight(178, WeightUnit.LB))
        // 171 cm / 2.54 = 67.32 in, nearest inch 67 = 5'7".
        assertEquals("5'7\"", formatHeight(171, WeightUnit.LB))
        // 180 cm / 2.54 = 70.87 in, nearest inch 71 = 5'11".
        assertEquals("5'11\"", formatHeight(180, WeightUnit.LB))
    }

    @Test
    fun formatKgTrimsTrailingZeros() {
        assertEquals("80", formatKg(80.0))
        assertEquals("82.5", formatKg(82.5))
        assertEquals("0", formatKg(0.0))
    }

    @Test
    fun formatKgCapsLongConvertedValuesAtTwoDecimals() {
        assertEquals("102.06", formatKg(lbToKg(225.0)))
        assertEquals("54.88", formatKg(54.884631999999996))
    }

    @Test
    fun poundKilogramPoundRoundTripShowsWholePounds() {
        val kg = lbToKg(225.0)
        assertEquals("102.06 kg", formatWeight(kg, WeightUnit.KG))
        assertEquals("225 lb", formatWeight(kg, WeightUnit.LB))
    }

    @Test
    fun effectiveUnitUsesOverrideThenGlobal() {
        assertEquals(WeightUnit.LB, exercise(unit = "LB").effectiveUnit(WeightUnit.KG))
        assertEquals(WeightUnit.KG, exercise(unit = null).effectiveUnit(WeightUnit.KG))
        assertEquals(WeightUnit.LB, exercise(unit = null).effectiveUnit(WeightUnit.LB))
    }

    @Test
    fun weightIncrementFollowsTheUnit() {
        assertEquals(2.5, weightIncrementKg(WeightUnit.KG), 0.0)
        // 5 lb / 2.20462 = 2.26796... kg; 5 lb displayed back is exactly 5 lb.
        assertEquals(2.26796, weightIncrementKg(WeightUnit.LB), 0.00001)
    }
}
