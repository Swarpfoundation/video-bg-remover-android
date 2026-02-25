package com.videobgremover.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = StudioNightTeal,
    onPrimary = StudioNight,
    primaryContainer = colorMix(StudioNightTeal, StudioNightSurface),
    onPrimaryContainer = StudioIvory,
    secondary = StudioNightEmber,
    onSecondary = StudioNight,
    secondaryContainer = colorMix(StudioNightEmber, StudioNightSurface),
    onSecondaryContainer = StudioIvory,
    tertiary = AccentBlueSoft,
    onTertiary = StudioNight,
    tertiaryContainer = colorMix(AccentBlue, StudioNightSurface),
    onTertiaryContainer = StudioIvory,
    background = StudioNight,
    onBackground = StudioIvory,
    surface = StudioNightSurface,
    onSurface = StudioIvory,
    surfaceVariant = StudioNightElevated,
    onSurfaceVariant = StudioNightTextMuted,
    outline = StudioNightOutline,
    error = colorMix(androidx.compose.ui.graphics.Color(0xFFFF6B6B), StudioNight, 0.15f),
    errorContainer = colorMix(androidx.compose.ui.graphics.Color(0xFFFF6B6B), StudioNightSurface, 0.35f)
)

private val LightColorScheme = lightColorScheme(
    primary = AccentTeal,
    onPrimary = StudioIvory,
    primaryContainer = AccentTealSoft,
    onPrimaryContainer = StudioSlate,
    secondary = AccentEmber,
    onSecondary = StudioIvory,
    secondaryContainer = AccentEmberSoft,
    onSecondaryContainer = StudioSlate,
    tertiary = AccentBlue,
    onTertiary = StudioIvory,
    tertiaryContainer = AccentBlueSoft,
    onTertiaryContainer = StudioSlate,
    background = StudioSand,
    onBackground = StudioInk,
    surface = StudioIvory,
    onSurface = StudioInk,
    surfaceVariant = StudioMist,
    onSurfaceVariant = StudioSlate,
    outline = StudioStone
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun VideoBgRemoverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
        shapes = AppShapes,
        content = content
    )
}

private fun colorMix(
    a: androidx.compose.ui.graphics.Color,
    b: androidx.compose.ui.graphics.Color,
    ratioA: Float = 0.5f
): androidx.compose.ui.graphics.Color {
    val ratioB = 1f - ratioA
    return androidx.compose.ui.graphics.Color(
        red = a.red * ratioA + b.red * ratioB,
        green = a.green * ratioA + b.green * ratioB,
        blue = a.blue * ratioA + b.blue * ratioB,
        alpha = a.alpha * ratioA + b.alpha * ratioB
    )
}
