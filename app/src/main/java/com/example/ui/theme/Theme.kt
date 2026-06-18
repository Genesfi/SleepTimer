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

private val DarkColorScheme =
  darkColorScheme(
    primary = CozyCyan,
    onPrimary = MidnightBackground,
    secondary = RelaxingViolet,
    onSecondary = MidnightBackground,
    tertiary = CozyAmber,
    onTertiary = MidnightBackground,
    background = MidnightBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = MidnightBackground
  )

private val LightColorScheme = DarkColorScheme // Forced uniform dark theme for high-comfort bedtime usage

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force Dark theme for cozy bedtime comfort
  dynamicColor: Boolean = false, // Disable dynamic light/dark colors to preserve Sleep Timer brand identity
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
