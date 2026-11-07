package com.buhat.tracker.ui

import java.time.Instant
import java.time.ZoneId

fun formatEpochDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
