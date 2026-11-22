package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendDirection
import com.example.model.WindTrendSummary
import com.example.ui.theme.LocalMeteoPalette
import kotlin.math.abs

@Composable
fun WindTrendIndicator(
  trend: WindTrendSummary,
  windUnit: WindSpeedUnit,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val accentColor = when (trend.direction) {
    WindTrendDirection.INCREASING -> palette.tailwindNegative
    WindTrendDirection.DECREASING -> palette.headwindPositive
    WindTrendDirection.STEADY -> palette.metarAmber
    WindTrendDirection.INSUFFICIENT -> palette.textMuted
  }

  val deltaPrefix = when {
    trend.deltaKt > 0 -> "+"
    trend.deltaKt < 0 -> "-"
    else -> ""
  }
  val convertedDelta = UnitConverter.formatWindSpeedValue(abs(trend.deltaKt), windUnit)
  val badgeText = when (trend.direction) {
    WindTrendDirection.INSUFFICIENT -> "${trend.direction.arrow} ${trend.direction.label}"
    WindTrendDirection.STEADY -> "${trend.direction.arrow} ${trend.direction.label}"
    else -> "${trend.direction.arrow} ${trend.direction.label} $deltaPrefix$convertedDelta ${windUnit.label}"
  }

  val minLabel = UnitConverter.formatWindSpeedValue(trend.minSpeedKt, windUnit)
  val maxLabel = UnitConverter.formatWindSpeedValue(trend.maxSpeedKt, windUnit)
  val rangeText = when {
    trend.entries.isEmpty() -> "Ожидание METAR"
    trend.entries.size == 1 -> "1 замер (${trend.entries.first().observationTimeUtc})"
    else -> "$minLabel–$maxLabel ${windUnit.label} · ${trend.entries.size} зам."
  }

  val accessibilitySummary = "${trend.icao}: тренд ветра — $badgeText ($rangeText)"

  Column(
    verticalArrangement = Arrangement.spacedBy(if (compactMode) 2.dp else 4.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("wind_trend_indicator")
      .semantics { contentDescription = accessibilitySummary }
      .background(palette.fieldBackground, RoundedCornerShape(3.dp))
      .border(1.dp, palette.border, RoundedCornerShape(3.dp))
      .padding(horizontal = if (compactMode) 6.dp else 8.dp, vertical = if (compactMode) 4.dp else 6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "Тренд ветра",
        color = palette.textSecondary,
        fontSize = if (compactMode) 10.sp else 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Box(
        modifier = Modifier
          .testTag("wind_trend_badge")
          .semantics(mergeDescendants = true) {}
          .clip(RoundedCornerShape(3.dp))
          .background(accentColor.copy(alpha = 0.14f))
          .border(0.8.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
          .padding(horizontal = 6.dp, vertical = 1.5.dp),
      ) {
        Text(
          text = badgeText,
          color = accentColor,
          fontSize = if (compactMode) 10.sp else 11.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          softWrap = false,
        )
      }
    }

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(if (compactMode) 24.dp else 32.dp)
        .testTag("wind_trend_sparkline"),
    ) {
      if (size.width <= 1f || size.height <= 1f) return@Canvas

      val padX = 4.dp.toPx()
      val padY = 4.dp.toPx()
      val plotW = (size.width - padX * 2f).coerceAtLeast(1f)
      val plotH = (size.height - padY * 2f).coerceAtLeast(1f)

      // Subtle horizontal midpoint guide
      val midY = padY + plotH * 0.5f
      drawLine(
        color = palette.border.copy(alpha = 0.45f),
        start = Offset(padX, midY),
        end = Offset(size.width - padX, midY),
        strokeWidth = 0.8.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
      )

      val pointsData = trend.entries
      if (pointsData.isEmpty()) return@Canvas

      val minVal = pointsData.minOf { it.speedKt }.toFloat()
      val maxVal = pointsData.maxOf { maxOf(it.speedKt, it.gustKt ?: it.speedKt) }.toFloat()
      val span = (maxVal - minVal).coerceAtLeast(2f)

      val coords = if (pointsData.size == 1) {
        val y = padY + plotH * 0.5f
        listOf(Offset(padX, y), Offset(size.width - padX, y))
      } else {
        pointsData.mapIndexed { idx, entry ->
          val x = padX + (idx.toFloat() / (pointsData.size - 1).toFloat()) * plotW
          val norm = ((entry.speedKt - minVal) / span).coerceIn(0f, 1f)
          val y = padY + plotH * (1f - norm)
          Offset(x, y)
        }
      }

      val linePath = Path().apply {
        coords.forEachIndexed { idx, pt ->
          if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
        }
      }
      val fillPath = Path().apply {
        addPath(linePath)
        lineTo(coords.last().x, padY + plotH)
        lineTo(coords.first().x, padY + plotH)
        close()
      }

      drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
          colors = listOf(
            accentColor.copy(alpha = 0.28f),
            Color.Transparent,
          ),
          startY = padY,
          endY = padY + plotH,
        ),
      )

      drawPath(
        path = linePath,
        color = accentColor,
        style = Stroke(
          width = 2.dp.toPx(),
          cap = StrokeCap.Round,
          join = StrokeJoin.Round,
        ),
      )

      if (pointsData.size >= 2) {
        coords.forEachIndexed { idx, pt ->
          val isLast = idx == coords.lastIndex
          drawCircle(
            color = if (isLast) accentColor else palette.panelSurface,
            radius = if (isLast) 3.4.dp.toPx() else 2.2.dp.toPx(),
            center = pt,
          )
          if (!isLast) {
            drawCircle(
              color = accentColor,
              radius = 2.2.dp.toPx(),
              center = pt,
              style = Stroke(width = 1.1.dp.toPx()),
            )
          }
        }
      } else {
        drawCircle(
          color = accentColor,
          radius = 3.dp.toPx(),
          center = coords.last(),
        )
      }
    }

    Text(
      text = rangeText,
      color = palette.textMuted,
      fontSize = if (compactMode) 9.5.sp else 10.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
