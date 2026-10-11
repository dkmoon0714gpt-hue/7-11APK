package com.chaoshangka.manager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer,
    tertiary = Tertiary,
    tertiaryContainer = TertiaryContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    error = ExpiredRed,
    errorContainer = ExpiredContainer,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE3B58F),
    onPrimary = Color(0xFF43220A),
    primaryContainer = Color(0xFF603A1E),
    onPrimaryContainer = Color(0xFFF7DCC6),
    secondary = Color(0xFFA9CCC4),
    onSecondary = Color(0xFF10312B),
    secondaryContainer = Color(0xFF2A4A44),
    tertiary = Color(0xFFE7C77A),
    tertiaryContainer = Color(0xFF5A4300),
    background = Color(0xFF1A1512),
    onBackground = Color(0xFFEDE0D8),
    surface = Color(0xFF201A16),
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF3A3029),
    onSurfaceVariant = Color(0xFFD7C3B5),
    outline = Color(0xFF9B8577),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
)

@Composable
fun ChaoshangkaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
