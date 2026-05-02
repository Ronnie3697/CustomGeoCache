package com.customgeocache.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = GeoGreen40,
    onPrimary = GeoNeutral99,
    primaryContainer = GeoGreen90,
    onPrimaryContainer = GeoGreen10,
    secondary = GeoOrange40,
    onSecondary = GeoNeutral99,
    secondaryContainer = GeoOrange80,
    background = GeoNeutral99,
    onBackground = GeoNeutral10,
    surface = GeoNeutral99,
    onSurface = GeoNeutral10,
    surfaceVariant = GeoNeutral95
)

private val DarkColors = darkColorScheme(
    primary = GeoGreen80,
    onPrimary = GeoGreen20,
    primaryContainer = GeoGreen30,
    onPrimaryContainer = GeoGreen90,
    secondary = GeoOrange80,
    onSecondary = GeoOrange40,
    background = GeoNeutral10,
    onBackground = GeoNeutral90,
    surface = GeoNeutral10,
    onSurface = GeoNeutral90,
    surfaceVariant = GeoNeutral20
)

@Composable
fun CustomGeoCacheTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
