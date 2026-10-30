package com.buhat.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.buhat.tracker.R

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.W400),
    Font(R.font.inter_semibold, FontWeight.W600),
    Font(R.font.inter_extrabold, FontWeight.W800),
)

val BuhatTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W800, fontSize = 32.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W800, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
)
