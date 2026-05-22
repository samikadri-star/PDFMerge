package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val SophisticatedColorScheme = darkColorScheme(
    primary = SophisticatedPrimary,
    onPrimary = SophisticatedOnPrimary,
    primaryContainer = SophisticatedPrimaryContainer,
    onPrimaryContainer = SophisticatedOnPrimaryContainer,
    secondary = SophisticatedSecondary,
    secondaryContainer = SophisticatedSecondaryContainer,
    onSecondaryContainer = SophisticatedOnSecondaryContainer,
    background = SophisticatedBg,
    onBackground = SophisticatedOnBg,
    surface = SophisticatedSurface,
    onSurface = SophisticatedOnSurface,
    surfaceVariant = SophisticatedSurfaceVariant,
    onSurfaceVariant = SophisticatedOnSurfaceVariant,
    outline = SophisticatedOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamicColor by default to enforce Sophisticated Dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        SophisticatedColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
