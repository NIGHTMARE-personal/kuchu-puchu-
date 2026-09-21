package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.R

// Cute rounded custom font "Sniglet" downloaded via font-util
val CuteFontFamily = FontFamily(
    Font(R.font.sniglet, FontWeight.Normal)
)

val CuteDisplay = CuteFontFamily
val CuteBody = CuteFontFamily

val Typography = Typography(
    // Home hero display: cute rounded font
    displayLarge = TextStyle(
        fontFamily = CuteDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    // Screen titles & headlines: cute rounded font
    headlineMedium = TextStyle(
        fontFamily = CuteDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = CuteDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CuteDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    // Body text: sweet and legible rounded font
    bodyLarge = TextStyle(
        fontFamily = CuteBody,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    // Secondary body
    bodyMedium = TextStyle(
        fontFamily = CuteBody,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.2.sp
    ),
    // Micro-labels: cute rounded
    labelSmall = TextStyle(
        fontFamily = CuteBody,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.05.em
    )
)
