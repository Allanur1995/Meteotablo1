package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MeteoPalette(
  val outerFrame: Color,
  val bezelSurface: Color,
  val panelSurface: Color,
  val fieldBackground: Color,
  val secondaryFieldBackground: Color,
  val buttonBackground: Color,
  val buttonActiveBackground: Color,
  val border: Color,
  val borderStrong: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val metarAmber: Color,
  val windArrow: Color,
  val headwindPositive: Color,
  val tailwindNegative: Color,
  val runwayFill: Color,
  val runwayStroke: Color,
  val compassStroke: Color,
  val statusBarBg: Color,
)

val DayMeteoPalette =
  MeteoPalette(
    outerFrame = Color(0xFF0A0D12),
    bezelSurface = Color(0xFFD5DAE1),
    panelSurface = Color(0xFFF3F5F8),
    fieldBackground = Color(0xFFFFFFFF),
    secondaryFieldBackground = Color(0xFFE7EBF0),
    buttonBackground = Color(0xFFFFFFFF),
    buttonActiveBackground = Color(0xFFDBEAFE),
    border = Color(0xFF8C96A4),
    borderStrong = Color(0xFF525D6B),
    textPrimary = Color(0xFF0D1117),
    textSecondary = Color(0xFF334155),
    textMuted = Color(0xFF64748B),
    metarAmber = Color(0xFFC85A00),
    windArrow = Color(0xFFD95300),
    headwindPositive = Color(0xFF0D7A3E),
    tailwindNegative = Color(0xFFC62828),
    runwayFill = Color(0xFFFCFDFF),
    runwayStroke = Color(0xFF1E293B),
    compassStroke = Color(0xFF1E293B),
    statusBarBg = Color(0xFFE8ECF1),
  )

val NightMeteoPalette =
  MeteoPalette(
    outerFrame = Color(0xFF030508),
    bezelSurface = Color(0xFF0B111A),
    panelSurface = Color(0xFF111A27),
    fieldBackground = Color(0xFF172233),
    secondaryFieldBackground = Color(0xFF0E1622),
    buttonBackground = Color(0xFF1A2638),
    buttonActiveBackground = Color(0xFF1E3A5F),
    border = Color(0xFF2D3E54),
    borderStrong = Color(0xFF475F7E),
    textPrimary = Color(0xFFEBF2FA),
    textSecondary = Color(0xFFA9B9CC),
    textMuted = Color(0xFF7B8FA6),
    metarAmber = Color(0xFFFF981A),
    windArrow = Color(0xFFFF8C1A),
    headwindPositive = Color(0xFF34D399),
    tailwindNegative = Color(0xFFF87171),
    runwayFill = Color(0xFF1A2536),
    runwayStroke = Color(0xFF94A3B8),
    compassStroke = Color(0xFF64748B),
    statusBarBg = Color(0xFF0D141F),
  )

val LocalMeteoPalette = staticCompositionLocalOf { DayMeteoPalette }
