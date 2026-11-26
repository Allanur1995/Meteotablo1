package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.RunwayCalculation
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendSummary
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WindRoseCanvas(
  wind: WindData,
  windUnit: WindSpeedUnit,
  onToggleUnit: () -> Unit,
  modifier: Modifier = Modifier,
  activeRunway: RunwayCalculation? = null,
  metarFetchSeq: Long = 0L,
) {
  val palette = LocalMeteoPalette.current
  val textMeasurer = rememberTextMeasurer()

  val maxScaleSpeed = when (windUnit) {
    WindSpeedUnit.KT -> 40f
    WindSpeedUnit.MPS -> 20f
    WindSpeedUnit.KPH -> 80f
  }
  val ringSteps = when (windUnit) {
    WindSpeedUnit.KT -> listOf(10, 20, 30, 40)
    WindSpeedUnit.MPS -> listOf(5, 10, 15, 20)
    WindSpeedUnit.KPH -> listOf(20, 40, 60, 80)
  }

  val rawSpeed = UnitConverter.convertWindFromKnots(wind.speedKt, windUnit).toFloat()
  val rawGust = wind.gustKt?.let { UnitConverter.convertWindFromKnots(it, windUnit).toFloat() } ?: 0f

  val animatedDir by animateFloatAsState(
    targetValue = (wind.directionDeg ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "windRoseDirection",
  )
  val animatedSpeedRatio by animateFloatAsState(
    targetValue = (rawSpeed / maxScaleSpeed).coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "windRoseSpeedRatio",
  )
  val animatedGustRatio by animateFloatAsState(
    targetValue = (rawGust / maxScaleSpeed).coerceIn(0f, 1f),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "windRoseGustRatio",
  )
  val animatedRunwayHeading by animateFloatAsState(
    targetValue = (activeRunway?.headingDeg ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "windRoseRunwayHeading",
  )
  val fetchRotatePulse = remember { Animatable(0f) }
  var lastFetchSeq by remember { mutableLongStateOf(metarFetchSeq) }
  LaunchedEffect(metarFetchSeq) {
    if (metarFetchSeq > 0L && metarFetchSeq != lastFetchSeq) {
      lastFetchSeq = metarFetchSeq
      fetchRotatePulse.snapTo(-9f)
      fetchRotatePulse.animateTo(
        targetValue = 0f,
        animationSpec = spring(
          dampingRatio = 0.52f,
          stiffness = Spring.StiffnessMediumLow,
        ),
      )
    }
  }

  val speedDisplay = rawSpeed.roundToInt()
  val dirDisplay = when {
    wind.isCalm -> "Штиль"
    wind.isVariable -> "VRB"
    wind.directionDeg != null -> String.format(Locale.US, "%03d°", wind.directionDeg)
    else -> "---"
  }

  val accessibilitySummary = buildString {
    append("Роза ветров: направление $dirDisplay, скорость $speedDisplay ${windUnit.label}")
    if (rawGust > 0f) append(", порывы ${rawGust.roundToInt()} ${windUnit.label}")
    if (activeRunway != null) append(", ВПП ${activeRunway.designator}")
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .testTag("wind_rose_canvas")
      .semantics { contentDescription = accessibilitySummary }
      .sizeIn(minWidth = 200.dp, minHeight = 200.dp)
      .aspectRatio(1f)
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
      val outerRadius = (minOf(size.width, size.height) * 0.40f).coerceAtLeast(1f)
      val hubRadius = outerRadius * 0.24f
      val usableSpan = outerRadius - hubRadius

      // Background radial shading
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            palette.panelSurface,
            palette.secondaryFieldBackground.copy(alpha = 0.7f),
          ),
          center = Offset(cx, cy),
          radius = outerRadius,
        ),
        radius = outerRadius,
        center = Offset(cx, cy),
      )

      // Calibrated concentric wind speed rings
      ringSteps.forEachIndexed { idx, stepValue ->
        val fraction = (idx + 1).toFloat() / ringSteps.size.toFloat()
        val ringR = hubRadius + usableSpan * fraction
        val isOuter = idx == ringSteps.lastIndex

        drawCircle(
          color = if (isOuter) palette.compassStroke else palette.border.copy(alpha = 0.65f),
          radius = ringR,
          center = Offset(cx, cy),
          style = Stroke(
            width = if (isOuter) 1.6.dp.toPx() else 0.9.dp.toPx(),
            pathEffect = if (isOuter) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
          ),
        )

        // Speed ring scale label along 045° diagonal
        val labelRad = (45.0 - 90.0) * PI / 180.0
        val lx = cx + ringR * cos(labelRad).toFloat()
        val ly = cy + ringR * sin(labelRad).toFloat()
        val measuredRing = textMeasurer.measure(
          text = stepValue.toString(),
          style = TextStyle(
            color = palette.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        )
        drawText(
          measuredRing,
          topLeft = Offset(
            lx - measuredRing.size.width / 2f,
            ly - measuredRing.size.height / 2f,
          ),
        )
      }

      // Crosshair axes (N-S, E-W)
      drawLine(
        color = palette.border.copy(alpha = 0.45f),
        start = Offset(cx, cy - outerRadius),
        end = Offset(cx, cy + outerRadius),
        strokeWidth = 0.8.dp.toPx(),
      )
      drawLine(
        color = palette.border.copy(alpha = 0.45f),
        start = Offset(cx - outerRadius, cy),
        end = Offset(cx + outerRadius, cy),
        strokeWidth = 0.8.dp.toPx(),
      )

      // Degree ticks (every 10 deg, major every 30 deg) and compass labels
      rotate(degrees = fetchRotatePulse.value * 0.6f, pivot = Offset(cx, cy)) {
        for (deg in 0 until 360 step 10) {
          val rad = (deg - 90.0) * PI / 180.0
          val cosA = cos(rad).toFloat()
          val sinA = sin(rad).toFloat()
          val isMajor = deg % 30 == 0
          val tickInner = if (isMajor) outerRadius - 6.dp.toPx() else outerRadius - 3.dp.toPx()
          val tickOuter = if (isMajor) outerRadius + 4.dp.toPx() else outerRadius + 2.dp.toPx()

          drawLine(
            color = palette.compassStroke,
            start = Offset(cx + tickInner * cosA, cy + tickInner * sinA),
            end = Offset(cx + tickOuter * cosA, cy + tickOuter * sinA),
            strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.9.dp.toPx(),
          )
        }
      }
      for (deg in 0 until 360 step 30) {
        val rad = (deg - 90.0) * PI / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
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
            fontSize = if (isCardinal) 11.5.sp else 10.sp,
            fontWeight = if (isCardinal) FontWeight.ExtraBold else FontWeight.Medium,
          ),
        )
        val labelR = outerRadius + 14.dp.toPx()
        drawText(
          measured,
          topLeft = Offset(
            cx + labelR * cosA - measured.size.width / 2f,
            cy + labelR * sinA - measured.size.height / 2f,
          ),
        )
      }

      // Variable wind sector wedge (if METAR contains dddVddd)
      val varFrom = wind.variableFromDeg
      val varTo = wind.variableToDeg
      if (varFrom != null && varTo != null) {
        val sweep = ((varTo - varFrom + 360) % 360).toFloat()
        val startAngle = (varFrom - 90f)
        val wedgeR = hubRadius + usableSpan * animatedSpeedRatio.coerceAtLeast(0.25f)
        drawArc(
          color = palette.windArrow.copy(alpha = 0.22f),
          startAngle = startAngle,
          sweepAngle = sweep,
          useCenter = true,
          topLeft = Offset(cx - wedgeR, cy - wedgeR),
          size = Size(wedgeR * 2f, wedgeR * 2f),
        )
      }

      // Runway alignment strip inside compass if an active runway is provided
      if (activeRunway != null) {
        rotate(degrees = animatedRunwayHeading, pivot = Offset(cx, cy)) {
          val rwyWidth = outerRadius * 0.24f
          val rwyLength = outerRadius * 1.72f
          val rwyLeft = cx - rwyWidth / 2f
          val rwyTop = cy - rwyLength / 2f

          drawRect(
            color = palette.runwayFill.copy(alpha = 0.68f),
            topLeft = Offset(rwyLeft, rwyTop),
            size = Size(rwyWidth, rwyLength),
          )
          drawRect(
            color = palette.runwayStroke.copy(alpha = 0.72f),
            topLeft = Offset(rwyLeft, rwyTop),
            size = Size(rwyWidth, rwyLength),
            style = Stroke(width = 1.2.dp.toPx()),
          )
          // Dashed runway centerline
          drawLine(
            color = palette.runwayStroke.copy(alpha = 0.65f),
            start = Offset(cx, rwyTop + 6.dp.toPx()),
            end = Offset(cx, rwyTop + rwyLength - 6.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f),
          )
          // Small directional landing chevron at approach end pointing toward runway heading
          val arrowTipY = cy + rwyLength * 0.28f
          val rwyArrow = Path().apply {
            moveTo(cx, arrowTipY)
            lineTo(cx - 4.dp.toPx(), arrowTipY + 7.dp.toPx())
            lineTo(cx + 4.dp.toPx(), arrowTipY + 7.dp.toPx())
            close()
          }
          drawPath(rwyArrow, color = palette.runwayStroke)
        }
      }

      // Graphical Wind Speed & Direction Vector
      if (wind.isCalm) {
        drawCircle(
          color = palette.headwindPositive,
          radius = hubRadius + 5.dp.toPx(),
          center = Offset(cx, cy),
          style = Stroke(width = 2.dp.toPx()),
        )
      } else if (wind.isVariable) {
        val vrbR = hubRadius + usableSpan * animatedSpeedRatio.coerceAtLeast(0.18f)
        drawCircle(
          color = palette.windArrow,
          radius = vrbR,
          center = Offset(cx, cy),
          style = Stroke(
            width = 2.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
          ),
        )
      } else if (wind.directionDeg != null) {
        val effectiveDir = animatedDir + fetchRotatePulse.value
        // Gust outer wedge/ring if gusts present
        if (rawGust > rawSpeed) {
          val gustR = hubRadius + usableSpan * animatedGustRatio.coerceAtLeast(0.22f)
          drawArc(
            color = palette.tailwindNegative.copy(alpha = 0.22f),
            startAngle = effectiveDir - 90f - 10f,
            sweepAngle = 20f,
            useCenter = true,
            topLeft = Offset(cx - gustR, cy - gustR),
            size = Size(gustR * 2f, gustR * 2f),
          )
          rotate(degrees = effectiveDir, pivot = Offset(cx, cy)) {
            drawLine(
              color = palette.tailwindNegative,
              start = Offset(cx, cy - hubRadius),
              end = Offset(cx, cy - gustR),
              strokeWidth = 2.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f),
            )
          }
        }

        // Sustained wind directional wedge & vector arrow scaled to current wind speed
        val vectorR = hubRadius + usableSpan * animatedSpeedRatio.coerceAtLeast(0.20f)
        drawArc(
          color = palette.windArrow.copy(alpha = 0.24f),
          startAngle = effectiveDir - 90f - 12f,
          sweepAngle = 24f,
          useCenter = true,
          topLeft = Offset(cx - vectorR, cy - vectorR),
          size = Size(vectorR * 2f, vectorR * 2f),
        )

        rotate(degrees = effectiveDir, pivot = Offset(cx, cy)) {
          // Outer origin pointer arrow (pointing inward toward center from wind origin)
          val shaftStartY = cy - vectorR
          val shaftEndY = cy - hubRadius - 4.dp.toPx()

          drawLine(
            color = palette.windArrow,
            start = Offset(cx, shaftStartY),
            end = Offset(cx, shaftEndY),
            strokeWidth = 3.5.dp.toPx(),
            cap = StrokeCap.Round,
          )

          // Arrowhead pointing inward to hub
          val tipY = cy - hubRadius - 1.dp.toPx()
          val arrowHead = Path().apply {
            moveTo(cx, tipY)
            lineTo(cx - 6.dp.toPx(), tipY - 10.dp.toPx())
            lineTo(cx + 6.dp.toPx(), tipY - 10.dp.toPx())
            close()
          }
          drawPath(arrowHead, color = palette.windArrow)

          // Meteorological speed barbs near the outer tail (each full barb = 10 kt, half = 5 kt)
          var remainingKt = wind.speedKt
          var barbOffset = 2.dp.toPx()
          while (remainingKt >= 5 && (shaftStartY + barbOffset) < shaftEndY - 12.dp.toPx()) {
            val isFull = remainingKt >= 10
            val barbLen = if (isFull) 11.dp.toPx() else 6.dp.toPx()
            val yPos = shaftStartY + barbOffset
            drawLine(
              color = palette.windArrow,
              start = Offset(cx, yPos),
              end = Offset(cx + barbLen, yPos - 4.dp.toPx()),
              strokeWidth = 2.dp.toPx(),
              cap = StrokeCap.Round,
            )
            remainingKt -= if (isFull) 10 else 5
            barbOffset += 5.dp.toPx()
          }

          // Outer speed dot marker at wind speed radius
          drawCircle(
            color = palette.windArrow,
            radius = 4.dp.toPx(),
            center = Offset(cx, shaftStartY),
          )
        }
      }

      // Center Hub Circle
      drawCircle(
        color = palette.fieldBackground,
        radius = hubRadius,
        center = Offset(cx, cy),
      )
      drawCircle(
        color = palette.borderStrong,
        radius = hubRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 1.4.dp.toPx()),
      )
    }

    // Center Hub Text Readout (Speed + Unit + Direction)
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = speedDisplay.toString(),
        color = palette.textPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold,
      )
      Text(
        text = windUnit.label,
        color = palette.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = dirDisplay,
        color = palette.metarAmber,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun WindRoseCard(
  wind: WindData,
  windUnit: WindSpeedUnit,
  onToggleUnit: () -> Unit,
  windTrend: WindTrendSummary? = null,
  activeRunway: RunwayCalculation? = null,
  metarFetchSeq: Long = 0L,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val speedMain = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)
  val gustMain = wind.gustKt?.let { UnitConverter.formatWindSpeedValue(it, windUnit) }
  val dirText = when {
    wind.isCalm -> "Штиль"
    wind.isVariable -> "Переменный (VRB)"
    wind.directionDeg != null -> String.format(Locale.US, "%03d°", wind.directionDeg)
    else -> "--"
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .background(palette.panelSurface)
      .border(1.dp, palette.border)
      .padding(10.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      WindRoseCanvas(
        wind = wind,
        windUnit = windUnit,
        onToggleUnit = onToggleUnit,
        activeRunway = activeRunway,
        metarFetchSeq = metarFetchSeq,
        modifier = Modifier.weight(1f),
      )

      Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = "РОЗА ВЕТРОВ (ГРАФИК)",
          color = palette.textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp,
        )
        WindStatBox(label = "Направление", value = dirText)
        WindStatBox(label = "Скорость", value = "$speedMain ${windUnit.label}")
        WindStatBox(
          label = "Порывы",
          value = gustMain?.let { "$it ${windUnit.label}" } ?: "Нет",
        )
        if (wind.variableFromDeg != null && wind.variableToDeg != null) {
          WindStatBox(
            label = "Сектор",
            value = "${wind.variableFromDeg}°–${wind.variableToDeg}°",
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Нажмите на розу ветров для смены kt ↔ м/с ↔ км/ч",
          color = palette.textMuted,
          fontSize = 10.5.sp,
          lineHeight = 13.sp,
        )
      }
    }

    if (activeRunway != null) {
      val hw = UnitConverter.convertWindFromKnots(activeRunway.headwindKt, windUnit)
      val xw = UnitConverter.convertWindFromKnots(activeRunway.crosswindKt, windUnit)
      val isTailwind = hw < -0.05
      val hwLabel = if (isTailwind) "Попутный" else "Встречный"
      val hwColor = if (isTailwind) palette.tailwindNegative else palette.headwindPositive
      val xwColor = when {
        activeRunway.crosswindKt >= 15.0 -> palette.tailwindNegative
        activeRunway.crosswindKt >= 10.0 -> palette.metarAmber
        else -> palette.textPrimary
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("wind_rose_runway_strip"),
      ) {
        WindStatBox(
          label = "ВПП ${activeRunway.designator} · $hwLabel",
          value = String.format(Locale.US, "%.1f %s", abs(hw), windUnit.label),
          valueColor = hwColor,
          modifier = Modifier.weight(1f),
        )
        WindStatBox(
          label = "ВПП ${activeRunway.designator} · Боковой",
          value = String.format(Locale.US, "%.1f %s", xw, windUnit.label),
          valueColor = xwColor,
          modifier = Modifier.weight(1f),
        )
      }
    }

    if (windTrend != null) {
      WindTrendIndicator(
        trend = windTrend,
        windUnit = windUnit,
      )
    }
  }
}

@Composable
private fun WindStatBox(
  label: String,
  value: String,
  valueColor: Color? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(palette.fieldBackground, RoundedCornerShape(3.dp))
      .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
      .padding(horizontal = 8.dp, vertical = 5.dp),
  ) {
    Text(
      text = label,
      color = palette.textMuted,
      fontSize = 10.sp,
    )
    Text(
      text = value,
      color = valueColor ?: palette.textPrimary,
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}
