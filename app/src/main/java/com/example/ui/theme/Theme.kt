package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val MeteoDayColorScheme =
  lightColorScheme(
    primary = Color(0xFF1E40AF),
    onPrimary = Color.White,
    secondary = Color(0xFFC85A00),
    onSecondary = Color.White,
    background = DayMeteoPalette.bezelSurface,
    onBackground = DayMeteoPalette.textPrimary,
    surface = DayMeteoPalette.panelSurface,
    onSurface = DayMeteoPalette.textPrimary,
    surfaceVariant = DayMeteoPalette.secondaryFieldBackground,
    onSurfaceVariant = DayMeteoPalette.textSecondary,
    outline = DayMeteoPalette.border,
  )

private val MeteoNightColorScheme =
  darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF09121F),
    secondary = Color(0xFFFF981A),
    onSecondary = Color(0xFF1A0D00),
    background = NightMeteoPalette.bezelSurface,
    onBackground = NightMeteoPalette.textPrimary,
    surface = NightMeteoPalette.panelSurface,
    onSurface = NightMeteoPalette.textPrimary,
    surfaceVariant = NightMeteoPalette.secondaryFieldBackground,
    onSurfaceVariant = NightMeteoPalette.textSecondary,
    outline = NightMeteoPalette.border,
  )

@Composable
fun MeteoTabloTheme(
  isNightMode: Boolean = false,
  content: @Composable () -> Unit,
) {
  val palette = if (isNightMode) NightMeteoPalette else DayMeteoPalette
  val colorScheme = if (isNightMode) MeteoNightColorScheme else MeteoDayColorScheme

  CompositionLocalProvider(LocalMeteoPalette provides palette) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content,
    )
  }
}
