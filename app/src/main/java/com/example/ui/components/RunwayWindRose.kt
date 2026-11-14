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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.RunwayCalculation
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun RunwayWindRose(
  calculation: RunwayCalculation,
  wind: WindData,
  windUnit: WindSpeedUnit,
  onToggleUnit: () -> Unit,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val textMeasurer = rememberTextMeasurer()

  val animatedHeading by animateFloatAsState(
    targetValue = calculation.headingDeg.toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "runwayHeading",
  )
  val animatedWindDir by animateFloatAsState(
    targetValue = (wind.directionDeg ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "windDir",
  )

  val crosswindVal = UnitConverter.convertWindFromKnots(calculation.crosswindKt, windUnit)
  val headwindVal = UnitConverter.convertWindFromKnots(calculation.headwindKt, windUnit)
  val speedMain = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)
  val gustMain = wind.gustKt?.let { UnitConverter.formatWindSpeedValue(it, windUnit) }

  val windDirHeader = when {
    wind.isCalm -> "Штиль"
    wind.isVariable -> "VRB"
    wind.directionDeg != null -> String.format(Locale.US, "%03d°", wind.directionDeg)
    else -> "---°"
  }
  val windSpeedFooter = when {
    wind.isCalm -> "0 ${windUnit.label}"
    gustMain != null -> "$speedMain G$gustMain ${windUnit.label}"
    else -> "$speedMain ${windUnit.label}"
  }

  val isTailwind = headwindVal < -0.049
  val headwindColor = when {
    isTailwind -> palette.tailwindNegative
    headwindVal > 0.05 -> palette.textPrimary
    else -> palette.textPrimary
  }

  val crosswindText = String.format(Locale.US, "%.1f", crosswindVal)
  val headwindText = if (isTailwind) {
    String.format(Locale.US, "-%.1f", kotlin.math.abs(headwindVal))
  } else {
    String.format(Locale.US, "%.1f", kotlin.math.abs(headwindVal))
  }
  val frictionText = calculation.frictionCoeff?.let { String.format(Locale.US, "%.2f", it) } ?: "--"

  Box(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (compactMode) {
          Modifier.aspectRatio(1.42f)
        } else {
          Modifier.aspectRatio(1.12f)
        },
      )
      .background(palette.panelSurface),
  ) {
    // Top-left Crosswind ("Бок.")
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(start = 6.dp, top = 4.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .widthIn(min = 48.dp)
          .background(palette.fieldBackground, RoundedCornerShape(3.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Text(
          text = crosswindText,
          color = palette.textPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Бок.",
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )
    }

    // Top-right Headwind / Tailwind ("Вст.")
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(end = 6.dp, top = 4.dp),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .widthIn(min = 48.dp)
          .background(palette.fieldBackground, RoundedCornerShape(3.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Text(
          text = headwindText,
          color = headwindColor,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
        )
      }
      Text(
        text = "Вст.",
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )
    }

    // Bottom-right Friction Coefficient ("КСц")
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 6.dp, bottom = 4.dp),
    ) {
      Text(
        text = "КСц",
        color = palette.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
      )
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .widthIn(min = 48.dp)
          .background(palette.fieldBackground, RoundedCornerShape(3.dp))
          .padding(horizontal = 6.dp, vertical = 2.dp),
      ) {
        Text(
          text = frictionText,
          color = palette.textPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    // Compass Dial + Runway + Arrows Canvas
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .padding(
          horizontal = if (compactMode) 10.dp else 16.dp,
          vertical = if (compactMode) 8.dp else 14.dp,
        ),
    ) {
      val cx = size.width / 2f
      val cy = size.height / 2f
      val radius = minOf(size.width, size.height) * 0.41f

      // Outer compass circle
      drawCircle(
        color = palette.compassStroke,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = 1.6.dp.toPx()),
      )

      // Compass ticks every 10 deg, major every 30 deg + numeric labels
      for (deg in 0 until 360 step 10) {
        val rad = (deg - 90.0) * PI / 180.0
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val isMajor = deg % 30 == 0
        val innerR = if (isMajor) radius - 5.dp.toPx() else radius - 2.5.dp.toPx()
        val outerR = if (isMajor) radius + 5.dp.toPx() else radius + 2.dp.toPx()

        drawLine(
          color = palette.compassStroke,
          start = Offset(cx + innerR * cosA, cy + innerR * sinA),
          end = Offset(cx + outerR * cosA, cy + outerR * sinA),
          strokeWidth = if (isMajor) 1.5.dp.toPx() else 0.9.dp.toPx(),
        )

        if (isMajor) {
          val label = if (deg == 0) "36" else String.format(Locale.US, "%02d", deg / 10)
          val measured = textMeasurer.measure(
            text = label,
            style = TextStyle(
              color = palette.textPrimary,
              fontSize = 10.sp,
              fontWeight = FontWeight.Medium,
            ),
          )
          val labelR = radius + 14.dp.toPx()
          val lx = cx + labelR * cosA - measured.size.width / 2f
          val ly = cy + labelR * sinA - measured.size.height / 2f
          drawText(measured, topLeft = Offset(lx, ly))
        }
      }

      // Variable wind sector arc if dddVddd is reported
      val varFrom = wind.variableFromDeg
      val varTo = wind.variableToDeg
      if (varFrom != null && varTo != null) {
        val sweep = ((varTo - varFrom + 360) % 360).toFloat()
        drawArc(
          color = palette.windArrow.copy(alpha = 0.20f),
          startAngle = varFrom - 90f,
          sweepAngle = sweep,
          useCenter = true,
          topLeft = Offset(cx - radius * 0.85f, cy - radius * 0.85f),
          size = Size(radius * 1.7f, radius * 1.7f),
        )
      }

      // Rotated Runway strip and dashed landing direction arrow
      rotate(degrees = animatedHeading, pivot = Offset(cx, cy)) {
        val rwyWidth = radius * 0.25f
        val rwyLength = radius * 1.62f
        val left = cx - rwyWidth / 2f
        val top = cy - rwyLength / 2f

        drawRect(
          color = palette.runwayFill,
          topLeft = Offset(left, top),
          size = Size(rwyWidth, rwyLength),
        )
        drawRect(
          color = palette.runwayStroke,
          topLeft = Offset(left, top),
          size = Size(rwyWidth, rwyLength),
          style = Stroke(width = 1.4.dp.toPx()),
        )

        // Dashed landing arrow inside the approach end (lower half in local rotated space, pointing UP toward heading)
        val arrowStartY = cy + rwyLength * 0.43f
        val arrowEndY = cy + rwyLength * 0.16f
        drawLine(
          color = palette.textPrimary,
          start = Offset(cx, arrowStartY),
          end = Offset(cx, arrowEndY + 5.dp.toPx()),
          strokeWidth = 2.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f),
        )
        val rwyHead = Path().apply {
          moveTo(cx, arrowEndY)
          lineTo(cx - 4.5.dp.toPx(), arrowEndY + 7.dp.toPx())
          lineTo(cx + 4.5.dp.toPx(), arrowEndY + 7.dp.toPx())
          close()
        }
        drawPath(rwyHead, color = palette.textPrimary)
      }

      // Orange wind direction arrow pointing INWARD from wind origin direction
      if (!wind.isCalm && !wind.isVariable && wind.directionDeg != null) {
        rotate(degrees = animatedWindDir, pivot = Offset(cx, cy)) {
          val outerTipY = cy - radius * 0.87f
          val innerTipY = cy - radius * 0.57f
          val shaftEndY = innerTipY + 10.dp.toPx()

          drawLine(
            color = palette.windArrow,
            start = Offset(cx, outerTipY),
            end = Offset(cx, innerTipY - 8.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Butt,
          )

          val head = Path().apply {
            moveTo(cx, innerTipY)
            lineTo(cx - 7.dp.toPx(), innerTipY - 11.dp.toPx())
            lineTo(cx + 7.dp.toPx(), innerTipY - 11.dp.toPx())
            close()
          }
          drawPath(head, color = palette.windArrow)
        }
      } else if (wind.isVariable) {
        drawCircle(
          color = palette.windArrow,
          radius = radius * 0.72f,
          center = Offset(cx, cy),
          style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
          ),
        )
      }
    }

    // Center Wind Readout Overlay Box: Top = Wind Direction (080°), Bottom = Wind Speed in knots (4 kt)
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier
        .align(Alignment.Center)
        .testTag("wind_center_box_${calculation.designator}")
        .clip(RoundedCornerShape(3.dp))
        .clickable(onClick = onToggleUnit),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .widthIn(min = if (compactMode) 68.dp else 78.dp)
          .background(palette.fieldBackground)
          .border(1.2.dp, palette.borderStrong),
      ) {
        Text(
          text = windDirHeader,
          color = palette.metarAmber,
          fontSize = if (compactMode) 15.sp else 18.sp,
          fontWeight = FontWeight.ExtraBold,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(
            top = if (compactMode) 2.dp else 3.dp,
            bottom = if (compactMode) 1.dp else 2.dp,
            start = 6.dp,
            end = 6.dp,
          ),
        )
        androidx.compose.material3.HorizontalDivider(
          thickness = 1.dp,
          color = palette.borderStrong,
          modifier = Modifier.widthIn(min = if (compactMode) 68.dp else 78.dp, max = 96.dp),
        )
        Text(
          text = windSpeedFooter,
          color = if (gustMain != null) palette.metarAmber else palette.textPrimary,
          fontSize = if (compactMode) 14.sp else 17.sp,
          fontWeight = FontWeight.ExtraBold,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(
            top = if (compactMode) 1.dp else 2.dp,
            bottom = if (compactMode) 2.dp else 3.dp,
            start = 6.dp,
            end = 6.dp,
          ),
        )
      }
    }
  }
}
