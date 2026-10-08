package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = ForestGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = SageGreenContainer,
    onPrimaryContainer = ForestGreenPrimary,
    secondary = SageGreenSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageGreenContainer,
    onSecondaryContainer = ForestGreenPrimary,
    background = SandBackground,
    onBackground = Color(0xFF1C1D1B),
    surface = SandSurface,
    onSurface = Color(0xFF1C1D1B),
    surfaceVariant = SandSurfaceVariant,
    onSurfaceVariant = Color(0xFF444843),
    error = ExpiryRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = ForestGreenPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = ForestGreenPrimary,
    onPrimaryContainer = SageGreenContainer,
    secondary = SageGreenSecondary,
    background = Color(0xFF131A15),
    onBackground = Color(0xFFE2E3DF),
    surface = Color(0xFF1B241E),
    onSurface = Color(0xFFE2E3DF),
    surfaceVariant = Color(0xFF2C372F),
    onSurfaceVariant = Color(0xFFC2C9C1)
)

@Composable
fun DispensaSmartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our tailored Sage/Forest theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

