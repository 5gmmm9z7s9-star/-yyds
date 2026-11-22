package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = AmberMuted,
    onPrimaryContainer = AmberGlow,
    secondary = CoralAccent,
    onSecondary = Color(0xFF280508),
    secondaryContainer = CoralContainer,
    onSecondaryContainer = Color(0xFFFFB3B3),
    tertiary = TealHighlight,
    onTertiary = Color(0xFF00201E),
    tertiaryContainer = TealContainer,
    onTertiaryContainer = Color(0xFF9DF2EA),
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = ElevatedCard,
    surfaceContainerHigh = ElevatedCardHigh,
    outline = OutlineSubtle,
    outlineVariant = Color(0xFF1F2636),
)

private val LightColorScheme = lightColorScheme(
    primary = DeepAmber,
    onPrimary = Color.White,
    primaryContainer = DeepAmberContainer,
    onPrimaryContainer = Color(0xFF3E2200),
    secondary = DeepCoral,
    onSecondary = Color.White,
    secondaryContainer = DeepCoralContainer,
    onSecondaryContainer = Color(0xFF410006),
    tertiary = DeepTeal,
    onTertiary = Color.White,
    tertiaryContainer = DeepTealContainer,
    onTertiaryContainer = Color(0xFF00201E),
    background = AlabasterBg,
    onBackground = TextDarkPrimary,
    surface = WarmWhiteSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = WarmCard,
    onSurfaceVariant = TextDarkSecondary,
    surfaceContainerLow = WarmWhiteSurface,
    surfaceContainer = WarmCard,
    surfaceContainerHigh = WarmCardHigh,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE7E1D6),
)

@Composable
fun RhythmMusicTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
