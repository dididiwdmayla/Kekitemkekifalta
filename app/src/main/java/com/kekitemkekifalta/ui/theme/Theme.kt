@file:OptIn(ExperimentalTextApi::class)

package com.kekitemkekifalta.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kekitemkekifalta.R

/** The kekitem palette: kitchen paper, ink, tomato, mustard and leaf. */
@Immutable
data class KekColors(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val outline: Color,
    val shadow: Color,
    val tomato: Color,
    val tomatoSoft: Color,
    val mustard: Color,
    val mustardSoft: Color,
    val leaf: Color,
    val leafSoft: Color,
    val onAccent: Color,
    val isDark: Boolean,
)

val LightKek = KekColors(
    paper = Color(0xFFFFF4E2),
    card = Color(0xFFFFFCF5),
    ink = Color(0xFF2A1E14),
    muted = Color(0xFF7A6857),
    outline = Color(0xFF2A1E14),
    shadow = Color(0xFF2A1E14),
    tomato = Color(0xFFE4572E),
    tomatoSoft = Color(0xFFFFD9CB),
    mustard = Color(0xFFF2B134),
    mustardSoft = Color(0xFFFFEBBF),
    leaf = Color(0xFF2F8F5B),
    leafSoft = Color(0xFFD3EEDC),
    onAccent = Color(0xFFFFFBF3),
    isDark = false,
)

val DarkKek = KekColors(
    paper = Color(0xFF1C1712),
    card = Color(0xFF2A231C),
    ink = Color(0xFFF7EBDC),
    muted = Color(0xFFB9A792),
    outline = Color(0xFF5B4B3B),
    shadow = Color(0xFF080605),
    tomato = Color(0xFFFF7A55),
    tomatoSoft = Color(0xFF5C2717),
    mustard = Color(0xFFFFC94D),
    mustardSoft = Color(0xFF4E3B10),
    leaf = Color(0xFF5BC48A),
    leafSoft = Color(0xFF173F29),
    onAccent = Color(0xFF1C1712),
    isDark = true,
)

/** Text on mustard/soft accents, which stay light in both themes. */
val KekColors.inkOnLight: Color get() = Color(0xFF2A1E14)

private val LocalKekColors = staticCompositionLocalOf { LightKek }

object KekTheme {
    val colors: KekColors
        @Composable get() = LocalKekColors.current
}

private fun fredoka(weight: Int) =
    Font(R.font.fredoka, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

private fun nunito(weight: Int) =
    Font(R.font.nunito, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Fredoka = FontFamily(fredoka(400), fredoka(500), fredoka(600), fredoka(700))
val Nunito = FontFamily(nunito(400), nunito(600), nunito(700), nunito(800))

private val KekTypography = Typography(
    displayLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp),
    displayMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 42.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp),
)

private val KekShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private fun KekColors.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = tomato,
        onPrimary = onAccent,
        primaryContainer = tomatoSoft,
        onPrimaryContainer = ink,
        secondary = leaf,
        onSecondary = onAccent,
        secondaryContainer = leafSoft,
        onSecondaryContainer = ink,
        tertiary = mustard,
        onTertiary = Color(0xFF2A1E14),
        tertiaryContainer = mustardSoft,
        onTertiaryContainer = ink,
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = card,
        onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        surfaceBright = card,
        surfaceDim = paper,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        inverseSurface = ink,
        inverseOnSurface = paper,
        inversePrimary = tomatoSoft,
        outline = outline,
        outlineVariant = muted.copy(alpha = 0.4f),
        error = Color(0xFFC62828),
        onError = Color.White,
        scrim = Color(0x99000000),
    )
}

@Composable
fun KekTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkKek else LightKek
    CompositionLocalProvider(LocalKekColors provides colors) {
        MaterialTheme(colorScheme = colors.toScheme(), typography = KekTypography, shapes = KekShapes, content = content)
    }
}
