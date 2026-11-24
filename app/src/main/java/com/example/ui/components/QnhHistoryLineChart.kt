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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.PressureUnit
import com.example.model.QnhTrendSummary
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

enum class HistoryChartMetric(val label: String) {
  PRESSURE("Давление QNH"),
  TEMPERATURE("Температура T / DP"),
}

private data class ChartSample(
  val observationTimeUtc: String,
  val observedAtMillis: Long,
  val primaryValue: Double,
  val secondaryValue: Double?,
  val rawMetar: String,
  val rawHpa: Int?,
  val rawTempC: Int?,
  val rawDewC: Int?,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QnhHistoryLineChart(
  trend: QnhTrendSummary,
  pressureUnit: PressureUnit,
  onTogglePressureUnit: (() -> Unit)? = null,
  compactMode: Boolean = false,
  initialMetric: HistoryChartMetric = HistoryChartMetric.PRESSURE,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val textMeasurer = rememberTextMeasurer()
  var activeMetric by remember(initialMetric) { mutableStateOf(initialMetric) }

  val useImperial = pressureUnit == PressureUnit.INHG
  val pressureAccent = MaterialTheme.colorScheme.primary
  val tempAccent = palette.metarAmber
  val dewPointColor = Color(0xFF0284C7)

  fun cToDisplay(celsius: Int): Double = if (useImperial) celsius * 9.0 / 5.0 + 32.0 else celsius.toDouble()

  val samples = remember(trend, activeMetric, pressureUnit) {
    when (activeMetric) {
      HistoryChartMetric.PRESSURE -> trend.points.map { pt ->
        ChartSample(
          observationTimeUtc = pt.observationTimeUtc,
          observedAtMillis = pt.observedAtMillis,
          primaryValue = if (useImperial) pt.qnhInHg else pt.qnhHpa.toDouble(),
          secondaryValue = null,
          rawMetar = pt.rawMetar,
          rawHpa = pt.qnhHpa,
          rawTempC = null,
          rawDewC = null,
        )
      }
      HistoryChartMetric.TEMPERATURE -> trend.temperaturePoints.map { pt ->
        ChartSample(
          observationTimeUtc = pt.observationTimeUtc,
          observedAtMillis = pt.observedAtMillis,
          primaryValue = cToDisplay(pt.temperatureC),
          secondaryValue = pt.dewPointC?.let { cToDisplay(it) },
          rawMetar = pt.rawMetar,
          rawHpa = null,
          rawTempC = pt.temperatureC,
          rawDewC = pt.dewPointC,
        )
      }
    }
  }

  var selectedIndex by remember(samples, activeMetric) { mutableStateOf<Int?>(null) }
  var canvasWidthPx by remember { mutableStateOf(0f) }

  val unitSuffix = when (activeMetric) {
    HistoryChartMetric.PRESSURE -> pressureUnit.label
    HistoryChartMetric.TEMPERATURE -> if (useImperial) "°F" else "°C"
  }

  fun formatDisplayValue(rawValue: Double?): String {
    if (rawValue == null) return "--"
    return when (activeMetric) {
      HistoryChartMetric.PRESSURE -> if (useImperial) {
        String.format(Locale.US, "%.2f", rawValue)
      } else {
        rawValue.roundToInt().toString()
      }
      HistoryChartMetric.TEMPERATURE -> rawValue.roundToInt().toString()
    }
  }

  val minStatText = when (activeMetric) {
    HistoryChartMetric.PRESSURE -> {
      val v = trend.minHpa?.let { if (useImperial) UnitConverter.convertHpaToInHg(it) else it.toDouble() }
      formatDisplayValue(v)
    }
    HistoryChartMetric.TEMPERATURE -> {
      val v = trend.minTempC?.let { cToDisplay(it) }
      formatDisplayValue(v)
    }
  }
  val maxStatText = when (activeMetric) {
    HistoryChartMetric.PRESSURE -> {
      val v = trend.maxHpa?.let { if (useImperial) UnitConverter.convertHpaToInHg(it) else it.toDouble() }
      formatDisplayValue(v)
    }
    HistoryChartMetric.TEMPERATURE -> {
      val v = trend.maxTempC?.let { cToDisplay(it) }
      formatDisplayValue(v)
    }
  }
  val latestStatText = formatDisplayValue(samples.lastOrNull()?.primaryValue)

  val (deltaText, deltaValueNum) = remember(samples, trend, activeMetric, useImperial) {
    if (samples.size < 2) {
      "—" to 0.0
    } else if (activeMetric == HistoryChartMetric.PRESSURE) {
      val dHpa = trend.delta24hHpa ?: 0
      if (!useImperial) {
        val sign = if (dHpa > 0) "+" else ""
        "$sign$dHpa гПа" to dHpa.toDouble()
      } else {
        val dInHg = samples.last().primaryValue - samples.first().primaryValue
        val sign = if (dInHg > 0.004) "+" else ""
        "$sign${String.format(Locale.US, "%.2f", dInHg)} дм" to dHpa.toDouble()
      }
    } else {
      val dC = trend.delta24hTempC ?: 0
      val dDisp = if (useImperial) (samples.last().primaryValue - samples.first().primaryValue).roundToInt() else dC
      val sign = if (dDisp > 0) "+" else ""
      "$sign$dDisp $unitSuffix" to dDisp.toDouble()
    }
  }

  val deltaColor = when {
    deltaValueNum > 1.0 -> palette.headwindPositive
    deltaValueNum < -1.0 -> palette.tailwindNegative
    else -> palette.textSecondary
  }

  val lineColor = if (activeMetric == HistoryChartMetric.PRESSURE) pressureAccent else tempAccent
  val referenceValue = when (activeMetric) {
    HistoryChartMetric.PRESSURE -> if (useImperial) 29.92 else 1013.25
    HistoryChartMetric.TEMPERATURE -> if (useImperial) 32.0 else 0.0
  }
  val referenceTag = when (activeMetric) {
    HistoryChartMetric.PRESSURE -> if (useImperial) "ISA 29.92" else "ISA 1013"
    HistoryChartMetric.TEMPERATURE -> if (useImperial) "0°C / 32°F" else "0 °C"
  }

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
          imageVector = if (activeMetric == HistoryChartMetric.PRESSURE) Icons.AutoMirrored.Filled.ShowChart else Icons.Filled.Thermostat,
          contentDescription = if (activeMetric == HistoryChartMetric.PRESSURE) {
            "График давления QNH за 24 часа"
          } else {
            "График температуры и точки росы за 24 часа"
          },
          tint = palette.metarAmber,
          modifier = Modifier.size(18.dp),
        )
        Column {
          Text(
            text = if (activeMetric == HistoryChartMetric.PRESSURE) {
              "ТРЕНД QNH ЗА 24 ЧАСА · ${trend.icao}"
            } else {
              "ТРЕНД ТЕМПЕРАТУРЫ (T/DP) 24Ч · ${trend.icao}"
            },
            color = palette.textPrimary,
            fontSize = if (compactMode) 11.5.sp else 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
          )
          Text(
            text = if (samples.isEmpty()) {
              "Нет архивных замеров"
            } else if (activeMetric == HistoryChartMetric.PRESSURE) {
              "Точек: ${samples.size} · стандарт ISA ${if (useImperial) "29.92 дм" else "1013 гПа"}"
            } else {
              "Точек: ${samples.size} · T (янтарь) и точка росы DP (синий)"
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
            text = unitSuffix,
            color = palette.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    // Dynamic Metric Switcher: Pressure QNH vs Temperature T / DP
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      HistoryChartMetric.entries.forEach { metric ->
        val selected = metric == activeMetric
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("chart_metric_${metric.name}")
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
            .border(
              width = if (selected) 1.5.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else palette.border,
              shape = RoundedCornerShape(4.dp),
            )
            .clickable { activeMetric = metric }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
          Icon(
            imageVector = if (metric == HistoryChartMetric.PRESSURE) Icons.Filled.Compress else Icons.Filled.Thermostat,
            contentDescription = null,
            tint = if (selected) palette.metarAmber else palette.textMuted,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = metric.label,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
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
      QnhStatPill(label = "MIN", value = "$minStatText $unitSuffix", accent = palette.textPrimary)
      QnhStatPill(label = "MAX", value = "$maxStatText $unitSuffix", accent = palette.textPrimary)
      QnhStatPill(label = "ТЕК.", value = "$latestStatText $unitSuffix", accent = palette.metarAmber)
      QnhStatPill(label = "Δ 24Ч", value = deltaText, accent = deltaColor)
    }

    // Legend row when Temperature + Dew Point mode is active
    if (activeMetric == HistoryChartMetric.TEMPERATURE && samples.isNotEmpty()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("chart_legend_row"),
      ) {
        LegendDot(color = tempAccent, label = "Температура (T)")
        LegendDot(color = dewPointColor, label = "Точка росы (DP)")
      }
    }

    // Selected point inspection strip
    val activeSample = selectedIndex?.let { idx -> samples.getOrNull(idx) }
    if (activeSample != null) {
      val headline = when (activeMetric) {
        HistoryChartMetric.PRESSURE -> {
          val selVal = formatDisplayValue(activeSample.primaryValue)
          "Замер ${activeSample.observationTimeUtc} · QNH $selVal $unitSuffix (${activeSample.rawHpa} гПа)"
        }
        HistoryChartMetric.TEMPERATURE -> {
          val tDisp = formatDisplayValue(activeSample.primaryValue)
          val dpDisp = activeSample.secondaryValue?.let { formatDisplayValue(it) } ?: "--"
          val spread = if (activeSample.rawTempC != null && activeSample.rawDewC != null) {
            " · дефицит ${activeSample.rawTempC - activeSample.rawDewC}°C"
          } else {
            ""
          }
          "Замер ${activeSample.observationTimeUtc} · T $tDisp$unitSuffix / DP $dpDisp$unitSuffix$spread"
        }
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("qnh_chart_selected_point")
          .background(palette.buttonActiveBackground, RoundedCornerShape(4.dp))
          .border(1.dp, lineColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = headline,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
          )
          if (activeSample.rawMetar.isNotBlank()) {
            Text(
              text = activeSample.rawMetar,
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

    if (samples.isEmpty()) {
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
          text = if (activeMetric == HistoryChartMetric.PRESSURE) {
            "Исторические данные QNH появятся после загрузки сводок METAR или обновления 24ч архива."
          } else {
            "Исторические данные температуры появятся после загрузки сводок METAR или обновления 24ч архива."
          },
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
      }
    } else {
      val chartHeight = if (compactMode) 152.dp else 196.dp
      val allSeriesValues = remember(samples) {
        buildList {
          samples.forEach { s ->
            add(s.primaryValue)
            if (s.secondaryValue != null) add(s.secondaryValue)
          }
        }
      }
      val (yMin, yMax) = remember(allSeriesValues, activeMetric, useImperial) {
        val rawMin = allSeriesValues.minOrNull() ?: 0.0
        val rawMax = allSeriesValues.maxOrNull() ?: 10.0
        when {
          activeMetric == HistoryChartMetric.PRESSURE && !useImperial -> {
            val span = max(4.0, rawMax - rawMin)
            val pad = max(2.0, ceil(span * 0.2))
            floor(rawMin - pad) to ceil(rawMax + pad)
          }
          activeMetric == HistoryChartMetric.PRESSURE && useImperial -> {
            val span = max(0.12, rawMax - rawMin)
            val pad = max(0.05, span * 0.2)
            (rawMin - pad) to (rawMax + pad)
          }
          else -> {
            val span = max(4.0, rawMax - rawMin)
            val pad = max(2.0, ceil(span * 0.2))
            floor(rawMin - pad) to ceil(rawMax + pad)
          }
        }
      }

      fun updateSelectionFromX(xPx: Float, leftPadPx: Float, rightPadPx: Float) {
        if (samples.isEmpty()) return
        val plotW = canvasWidthPx - leftPadPx - rightPadPx
        if (plotW <= 1f) return
        if (samples.size == 1) {
          selectedIndex = 0
          return
        }
        val minT = samples.first().observedAtMillis
        val maxT = samples.last().observedAtMillis
        val ratio = ((xPx - leftPadPx) / plotW).coerceIn(0f, 1f)
        val bestIdx = if (maxT > minT) {
          val targetT = minT + (ratio * (maxT - minT)).toLong()
          samples.indices.minByOrNull { idx -> abs(samples[idx].observedAtMillis - targetT) }
        } else {
          (ratio * (samples.size - 1)).roundToInt().coerceIn(0, samples.lastIndex)
        }
        selectedIndex = bestIdx
      }

      val accessibilitySummary = if (activeMetric == HistoryChartMetric.PRESSURE) {
        "График давления QNH для ${trend.icao}: минимум $minStatText, максимум $maxStatText, текущее $latestStatText $unitSuffix"
      } else {
        "График температуры для ${trend.icao}: минимум $minStatText, максимум $maxStatText, текущее $latestStatText $unitSuffix"
      }

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
            .pointerInput(samples) {
              val leftPadPx = 46.dp.toPx()
              val rightPadPx = 14.dp.toPx()
              detectTapGestures { offset ->
                updateSelectionFromX(offset.x, leftPadPx, rightPadPx)
              }
            }
            .pointerInput(samples) {
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
          listOf(0f, 0.5f, 1f).forEach { r ->
            val yPos = topPad + plotH * (1f - r)
            drawLine(
              color = palette.border.copy(alpha = 0.45f),
              start = Offset(leftPad, yPos),
              end = Offset(leftPad + plotW, yPos),
              strokeWidth = 1.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val tickVal = yMin + ySpan * r
            val tickText = formatDisplayValue(tickVal)
            val measured = textMeasurer.measure(tickText, style = axisTextStyle)
            drawText(
              textLayoutResult = measured,
              topLeft = Offset(
                x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                y = (yPos - measured.size.height / 2f).coerceIn(0f, size.height - measured.size.height),
              ),
            )
          }

          // Reference line (ISA 1013/29.92 or Freezing 0°C/32°F) when in range
          if (referenceValue in yMin..yMax) {
            val refNorm = ((referenceValue - yMin) / ySpan).toFloat()
            val refY = topPad + plotH * (1f - refNorm)
            drawLine(
              color = palette.metarAmber.copy(alpha = 0.55f),
              start = Offset(leftPad, refY),
              end = Offset(leftPad + plotW, refY),
              strokeWidth = 1.2.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f)),
            )
            val refMeasure = textMeasurer.measure(
              referenceTag,
              style = axisTextStyle.copy(color = palette.metarAmber, fontWeight = FontWeight.Bold),
            )
            drawText(
              textLayoutResult = refMeasure,
              topLeft = Offset(
                x = (leftPad + plotW - refMeasure.size.width - 2.dp.toPx()).coerceAtLeast(leftPad),
                y = (refY - refMeasure.size.height - 2.dp.toPx()).coerceAtLeast(0f),
              ),
            )
          }

          val minT = samples.first().observedAtMillis
          val maxT = samples.last().observedAtMillis
          fun xForIndex(idx: Int): Float {
            val xNorm = when {
              samples.size == 1 -> 0.5f
              maxT > minT -> ((samples[idx].observedAtMillis - minT).toDouble() / (maxT - minT).toDouble()).toFloat()
              else -> idx.toFloat() / samples.lastIndex.coerceAtLeast(1)
            }
            return leftPad + xNorm * plotW
          }
          fun yForValue(v: Double): Float {
            val yNorm = ((v - yMin) / ySpan).toFloat().coerceIn(0f, 1f)
            return topPad + (1f - yNorm) * plotH
          }

          val primaryCoords = samples.mapIndexed { idx, s ->
            Offset(xForIndex(idx), yForValue(s.primaryValue))
          }

          // Optional secondary Dew Point series (Temperature mode)
          val secondaryCoords = samples.mapIndexedNotNull { idx, s ->
            s.secondaryValue?.let { Offset(xForIndex(idx), yForValue(it)) }
          }

          if (primaryCoords.size >= 2) {
            val linePath = buildSmoothPath(primaryCoords)
            val fillPath = Path().apply {
              addPath(linePath)
              lineTo(primaryCoords.last().x, topPad + plotH)
              lineTo(primaryCoords.first().x, topPad + plotH)
              close()
            }
            drawPath(
              path = fillPath,
              brush = Brush.verticalGradient(
                colors = listOf(
                  lineColor.copy(alpha = 0.28f),
                  lineColor.copy(alpha = 0.02f),
                ),
                startY = topPad,
                endY = topPad + plotH,
              ),
            )
            drawPath(
              path = linePath,
              color = lineColor,
              style = Stroke(
                width = 2.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
              ),
            )
          }

          if (secondaryCoords.size >= 2) {
            val dpPath = buildSmoothPath(secondaryCoords)
            drawPath(
              path = dpPath,
              color = dewPointColor,
              style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 5f)),
              ),
            )
          }
          secondaryCoords.forEach { offset ->
            drawCircle(color = dewPointColor, radius = 2.8.dp.toPx(), center = offset)
          }

          // Vertical scrub crosshair
          val activeIdx = selectedIndex?.takeIf { it in primaryCoords.indices }
          if (activeIdx != null) {
            val pt = primaryCoords[activeIdx]
            drawLine(
              color = palette.textSecondary.copy(alpha = 0.65f),
              start = Offset(pt.x, topPad),
              end = Offset(pt.x, topPad + plotH),
              strokeWidth = 1.2.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)),
            )
          }

          primaryCoords.forEachIndexed { idx, offset ->
            val isSelected = idx == activeIdx
            val isLatest = idx == primaryCoords.lastIndex
            val outerR = when {
              isSelected -> 6.dp.toPx()
              isLatest -> 4.5.dp.toPx()
              else -> 3.dp.toPx()
            }
            drawCircle(
              color = if (isSelected) palette.metarAmber else lineColor,
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
            samples.size == 1 -> listOf(0)
            samples.size == 2 -> listOf(0, 1)
            else -> listOf(0, samples.size / 2, samples.lastIndex)
          }
          xLabelsIndices.distinct().forEach { idx ->
            val label = samples[idx].observationTimeUtc.take(5)
            val measured = textMeasurer.measure(label, style = axisTextStyle)
            val desiredX = primaryCoords[idx].x - measured.size.width / 2f
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

private fun buildSmoothPath(coords: List<Offset>): Path {
  return Path().apply {
    moveTo(coords.first().x, coords.first().y)
    for (i in 1 until coords.size) {
      val prev = coords[i - 1]
      val curr = coords[i]
      val midX = (prev.x + curr.x) / 2f
      cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
    }
  }
}

@Composable
private fun LegendDot(
  color: Color,
  label: String,
) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Box(
      modifier = Modifier
        .size(9.dp)
        .background(color, RoundedCornerShape(50)),
    )
    Text(
      text = label,
      color = palette.textSecondary,
      fontSize = 10.5.sp,
      fontWeight = FontWeight.Medium,
    )
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
      .semantics(mergeDescendants = true) {}
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
