package com.iiitl.canteen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimaryWhite,
    secondary = PrimaryGreen,
    onSecondary = OnPrimaryWhite,
    background = DarkBackground,
    onBackground = OnPrimaryWhite,
    surface = DarkSurface,
    onSurface = OnPrimaryWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = SecondaryText,
    error = ErrorRed,
    onError = OnPrimaryWhite
)

@Composable
fun QueuelessTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}