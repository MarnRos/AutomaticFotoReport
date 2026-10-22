package it.officina.automaticfotoreport

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BrandBlue = Color(0xFF2B5BE0)
val BrandBlueDark = Color(0xFF1A3FA8)
val BrandAmber = Color(0xFFF2A900)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA8),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF006A60),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFF6F7FB),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E3EE),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F1F7),
    surfaceContainer = Color(0xFFEAEBF3),
    surfaceContainerHigh = Color(0xFFE4E5EE),
    surfaceContainerHighest = Color(0xFFDEDFE9),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C6D0),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB7C4FF),
    onPrimary = Color(0xFF002388),
    primaryContainer = Color(0xFF1E44C0),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFFFBA3D),
    onSecondary = Color(0xFF482F00),
    secondaryContainer = Color(0xFF684400),
    onSecondaryContainer = Color(0xFFFFDEA8),
    tertiary = Color(0xFF53DBC9),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE3E2E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Color(0xFF1D1F25),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33353B),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
