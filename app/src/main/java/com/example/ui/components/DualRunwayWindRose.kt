package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.RunwayCalculation
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DualRunwayWindRosePanel(
  station: AirportStation,
  selectedRunwayPairIndex: Int,
  primaryCalc: RunwayCalculation,
  reciprocalCalc: RunwayCalculation,
  wind: WindData,
  windUnit: WindSpeedUnit,
  onSelectRunwayPair: (Int) -> Unit,
  onToggleUnit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val preferPrimary = primaryCalc.headwindKt >= reciprocalCalc.headwindKt
  val bestCalc = if (preferPrimary) primaryCalc else reciprocalCalc
  val bestHw = UnitConverter.convertWindFromKnots(bestCalc.headwindKt, windUnit)
  val bestXw = UnitConverter.convertWindFromKnots(bestCalc.crosswindKt, windUnit)

  val windHeader = when {
    wind.isCalm -> "Штиль · 0 ${windUnit.label}"
    wind.isVariable -> "VRB · ${UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)} ${windUnit.label}"
    wind.directionDeg != null -> {
      val spd = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)
      val gSuffix = wind.gustKt?.let { " G${UnitConverter.formatWindSpeedValue(it, windUnit)}" }.orEmpty()
      String.format(Locale.US, "%03d° · %d%s %s", wind.directionDeg, spd, gSuffix, windUnit.label)
    }
    else -> "--"
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("dual_runway_wind_rose_panel")
      .background(palette.panelSurface, RoundedCornerShape(4.dp))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
      .padding(10.dp),
  ) {
    // Top Header + Wind Unit Toggle
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Filled.FlightLand,
          contentDescription = null,
          tint = palette.metarAmber,
          modifier = Modifier.size(16.dp),
        )
        Column {
          Text(
            text = "ДВОЙНАЯ РОЗА ВПП · ${station.icao}",
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
          )
          Text(
            text = "Ветер: $windHeader · ВПП ${primaryCalc.designator} / ${reciprocalCalc.designator}",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
          )
        }
      }

      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("dual_rwy_unit_toggle")
          .clip(RoundedCornerShape(4.dp))
          .background(palette.buttonBackground)
          .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
          .clickable(onClick = onToggleUnit)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text(
          text = windUnit.label,
          color = palette.metarAmber,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    // Airport Runway Pair Selector Strip
    if (station.runways.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Полоса:",
          color = palette.textMuted,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.align(Alignment.CenterVertically),
        )
        station.runways.forEachIndexed { idx, pair ->
          val isSelected = idx == selectedRunwayPairIndex
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("dual_rwy_pair_chip_$idx")
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else palette.border,
                shape = RoundedCornerShape(4.dp),
              )
              .clickable { onSelectRunwayPair(idx) }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(
              text = "ВПП ${pair.primaryDesignator} / ${pair.reciprocalDesignator}",
              color = if (isSelected) palette.metarAmber else palette.textPrimary,
              fontSize = 11.5.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // Live Recommended Runway End Summary
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
        .testTag("dual_rwy_recommendation")
        .background(palette.headwindPositive.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
        .border(1.dp, palette.headwindPositive.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
        .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
      val hwSign = if (bestHw >= -0.05) "+" else ""
      Text(
        text = "Рекомендуется ВПП ${bestCalc.designator} (${String.format(Locale.US, "%03d°", bestCalc.headingDeg)})",
        color = palette.headwindPositive,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
      )
      Text(
        text = String.format(
          Locale.US,
          "Встр: %s%.1f · Бок: %.1f %s",
          hwSign,
          bestHw,
          bestXw,
          windUnit.label,
        ),
        color = palette.textPrimary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
    }

    // Responsive Layout: Unified Dual Compass Diagram + Dual End Telemetry Cards
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val sideBySide = maxWidth >= 560.dp
      if (sideBySide) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.fillMaxWidth(),
        ) {
          DualRunwayCompassCanvas(
            station = station,
            selectedRunwayPairIndex = selectedRunwayPairIndex,
            primaryCalc = primaryCalc,
            reciprocalCalc = reciprocalCalc,
            wind = wind,
            windUnit = windUnit,
            onToggleUnit = onToggleUnit,
            modifier = Modifier.weight(0.95f),
          )
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1.05f),
          ) {
            DualRunwayComponentCard(
              calc = primaryCalc,
              wind = wind,
              windUnit = windUnit,
              isRecommended = preferPrimary,
              modifier = Modifier.fillMaxWidth(),
            )
            DualRunwayComponentCard(
              calc = reciprocalCalc,
              wind = wind,
              windUnit = windUnit,
              isRecommended = !preferPrimary,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      } else {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          DualRunwayCompassCanvas(
            station = station,
            selectedRunwayPairIndex = selectedRunwayPairIndex,
            primaryCalc = primaryCalc,
            reciprocalCalc = reciprocalCalc,
            wind = wind,
            windUnit = windUnit,
            onToggleUnit = onToggleUnit,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            DualRunwayComponentCard(
              calc = primaryCalc,
              wind = wind,
              windUnit = windUnit,
              isRecommended = preferPrimary,
              modifier = Modifier.weight(1f),
            )
            DualRunwayComponentCard(
              calc = reciprocalCalc,
              wind = wind,
              windUnit = windUnit,
              isRecommended = !preferPrimary,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun DualRunwayCompassCanvas(
  station: AirportStation,
  selectedRunwayPairIndex: Int,
  primaryCalc: RunwayCalculation,
  reciprocalCalc: RunwayCalculation,
  wind: WindData,
  windUnit: WindSpeedUnit,
  onToggleUnit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val textMeasurer = rememberTextMeasurer()

  val maxScaleKt = 40f
  val animatedWindDir by animateFloatAsState(
    targetValue = (wind.directionDeg ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "dualWindDir",
  )
  val animatedSpeedRatio by animateFloatAsState(
    targetValue = (wind.speedKt.toFloat() / maxScaleKt).coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "dualWindSpeed",
  )
  val animatedPrimaryHdg by animateFloatAsState(
    targetValue = primaryCalc.headingDeg.toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "dualRwyHdg",
  )

  val accessDesc = "Двойная роза ВПП ${primaryCalc.designator} и ${reciprocalCalc.designator}, ветер ${wind.speedKt} узлов"

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .testTag("dual_rwy_compass_canvas")
      .semantics { contentDescription = accessDesc }
      .sizeIn(minWidth = 190.dp, minHeight = 190.dp)
      .aspectRatio(1.06f)
      .clip(RoundedCornerShape(4.dp))
      .background(palette.fieldBackground)
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
      .clickable(onClick = onToggleUnit),
  ) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
    ) {
      if (size.width <= 1f || size.height <= 1f) return@Canvas
      val cx = size.width / 2f
      val cy = size.height / 2f
      val radius = (minOf(size.width, size.height) * 0.41f).coerceAtLeast(1f)

      // Outer gradient fill
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(palette.panelSurface, palette.secondaryFieldBackground.copy(alpha = 0.72f)),
          center = Offset(cx, cy),
          radius = radius,
        ),
        radius = radius,
        center = Offset(cx, cy),
      )

      // Concentric speed rings (10, 20, 30, 40 kt scaled)
      listOf(0.30f, 0.54f, 0.77f, 1.0f).forEachIndexed { idx, frac ->
        val r = radius * frac
        val isOuter = idx == 3
        drawCircle(
          color = if (isOuter) palette.compassStroke else palette.border.copy(alpha = 0.55f),
          radius = r,
          center = Offset(cx, cy),
          style = Stroke(
            width = if (isOuter) 1.6.dp.toPx() else 0.9.dp.toPx(),
            pathEffect = if (isOuter) null else PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
          ),
        )
      }

      // 10° / 30° compass ticks + cardinal & degree labels
      for (deg in 0 until 360 step 10) {
        val rad = (deg - 90.0) * PI / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val isMajor = deg % 30 == 0
        val innerR = if (isMajor) radius - 5.dp.toPx() else radius - 2.5.dp.toPx()
        val outerR = if (isMajor) radius + 4.dp.toPx() else radius + 2.dp.toPx()
        drawLine(
          color = palette.compassStroke,
          start = Offset(cx + innerR * cosA, cy + innerR * sinA),
          end = Offset(cx + outerR * cosA, cy + outerR * sinA),
          strokeWidth = if (isMajor) 1.4.dp.toPx() else 0.8.dp.toPx(),
        )
        if (isMajor) {
          val label = when (deg) {
            0 -> "N"
            90 -> "E"
            180 -> "S"
            270 -> "W"
            else -> String.format(Locale.US, "%02d", deg / 10)
          }
          val isCardinal = deg % 90 == 0
          val measured = textMeasurer.measure(
            text = label,
            style = TextStyle(
              color = if (isCardinal) palette.metarAmber else palette.textPrimary,
              fontSize = if (isCardinal) 11.sp else 9.5.sp,
              fontWeight = if (isCardinal) FontWeight.ExtraBold else FontWeight.Medium,
            ),
          )
          val labelR = radius + 12.5.dp.toPx()
          drawText(
            measured,
            topLeft = Offset(
              cx + labelR * cosA - measured.size.width / 2f,
              cy + labelR * sinA - measured.size.height / 2f,
            ),
          )
        }
      }

      // Draw faint alternate runways if the airport has >1 runway pair
      station.runways.forEachIndexed { idx, altPair ->
        if (idx != selectedRunwayPairIndex) {
          rotate(degrees = altPair.primaryHeadingDeg.toFloat(), pivot = Offset(cx, cy)) {
            val w = radius * 0.16f
            val l = radius * 1.56f
            drawRect(
              color = palette.border.copy(alpha = 0.25f),
              topLeft = Offset(cx - w / 2f, cy - l / 2f),
              size = Size(w, l),
            )
            drawRect(
              color = palette.textMuted.copy(alpha = 0.45f),
              topLeft = Offset(cx - w / 2f, cy - l / 2f),
              size = Size(w, l),
              style = Stroke(width = 0.9.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)),
            )
          }
        }
      }

      // Draw Primary + Reciprocal dual runway strip
      rotate(degrees = animatedPrimaryHdg, pivot = Offset(cx, cy)) {
        val rwyW = radius * 0.24f
        val rwyL = radius * 1.72f
        val left = cx - rwyW / 2f
        val top = cy - rwyL / 2f

        drawRect(
          color = palette.runwayFill,
          topLeft = Offset(left, top),
          size = Size(rwyW, rwyL),
        )
        drawRect(
          color = palette.runwayStroke,
          topLeft = Offset(left, top),
          size = Size(rwyW, rwyL),
          style = Stroke(width = 1.4.dp.toPx()),
        )
        drawLine(
          color = palette.textPrimary.copy(alpha = 0.55f),
          start = Offset(cx, top + 6.dp.toPx()),
          end = Offset(cx, top + rwyL - 6.dp.toPx()),
          strokeWidth = 1.1.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f),
        )

        // Highlight chevron pointing along recommended runway direction
        val recPointsUp = primaryCalc.headwindKt >= reciprocalCalc.headwindKt
        val chevronY = if (recPointsUp) top + rwyL * 0.22f else top + rwyL * 0.78f
        val dirSign = if (recPointsUp) -1f else 1f
        val chevron = Path().apply {
          moveTo(cx, chevronY + dirSign * 6.dp.toPx())
          lineTo(cx - 5.dp.toPx(), chevronY - dirSign * 4.dp.toPx())
          lineTo(cx + 5.dp.toPx(), chevronY - dirSign * 4.dp.toPx())
          close()
        }
        drawPath(chevron, color = palette.headwindPositive)
      }

      // Designator badges at both runway ends (top = heading, bottom = reciprocal)
      val drawEndLabel: (String, Int, Boolean) -> Unit = { designator, hdg, isBest ->
        val rad = (hdg - 90.0) * PI / 180.0
        val posR = radius * 0.67f
        val px = cx + posR * cos(rad).toFloat()
        val py = cy + posR * sin(rad).toFloat()
        val m = textMeasurer.measure(
          text = designator,
          style = TextStyle(
            color = if (isBest) palette.headwindPositive else palette.textPrimary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
          ),
        )
        val pad = 3.dp.toPx()
        drawRect(
          color = palette.fieldBackground.copy(alpha = 0.92f),
          topLeft = Offset(px - m.size.width / 2f - pad, py - m.size.height / 2f - 1.dp.toPx()),
          size = Size(m.size.width + pad * 2f, m.size.height + 2.dp.toPx()),
        )
        drawRect(
          color = if (isBest) palette.headwindPositive else palette.borderStrong,
          topLeft = Offset(px - m.size.width / 2f - pad, py - m.size.height / 2f - 1.dp.toPx()),
          size = Size(m.size.width + pad * 2f, m.size.height + 2.dp.toPx()),
          style = Stroke(width = 1.dp.toPx()),
        )
        drawText(m, topLeft = Offset(px - m.size.width / 2f, py - m.size.height / 2f))
      }
      val primaryBest = primaryCalc.headwindKt >= reciprocalCalc.headwindKt
      drawEndLabel(primaryCalc.designator, primaryCalc.headingDeg, primaryBest)
      drawEndLabel(reciprocalCalc.designator, reciprocalCalc.headingDeg, !primaryBest)

      // Variable wind sector arc
      if (wind.variableFromDeg != null && wind.variableToDeg != null) {
        val sweep = ((wind.variableToDeg - wind.variableFromDeg + 360) % 360).toFloat()
        val wR = radius * (0.28f + 0.60f * animatedSpeedRatio)
        drawArc(
          color = palette.windArrow.copy(alpha = 0.22f),
          startAngle = wind.variableFromDeg - 90f,
          sweepAngle = sweep,
          useCenter = true,
          topLeft = Offset(cx - wR, cy - wR),
          size = Size(wR * 2f, wR * 2f),
        )
      }

      // Live Wind Vector + Orthogonal Crosswind/Headwind Decomposition Guide
      if (wind.isCalm) {
        drawCircle(
          color = palette.headwindPositive,
          radius = radius * 0.20f,
          center = Offset(cx, cy),
          style = Stroke(width = 2.dp.toPx()),
        )
      } else if (wind.isVariable) {
        drawCircle(
          color = palette.windArrow,
          radius = radius * (0.25f + 0.52f * animatedSpeedRatio),
          center = Offset(cx, cy),
          style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)),
        )
      } else if (wind.directionDeg != null) {
        val vecLen = radius * (0.28f + 0.62f * animatedSpeedRatio.coerceAtLeast(0.18f))
        val windRad = (animatedWindDir - 90.0) * PI / 180.0
        val tipX = cx + vecLen * cos(windRad).toFloat()
        val tipY = cy + vecLen * sin(windRad).toFloat()

        // Orthogonal projection point onto active runway axis
        val rwyRad = (animatedPrimaryHdg - 90.0) * PI / 180.0
        val ux = cos(rwyRad).toFloat()
        val uy = sin(rwyRad).toFloat()
        val dot = (tipX - cx) * ux + (tipY - cy) * uy
        val projX = cx + dot * ux
        val projY = cy + dot * uy

        // Crosswind dashed projection line (wind origin tip perpendicular to runway axis)
        drawLine(
          color = palette.metarAmber.copy(alpha = 0.72f),
          start = Offset(tipX, tipY),
          end = Offset(projX, projY),
          strokeWidth = 1.6.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
        )

        // Wind directional inward arrow
        rotate(degrees = animatedWindDir, pivot = Offset(cx, cy)) {
          val outerY = cy - radius * 0.90f
          val innerY = cy - radius * 0.42f
          drawLine(
            color = palette.windArrow,
            start = Offset(cx, outerY),
            end = Offset(cx, innerY - 7.dp.toPx()),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
          )
          val head = Path().apply {
            moveTo(cx, innerY)
            lineTo(cx - 6.dp.toPx(), innerY - 10.dp.toPx())
            lineTo(cx + 6.dp.toPx(), innerY - 10.dp.toPx())
            close()
          }
          drawPath(head, color = palette.windArrow)
        }
      }
    }

    // Center Compact Wind Readout Badge
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .background(palette.fieldBackground.copy(alpha = 0.94f), RoundedCornerShape(4.dp))
        .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      val spdVal = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)
      Text(
        text = when {
          wind.isCalm -> "ШТИЛЬ"
          wind.isVariable -> "VRB"
          wind.directionDeg != null -> String.format(Locale.US, "%03d°", wind.directionDeg)
          else -> "---"
        },
        color = palette.metarAmber,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
      )
      Text(
        text = "$spdVal ${windUnit.label}",
        color = palette.textPrimary,
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun DualRunwayComponentCard(
  calc: RunwayCalculation,
  wind: WindData,
  windUnit: WindSpeedUnit,
  isRecommended: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val hwVal = UnitConverter.convertWindFromKnots(calc.headwindKt, windUnit)
  val xwVal = UnitConverter.convertWindFromKnots(calc.crosswindKt, windUnit)
  val isTailwind = hwVal < -0.05

  // Determine crosswind side relative to aircraft on approach heading
  val crossSide = if (wind.isCalm || wind.isVariable || wind.directionDeg == null || calc.crosswindKt < 0.2) {
    ""
  } else {
    val rel = ((wind.directionDeg - calc.headingDeg + 540) % 360) - 180
    when {
      rel > 2 -> " · справа"
      rel < -2 -> " · слева"
      else -> " · по оси"
    }
  }

  val hwColor = if (isTailwind) palette.tailwindNegative else palette.headwindPositive
  val xwColor = when {
    calc.crosswindKt >= 15.0 -> palette.tailwindNegative
    calc.crosswindKt >= 10.0 -> palette.metarAmber
    else -> palette.textPrimary
  }

  val maxBarRef = UnitConverter.convertWindFromKnots(30.0, windUnit).toFloat().coerceAtLeast(1f)
  val hwRatio = (abs(hwVal).toFloat() / maxBarRef).coerceIn(0f, 1f)
  val xwRatio = (abs(xwVal).toFloat() / maxBarRef).coerceIn(0f, 1f)

  val gustInfo = wind.gustKt?.takeIf { wind.directionDeg != null }?.let { gKt ->
    val diffRad = (wind.directionDeg!! - calc.headingDeg) * PI / 180.0
    val gHw = UnitConverter.convertWindFromKnots(gKt * cos(diffRad), windUnit)
    val gXw = UnitConverter.convertWindFromKnots(abs(gKt * sin(diffRad)), windUnit)
    String.format(Locale.US, "Порывы: встр %.1f · бок %.1f %s", gHw, gXw, windUnit.label)
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(5.dp),
    modifier = modifier
      .semantics(mergeDescendants = true) {}
      .testTag("dual_rwy_card_${calc.designator}")
      .background(
        if (isRecommended) palette.buttonActiveBackground else palette.fieldBackground,
        RoundedCornerShape(4.dp),
      )
      .border(
        width = if (isRecommended) 1.5.dp else 1.dp,
        color = if (isRecommended) palette.headwindPositive else palette.borderStrong,
        shape = RoundedCornerShape(4.dp),
      )
      .padding(horizontal = 9.dp, vertical = 7.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "ВПП ${calc.designator} · ${String.format(Locale.US, "%03d°", calc.headingDeg)}",
        color = palette.textPrimary,
        fontSize = 12.5.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
      )
      if (isRecommended) {
        Text(
          text = "РЕКОМЕНД.",
          color = palette.headwindPositive,
          fontSize = 9.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier
            .background(palette.headwindPositive.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        )
      }
    }

    // Headwind / Tailwind gauge bar
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = if (isTailwind) "Попутный (Tailwind)" else "Встречный (Headwind)",
          color = palette.textSecondary,
          fontSize = 10.sp,
        )
        Text(
          text = String.format(
            Locale.US,
            "%s%.1f %s",
            if (isTailwind) "-" else "+",
            abs(hwVal),
            windUnit.label,
          ),
          color = hwColor,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.testTag("dual_rwy_hw_${calc.designator}"),
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(5.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(palette.secondaryFieldBackground),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(hwRatio.coerceAtLeast(0.04f))
            .height(5.dp)
            .background(hwColor, RoundedCornerShape(3.dp)),
        )
      }
    }

    // Crosswind gauge bar
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Боковой (Crosswind)$crossSide",
          color = palette.textSecondary,
          fontSize = 10.sp,
        )
        Text(
          text = String.format(Locale.US, "%.1f %s", abs(xwVal), windUnit.label),
          color = xwColor,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.testTag("dual_rwy_xw_${calc.designator}"),
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(5.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(palette.secondaryFieldBackground),
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth(xwRatio.coerceAtLeast(0.04f))
            .height(5.dp)
            .background(xwColor, RoundedCornerShape(3.dp)),
        )
      }
    }

    if (gustInfo != null) {
      Text(
        text = gustInfo,
        color = palette.metarAmber,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}
