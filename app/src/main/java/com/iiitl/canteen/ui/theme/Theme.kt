package com.iiitl.canteen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DeepGreen,
    onPrimary = OnPrimaryWhite,
    secondary = WarmAmber,
    onSecondary = Color.White,
    background = AppBackground,
    onBackground = Color(0xFF212121),
    surface = AppSurface,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF0F4F1),
    onSurfaceVariant = Color(0xFF555555),
    error = ErrorRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = LightDeepGreen,
    onPrimary = OnPrimaryWhite,
    secondary = WarmAmber,
    onSecondary = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C352E),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun QueuelessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}