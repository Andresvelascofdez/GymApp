package com.gymapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Lime = Color(0xFFD8F37C)
val Muted = Color(0xFFB4BCB2)
val Ink = Color(0xFF10140F)
val Panel = Color(0xFF1B211A)

@Composable
fun GymTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lime,
            onPrimary = Ink,
            primaryContainer = Color(0xFF344423),
            onPrimaryContainer = Lime,
            secondary = Color(0xFF9EDFD2),
            background = Ink,
            onBackground = Color(0xFFF0F3E9),
            surface = Ink,
            surfaceContainer = Panel,
            surfaceContainerHigh = Color(0xFF252D23),
            onSurface = Color(0xFFF0F3E9),
            onSurfaceVariant = Muted,
            outline = Color(0xFF586250),
            error = Color(0xFFFFB4A9)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
        ),
        content = content
    )
}
