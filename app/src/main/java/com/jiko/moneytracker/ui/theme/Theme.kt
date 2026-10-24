package com.jiko.moneytracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jiko.moneytracker.R

/** Plus Jakarta Sans (400/500/600/700/800), bundled in res/font. */
val Jakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

/** Extra tokens from the web design system (beyond Material3's scheme). */
data class SaldoPalette(
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val successText: Color,
    val successFill: Color,
    val warningText: Color,
    val warningFill: Color,
    val dangerSoft: Color,
    val alertIconBg: Color,
    val alertIconFg: Color,
    val surface3: Color,
    val line: Color,
    val faint: Color,
    val muted: Color,
    val ink: Color,
)

private val LightPalette = SaldoPalette(
    accent = Color(0xFFFF5722),
    accentInk = Color.White,
    accentSoft = Color(0xFFFFE9E2),
    successText = Color(0xFF00A047),
    successFill = Color(0xFF00C853),
    warningText = Color(0xFFE07F00),
    warningFill = Color(0xFFFF9100),
    dangerSoft = Color(0xFFFDECEC),
    alertIconBg = Color(0xFFFFF3E0),
    alertIconFg = Color(0xFFE07F00),
    surface3 = Color(0xFFE2E7EE),
    line = Color(0xFFE4E8EE),
    faint = Color(0xFF98A1AF),
    muted = Color(0xFF5A6472),
    ink = Color(0xFF10151D),
)

private val DarkPalette = SaldoPalette(
    accent = Color(0xFFFF8A50),
    accentInk = Color(0xFF1A0E08),
    accentSoft = Color(0x79332016),
    successText = Color(0xFF00E676),
    successFill = Color(0xFF00E676),
    warningText = Color(0xFFFFAB40),
    warningFill = Color(0xFFFF9100),
    dangerSoft = Color(0xFF3A1E22),
    alertIconBg = Color(0xFF33240F),
    alertIconFg = Color(0xFFFFAB40),
    surface3 = Color(0xFF242E44),
    line = Color(0xFF232B3D),
    faint = Color(0xFF67707F),
    muted = Color(0xFF9AA4B2),
    ink = Color(0xFFF2F4F8),
)

val LocalSaldoPalette = staticCompositionLocalOf { LightPalette }

/** Extra design tokens for the current theme: `saldoColors.accent` etc. */
val saldoColors: SaldoPalette
    @Composable get() = LocalSaldoPalette.current

private val LightScheme = lightColorScheme(
    primary = Color(0xFFFF5722),
    onPrimary = Color.White,
    secondary = Color(0xFF5A6472),
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFECEFF3),
    onBackground = Color(0xFF10151D),
    onSurface = Color(0xFF10151D),
    onSurfaceVariant = Color(0xFF5A6472),
    tertiary = Color(0xFF00C853),
    error = Color(0xFFD32F2F),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A50),
    onPrimary = Color(0xFF1A0E08),
    secondary = Color(0xFF9AA4B2),
    background = Color(0xFF0B0F19),
    surface = Color(0xFF141A26),
    surfaceVariant = Color(0xFF1C2333),
    onBackground = Color(0xFFF2F4F8),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFF9AA4B2),
    tertiary = Color(0xFF00E676),
    error = Color(0xFFFF6B6B),
)

/** App typography on Plus Jakarta Sans. Money text uses tabular figures. */
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, letterSpacing = (-0.5).sp,
        fontFeatureSettings = "tnum"
    ),
    headlineMedium = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, fontFeatureSettings = "tnum"
    ),
    titleMedium = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
    ),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontSize = 14.sp),
    labelSmall = TextStyle(
        fontFamily = Jakarta, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, fontFeatureSettings = "tnum"
    ),
)

@Composable
fun SaldoTheme(
    darkMode: Int = 0, // 0 = system, 1 = light, 2 = dark
    content: @Composable () -> Unit
) {
    val dark = when (darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSaldoPalette provides if (dark) DarkPalette else LightPalette
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = Typography,
            content = content
        )
    }
}
