package com.buhat.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

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
    fun formatKgTrimsTrailingZeros() {
        assertEquals("80", formatKg(80.0))
        assertEquals("82.5", formatKg(82.5))
        assertEquals("0", formatKg(0.0))
    }
}
