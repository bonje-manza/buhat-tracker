package com.buhat.tracker.domain

import com.buhat.tracker.data.local.Exercise
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

const val KG_PER_LB = 2.20462
const val CM_PER_INCH = 2.54

enum class WeightUnit { KG, LB }

fun kgToLb(kg: Double): Double = kg * KG_PER_LB

fun lbToKg(lb: Double): Double = lb / KG_PER_LB

fun Exercise.effectiveUnit(global: WeightUnit): WeightUnit =
    weightUnitOverride?.let(WeightUnit::valueOf) ?: global

fun weightIncrementKg(unit: WeightUnit): Double =
    if (unit == WeightUnit.LB) lbToKg(5.0) else 2.5

fun formatHeight(heightCm: Int, unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> "$heightCm cm"
    WeightUnit.LB -> {
        val totalInches = (heightCm / CM_PER_INCH).roundToInt()
        "${totalInches / 12}'${totalInches % 12}\""
    }
}

fun formatWeightValue(kg: Double, unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> formatKg(kg)
    WeightUnit.LB -> BigDecimal.valueOf(kgToLb(kg)).setScale(1, RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
}

fun formatWeight(kg: Double, unit: WeightUnit): String =
    "${formatWeightValue(kg, unit)} ${if (unit == WeightUnit.KG) "kg" else "lb"}"
