package com.buhat.tracker.domain

import java.math.BigDecimal
import java.math.RoundingMode

const val KG_PER_LB = 2.20462

enum class WeightUnit { KG, LB }

fun kgToLb(kg: Double): Double = kg * KG_PER_LB

fun lbToKg(lb: Double): Double = lb / KG_PER_LB

fun formatWeight(kg: Double, unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> "${formatKg(kg)} kg"
    WeightUnit.LB -> "${BigDecimal.valueOf(kgToLb(kg)).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} lb"
}
