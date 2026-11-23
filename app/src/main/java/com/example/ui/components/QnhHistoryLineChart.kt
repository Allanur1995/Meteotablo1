package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.example.model.PressureUnit
import com.example.model.QnhHistoryPoint
import com.example.model.QnhTrendSummary
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QnhHistoryLineChart(
  trend: QnhTrendSummary,
  pressureUnit: PressureUnit,
  onTogglePressureUnit: (() -> Unit)? = null,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val textMeasurer = rememberTextMeasurer()
  val points = trend.points
  var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
  var canvasWidthPx by remember { mutableStateOf(0f) }

  val chartAccent = MaterialTheme.colorScheme.primary
  val isaColor = palette.metarAmber

  fun formatVal(hpa: Int?, inHg: Double? = null): String {
    return when (pressureUnit) {
      PressureUnit.HPA -> hpa?.toString() ?: "----"
      PressureUnit.INHG -> {
        val resolved = inHg ?: hpa?.let { UnitConverter.convertHpaToInHg(it) }
        resolved?.let { String.format(Locale.US, "%.2f", it) } ?: "--.--"
      }
    }
  }

  fun formatAxisNumeric(rawValue: Double): String {
    return when (pressureUnit) {
      PressureUnit.HPA -> rawValue.roundToInt().toString()
      PressureUnit.INHG -> String.format(Locale.US, "%.2f", rawValue)
    }
  }

  val deltaText = remember(trend, pressureUnit) {
    val deltaHpa = trend.delta24hHpa
    if (deltaHpa == null || points.size < 2) {
      "—"
    } else if (pressureUnit == PressureUnit.HPA) {
      val sign = if (deltaHpa > 0) "+" else ""
      "$sign$deltaHpa гПа"
    } else {
      val deltaInHg = points.last().qnhInHg - points.first().qnhInHg
      val sign = if (deltaInHg > 0.004) "+" else ""
      "$sign${String.format(Locale.US, "%.2f", deltaInHg)} дм"
    }
  }

  val deltaColor = when {
    (trend.delta24hHpa ?: 0) > 1 -> palette.headwindPositive
    (trend.delta24hHpa ?: 0) < -1 -> palette.tailwindNegative
    else -> palette.textSecondary
  }

  val minText = formatVal(trend.minHpa)
  val maxText = formatVal(trend.maxHpa)
  val latestText = formatVal(trend.latestHpa, points.lastOrNull()?.qnhInHg)

  Column(
    verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 8.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("qnh_line_chart_card")
      .background(palette.panelSurface, RoundedCornerShape(4.dp))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
      .padding(if (compactMode) 8.dp else 10.dp),
  ) {
    // Header row
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
          imageVector = Icons.Filled.ShowChart,
          contentDescription = "График давления QNH за 24 часа",
          tint = palette.metarAmber,
          modifier = Modifier.size(18.dp),
        )
        Column {
          Text(
            text = "ТРЕНД QNH ЗА 24 ЧАСА · ${trend.icao}",
            color = palette.textPrimary,
            fontSize = if (compactMode) 11.5.sp else 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
          )
          Text(
            text = if (points.isEmpty()) {
              "Нет архивных замеров давления"
            } else {
              "Точек: ${points.size} · стандарт ISA ${if (pressureUnit == PressureUnit.HPA) "1013 гПа" else "29.92 дм"}"
            },
            color = palette.textSecondary,
            fontSize = 10.5.sp,
          )
        }
      }

      if (onTogglePressureUnit != null) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("qnh_chart_unit_toggle")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onTogglePressureUnit)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = pressureUnit.label,
            color = palette.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    // Min / Max / Latest / 24h Δ summary chips
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("qnh_chart_stats_row"),
    ) {
      QnhStatPill(label = "MIN", value = "$minText ${pressureUnit.label}", accent = palette.textPrimary)
      QnhStatPill(label = "MAX", value = "$maxText ${pressureUnit.label}", accent = palette.textPrimary)
      QnhStatPill(label = "ТЕК.", value = "$latestText ${pressureUnit.label}", accent = palette.metarAmber)
      QnhStatPill(label = "Δ 24Ч", value = deltaText, accent = deltaColor)
    }

    // Selected point inspection strip
    val activePoint = selectedIndex?.let { idx -> points.getOrNull(idx) }
    if (activePoint != null) {
      val selVal = formatVal(activePoint.qnhHpa, activePoint.qnhInHg)
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("qnh_chart_selected_point")
          .background(palette.buttonActiveBackground, RoundedCornerShape(4.dp))
          .border(1.dp, chartAccent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Замер ${activePoint.observationTimeUtc} · QNH $selVal ${pressureUnit.label} (${activePoint.qnhHpa} гПа)",
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
          )
          if (activePoint.rawMetar.isNotBlank()) {
            Text(
              text = activePoint.rawMetar,
              color = palette.textSecondary,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              maxLines = 2,
            )
          }
        }
        Text(
          text = "Сброс",
          color = palette.metarAmber,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clickable { selectedIndex = null }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    }

    if (points.isEmpty()) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .height(if (compactMode) 110.dp else 140.dp)
          .testTag("qnh_line_chart_empty")
          .background(palette.fieldBackground, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(14.dp),
      ) {
        Text(
          text = "Исторические данные QNH появятся после загрузки сводок METAR или обновления 24ч архива.",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
      }
    } else {
      val chartHeight = if (compactMode) 148.dp else 192.dp
      val yValues = remember(points, pressureUnit) {
        points.map { pt ->
          if (pressureUnit == PressureUnit.HPA) pt.qnhHpa.toDouble() else pt.qnhInHg
        }
      }
      val (yMin, yMax) = remember(yValues, pressureUnit) {
        val rawMin = yValues.minOrNull() ?: if (pressureUnit == PressureUnit.HPA) 1008.0 else 29.76
        val rawMax = yValues.maxOrNull() ?: if (pressureUnit == PressureUnit.HPA) 1018.0 else 30.06
        if (pressureUnit == PressureUnit.HPA) {
          val span = max(4.0, rawMax - rawMin)
          val pad = max(2.0, ceil(span * 0.2))
          floor(rawMin - pad) to ceil(rawMax + pad)
        } else {
          val span = max(0.12, rawMax - rawMin)
          val pad = max(0.05, span * 0.2)
          (rawMin - pad) to (rawMax + pad)
        }
      }

      fun updateSelectionFromX(xPx: Float, leftPadPx: Float, rightPadPx: Float) {
        if (points.isEmpty()) return
        val plotW = canvasWidthPx - leftPadPx - rightPadPx
        if (plotW <= 1f) return
        if (points.size == 1) {
          selectedIndex = 0
          return
        }
        val minT = points.first().observedAtMillis
        val maxT = points.last().observedAtMillis
        val ratio = ((xPx - leftPadPx) / plotW).coerceIn(0f, 1f)
        val bestIdx = if (maxT > minT) {
          val targetT = minT + (ratio * (maxT - minT)).toLong()
          points.indices.minByOrNull { idx -> abs(points[idx].observedAtMillis - targetT) }
        } else {
          (ratio * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
        }
        selectedIndex = bestIdx
      }

      val accessibilitySummary = "График давления QNH для ${trend.icao}: минимум $minText, максимум $maxText, текущее $latestText ${pressureUnit.label}"

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.fieldBackground, RoundedCornerShape(4.dp))
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .padding(horizontal = 6.dp, vertical = 6.dp),
      ) {
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(chartHeight)
            .testTag("qnh_line_chart_canvas")
            .semantics { contentDescription = accessibilitySummary }
            .onSizeChanged { canvasWidthPx = it.width.toFloat() }
            .pointerInput(points) {
              val leftPadPx = 46.dp.toPx()
              val rightPadPx = 14.dp.toPx()
              detectTapGestures { offset ->
                updateSelectionFromX(offset.x, leftPadPx, rightPadPx)
              }
            }
            .pointerInput(points) {
              val leftPadPx = 46.dp.toPx()
              val rightPadPx = 14.dp.toPx()
              detectHorizontalDragGestures { change, _ ->
                change.consume()
                updateSelectionFromX(change.position.x, leftPadPx, rightPadPx)
              }
            },
        ) {
          if (size.width <= 1f || size.height <= 1f) return@Canvas

          val leftPad = 46.dp.toPx()
          val rightPad = 14.dp.toPx()
          val topPad = 12.dp.toPx()
          val bottomPad = 24.dp.toPx()
          val plotW = size.width - leftPad - rightPad
          val plotH = size.height - topPad - bottomPad
          if (plotW <= 20f || plotH <= 20f) return@Canvas

          val axisTextStyle = TextStyle(
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
          )

          // Horizontal calibrated gridlines (Min, Mid, Max)
          val ySpan = (yMax - yMin).coerceAtLeast(1e-4)
          val gridRatios = listOf(0f, 0.5f, 1f)
          gridRatios.forEach { r ->
            val yPos = topPad + plotH * (1f - r)
            drawLine(
              color = palette.border.copy(alpha = 0.45f),
              start = Offset(leftPad, yPos),
              end = Offset(leftPad + plotW, yPos),
              strokeWidth = 1.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val tickVal = yMin + ySpan * r
            val tickText = formatAxisNumeric(tickVal)
            val measured = textMeasurer.measure(tickText, style = axisTextStyle)
            drawText(
              textLayoutResult = measured,
              topLeft = Offset(
                x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                y = (yPos - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height),
              ),
            )
          }

          // Standard 1013.25 hPa / 29.92 inHg reference line when in range
          val isaVal = if (pressureUnit == PressureUnit.HPA) 1013.25 else 29.92
          if (isaVal in yMin..yMax) {
            val isaNorm = ((isaVal - yMin) / ySpan).toFloat()
            val isaY = topPad + plotH * (1f - isaNorm)
            drawLine(
              color = isaColor.copy(alpha = 0.65f),
              start = Offset(leftPad, isaY),
              end = Offset(leftPad + plotW, isaY),
              strokeWidth = 1.2.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
            )
            val isaTag = if (pressureUnit == PressureUnit.HPA) "ISA 1013" else "ISA 29.92"
            val isaMeasure = textMeasurer.measure(
              isaTag,
              style = axisTextStyle.copy(color = isaColor, fontWeight = FontWeight.Bold),
            )
            drawText(
              textLayoutResult = isaMeasure,
              topLeft = Offset(
                x = (leftPad + plotW - isaMeasure.size.width - 2.dp.toPx()).coerceAtLeast(leftPad),
                y = (isaY - isaMeasure.size.height - 2.dp.toPx()).coerceAtLeast(0f),
              ),
            )
          }

          // Compute point coordinates
          val minT = points.first().observedAtMillis
          val maxT = points.last().observedAtMillis
          val coords = points.mapIndexed { idx, _ ->
            val xNorm = when {
              points.size == 1 -> 0.5f
              maxT > minT -> ((points[idx].observedAtMillis - minT).toDouble() / (maxT - minT).toDouble()).toFloat()
              else -> idx.toFloat() / points.lastIndex.coerceAtLeast(1)
            }
            val yNorm = ((yValues[idx] - yMin) / ySpan).toFloat().coerceIn(0f, 1f)
            Offset(
              x = leftPad + xNorm * plotW,
              y = topPad + (1f - yNorm) * plotH,
            )
          }

          // Area fill + smooth line when >= 2 points
          if (coords.size >= 2) {
            val linePath = Path().apply {
              moveTo(coords.first().x, coords.first().y)
              for (i in 1 until coords.size) {
                val prev = coords[i - 1]
                val curr = coords[i]
                val midX = (prev.x + curr.x) / 2f
                cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
              }
            }
            val fillPath = Path().apply {
              addPath(linePath)
              lineTo(coords.last().x, topPad + plotH)
              lineTo(coords.first().x, topPad + plotH)
              close()
            }
            drawPath(
              path = fillPath,
              brush = Brush.verticalGradient(
                colors = listOf(
                  chartAccent.copy(alpha = 0.28f),
                  chartAccent.copy(alpha = 0.02f),
                ),
                startY = topPad,
                endY = topPad + plotH,
              ),
            )
            drawPath(
              path = linePath,
              color = chartAccent,
              style = Stroke(
                width = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
              ),
            )
          }

          // Draw vertical crosshair if user tapped/scrubbed a point
          val activeIdx = selectedIndex?.takeIf { it in coords.indices }
          if (activeIdx != null) {
            val pt = coords[activeIdx]
            drawLine(
              color = palette.textSecondary.copy(alpha = 0.65f),
              start = Offset(pt.x, topPad),
              end = Offset(pt.x, topPad + plotH),
              strokeWidth = 1.2.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
            )
          }

          // Draw data point nodes
          coords.forEachIndexed { idx, offset ->
            val isSelected = idx == activeIdx
            val isLatest = idx == coords.lastIndex
            val outerR = when {
              isSelected -> 6.dp.toPx()
              isLatest -> 4.5.dp.toPx()
              else -> 3.dp.toPx()
            }
            val dotColor = when {
              isSelected -> isaColor
              isLatest -> chartAccent
              else -> chartAccent
            }
            drawCircle(
              color = dotColor,
              radius = outerR,
              center = offset,
            )
            drawCircle(
              color = palette.fieldBackground,
              radius = outerR * 0.45f,
              center = offset,
            )
          }

          // X-axis UTC timestamps (first, mid, last)
          val xLabelsIndices = when {
            points.size == 1 -> listOf(0)
            points.size == 2 -> listOf(0, 1)
            else -> listOf(0, points.size / 2, points.lastIndex)
          }
          xLabelsIndices.distinct().forEach { idx ->
            val label = points[idx].observationTimeUtc.take(5)
            val measured = textMeasurer.measure(label, style = axisTextStyle)
            val desiredX = coords[idx].x - measured.size.width / 2f
            val clampedX = desiredX.coerceIn(leftPad, (leftPad + plotW - measured.size.width).coerceAtLeast(leftPad))
            drawText(
              textLayoutResult = measured,
              topLeft = Offset(
                x = clampedX,
                y = (topPad + plotH + 6.dp.toPx()).coerceAtMost(size.height - measured.size.height),
              ),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QnhStatPill(
  label: String,
  value: String,
  accent: Color,
) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
    modifier = Modifier
      .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
      .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 4.dp),
  ) {
    Text(
      text = label,
      color = palette.textMuted,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = value,
      color = accent,
      fontSize = 11.5.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.ExtraBold,
    )
  }
}
