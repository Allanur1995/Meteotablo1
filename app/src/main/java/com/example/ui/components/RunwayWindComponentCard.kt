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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class RunwayWindComponents(
  val runwayHeadingDeg: Int,
  val headwindKt: Double,
  val crosswindKt: Double,
  val signedCrosswindKt: Double,
  val gustHeadwindKt: Double?,
  val gustCrosswindKt: Double?,
  val relativeAngleDeg: Int?,
  val isVariable: Boolean,
  val isCalm: Boolean,
) {
  val isTailwind: Boolean get() = headwindKt < -0.05
  val tailwindKt: Double get() = if (headwindKt < 0.0) abs(headwindKt) else 0.0
}

fun normalizeRunwayHeading(headingDeg: Int): Int {
  val mod = ((headingDeg % 360) + 360) % 360
  return if (mod == 0) 360 else mod
}

fun calculateRunwayWindComponents(
  runwayHeadingDeg: Int,
  wind: WindData,
): RunwayWindComponents {
  val normalizedHdg = normalizeRunwayHeading(runwayHeadingDeg)
  if (wind.isCalm || wind.speedKt == 0) {
    return RunwayWindComponents(
      runwayHeadingDeg = normalizedHdg,
      headwindKt = 0.0,
      crosswindKt = 0.0,
      signedCrosswindKt = 0.0,
      gustHeadwindKt = null,
      gustCrosswindKt = null,
      relativeAngleDeg = 0,
      isVariable = false,
      isCalm = true,
    )
  }

  val windDir = wind.directionDeg
  if (wind.isVariable || windDir == null) {
    return RunwayWindComponents(
      runwayHeadingDeg = normalizedHdg,
      headwindKt = 0.0,
      crosswindKt = wind.speedKt.toDouble(),
      signedCrosswindKt = wind.speedKt.toDouble(),
      gustHeadwindKt = null,
      gustCrosswindKt = wind.gustKt?.toDouble(),
      relativeAngleDeg = null,
      isVariable = true,
      isCalm = false,
    )
  }

  val signedDelta = (((windDir - normalizedHdg) % 360) + 540) % 360 - 180
  val diffRad = signedDelta * PI / 180.0
  val cosA = cos(diffRad)
  val sinA = sin(diffRad)

  val headKt = wind.speedKt * cosA
  val signedCrossKt = wind.speedKt * sinA
  val gustHeadKt = wind.gustKt?.let { it * cosA }
  val gustCrossKt = wind.gustKt?.let { abs(it * sinA) }

  return RunwayWindComponents(
    runwayHeadingDeg = normalizedHdg,
    headwindKt = headKt,
    crosswindKt = abs(signedCrossKt),
    signedCrosswindKt = signedCrossKt,
    gustHeadwindKt = gustHeadKt,
    gustCrosswindKt = gustCrossKt,
    relativeAngleDeg = signedDelta,
    isVariable = false,
    isCalm = false,
  )
}

/**
 * Graphical Canvas visualizer showing runway orientation relative to the live wind vector,
 * orthogonal headwind/tailwind & crosswind decomposition vectors, and aircraft crosswind
 * threshold warnings.
 */
@Composable
fun RunwayOrientationWindVisualizer(
  runwayHeadingDeg: Int,
  wind: WindData,
  windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  runwayDesignator: String? = null,
  maxCrosswindKt: Int? = null,
  aircraftLabel: String? = null,
  onToggleUnit: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val textMeasurer = rememberTextMeasurer()
  val components = remember(runwayHeadingDeg, wind) {
    calculateRunwayWindComponents(runwayHeadingDeg = runwayHeadingDeg, wind = wind)
  }

  val animatedHeading by animateFloatAsState(
    targetValue = components.runwayHeadingDeg.toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "orientationRwyHeading",
  )
  val animatedWindDir by animateFloatAsState(
    targetValue = (wind.directionDeg ?: 0).toFloat(),
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "orientationWindDir",
  )

  val limitExceeded = maxCrosswindKt != null && (
    components.crosswindKt > maxCrosswindKt + 0.05 ||
      (components.gustCrosswindKt != null && components.gustCrosswindKt > maxCrosswindKt + 0.05)
    )

  val crossValDisp = UnitConverter.convertWindFromKnots(components.crosswindKt, windUnit)
  val limitValDisp = maxCrosswindKt?.let { UnitConverter.convertWindFromKnots(it.toDouble(), windUnit) }
  val headingText = runwayDesignator?.let {
    String.format(Locale.US, "ВПП %s (%03d°)", it, components.runwayHeadingDeg)
  } ?: String.format(Locale.US, "Курс ВПП %03d°", components.runwayHeadingDeg)

  val relAngleText = when {
    components.isCalm -> "Штиль"
    components.isVariable -> "Переменный (VRB)"
    components.relativeAngleDeg != null -> {
      val deg = components.relativeAngleDeg
      when {
        deg > 0 -> "Ветер справа +${deg}°"
        deg < 0 -> "Ветер слева ${deg}°"
        else -> "Строго встречный 0°"
      }
    }
    else -> "--"
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("runway_orientation_visualizer")
      .background(palette.fieldBackground, RoundedCornerShape(4.dp))
      .border(
        width = if (limitExceeded) 1.5.dp else 1.dp,
        color = if (limitExceeded) palette.tailwindNegative else palette.borderStrong,
        shape = RoundedCornerShape(4.dp),
      )
      .padding(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "ОРИЕНТАЦИЯ ВПП И ВЕТЕР · $headingText",
          color = palette.textPrimary,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(
          text = buildString {
            append(relAngleText)
            if (maxCrosswindKt != null && limitValDisp != null) {
              val ac = aircraftLabel?.takeIf { it.isNotBlank() } ?: "ВС"
              append(String.format(Locale.US, " · Лимит %s: %.0f %s", ac, limitValDisp, windUnit.label))
            }
          },
          color = if (limitExceeded) palette.tailwindNegative else palette.textSecondary,
          fontSize = 10.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = if (limitExceeded) FontWeight.Bold else FontWeight.Medium,
        )
      }
      if (onToggleUnit != null) {
        Text(
          text = windUnit.label,
          color = palette.metarAmber,
          fontSize = 10.5.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(palette.secondaryFieldBackground)
            .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
            .clickable(onClick = onToggleUnit)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        )
      }
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 172.dp, max = 220.dp)
        .aspectRatio(1.45f, matchHeightConstraintsFirst = true)
        .clip(RoundedCornerShape(4.dp))
        .background(palette.panelSurface)
        .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
        .semantics {
          contentDescription = "$headingText, $relAngleText, боковой ветер ${String.format(Locale.US, "%.1f", crossValDisp)} ${windUnit.label}"
        },
    ) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .padding(10.dp)
          .testTag("runway_orientation_canvas"),
      ) {
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = (minOf(size.width, size.height) * 0.42f).coerceAtLeast(1f)

        // Compass backdrop & radial rings
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(palette.secondaryFieldBackground, palette.panelSurface),
            center = Offset(cx, cy),
            radius = radius,
          ),
          radius = radius,
          center = Offset(cx, cy),
        )
        drawCircle(
          color = if (limitExceeded) palette.tailwindNegative.copy(alpha = 0.85f) else palette.compassStroke,
          radius = radius,
          center = Offset(cx, cy),
          style = Stroke(width = 1.5.dp.toPx()),
        )

        // Draw crosswind limit ring scaled against 45 kt
        if (maxCrosswindKt != null) {
          val limitRatio = (maxCrosswindKt.toFloat() / 45f).coerceIn(0.18f, 0.92f)
          drawCircle(
            color = if (limitExceeded) palette.tailwindNegative.copy(alpha = 0.65f) else palette.metarAmber.copy(alpha = 0.45f),
            radius = radius * limitRatio,
            center = Offset(cx, cy),
            style = Stroke(
              width = 1.2.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f),
            ),
          )
        }

        // Compass 30° ticks & cardinal letters
        for (deg in 0 until 360 step 30) {
          val rad = (deg - 90.0) * PI / 180.0
          val cosA = cos(rad).toFloat()
          val sinA = sin(rad).toFloat()
          drawLine(
            color = palette.compassStroke.copy(alpha = 0.75f),
            start = Offset(cx + (radius - 4.dp.toPx()) * cosA, cy + (radius - 4.dp.toPx()) * sinA),
            end = Offset(cx + (radius + 3.dp.toPx()) * cosA, cy + (radius + 3.dp.toPx()) * sinA),
            strokeWidth = 1.1.dp.toPx(),
          )
          val lbl = when (deg) {
            0 -> "N"
            90 -> "E"
            180 -> "S"
            270 -> "W"
            else -> String.format(Locale.US, "%02d", deg / 10)
          }
          val measured = textMeasurer.measure(
            text = lbl,
            style = TextStyle(
              color = if (deg % 90 == 0) palette.metarAmber else palette.textSecondary,
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
            ),
          )
          val lr = radius + 10.5.dp.toPx()
          drawText(
            measured,
            topLeft = Offset(cx + lr * cosA - measured.size.width / 2f, cy + lr * sinA - measured.size.height / 2f),
          )
        }

        // Oriented Runway Strip
        rotate(degrees = animatedHeading, pivot = Offset(cx, cy)) {
          val rw = radius * 0.22f
          val rl = radius * 1.66f
          val left = cx - rw / 2f
          val top = cy - rl / 2f
          drawRect(
            color = palette.runwayFill,
            topLeft = Offset(left, top),
            size = Size(rw, rl),
          )
          drawRect(
            color = palette.runwayStroke,
            topLeft = Offset(left, top),
            size = Size(rw, rl),
            style = Stroke(width = 1.3.dp.toPx()),
          )
          drawLine(
            color = palette.textPrimary.copy(alpha = 0.65f),
            start = Offset(cx, top + 6.dp.toPx()),
            end = Offset(cx, top + rl - 6.dp.toPx()),
            strokeWidth = 1.1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f),
          )
          // Runway heading triangle pointing toward runway departure end (up in rotated space)
          val dirHead = Path().apply {
            moveTo(cx, top + 4.dp.toPx())
            lineTo(cx - 4.5.dp.toPx(), top + 12.dp.toPx())
            lineTo(cx + 4.5.dp.toPx(), top + 12.dp.toPx())
            close()
          }
          drawPath(dirHead, color = palette.headwindPositive)
        }

        // Wind Vector & Orthogonal Headwind / Crosswind Components
        if (!wind.isCalm && !wind.isVariable && wind.directionDeg != null) {
          val speedScale = (wind.speedKt.toFloat() / 40f).coerceIn(0.28f, 0.92f)
          val windOriginDist = radius * speedScale
          val windRad = (animatedWindDir - 90.0) * PI / 180.0
          val ox = cx + windOriginDist * cos(windRad).toFloat()
          val oy = cy + windOriginDist * sin(windRad).toFloat()

          val rwyRad = (animatedHeading - 90.0) * PI / 180.0
          val ux = cos(rwyRad).toFloat()
          val uy = sin(rwyRad).toFloat()
          val dot = (ox - cx) * ux + (oy - cy) * uy
          val projX = cx + dot * ux
          val projY = cy + dot * uy

          // Longitudinal component line (headwind green / tailwind red)
          val headColor = if (components.isTailwind) palette.tailwindNegative else palette.headwindPositive
          drawLine(
            color = headColor,
            start = Offset(cx, cy),
            end = Offset(projX, projY),
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round,
          )

          // Crosswind component dashed vector from runway projection to wind vector tip
          val xwColor = if (limitExceeded) palette.tailwindNegative else palette.metarAmber
          drawLine(
            color = xwColor,
            start = Offset(projX, projY),
            end = Offset(ox, oy),
            strokeWidth = 2.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 3f), 0f),
          )

          // Main incoming wind arrow pointing inward toward aircraft/runway center
          val arrowColor = if (limitExceeded) palette.tailwindNegative else palette.windArrow
          rotate(degrees = animatedWindDir, pivot = Offset(cx, cy)) {
            val outerY = cy - radius * 0.92f
            val innerY = cy - radius * 0.36f
            drawLine(
              color = arrowColor,
              start = Offset(cx, outerY),
              end = Offset(cx, innerY - 6.dp.toPx()),
              strokeWidth = 3.2.dp.toPx(),
              cap = StrokeCap.Round,
            )
            val head = Path().apply {
              moveTo(cx, innerY)
              lineTo(cx - 5.5.dp.toPx(), innerY - 9.dp.toPx())
              lineTo(cx + 5.5.dp.toPx(), innerY - 9.dp.toPx())
              close()
            }
            drawPath(head, color = arrowColor)
          }
        }
      }
    }

    if (limitExceeded && maxCrosswindKt != null && limitValDisp != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("visualizer_crosswind_warning")
          .semantics(mergeDescendants = true) {}
          .background(palette.tailwindNegative.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
          .border(1.dp, palette.tailwindNegative, RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 5.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = "Превышение ограничения бокового ветра",
          tint = palette.tailwindNegative,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = String.format(
            Locale.US,
            "ПРЕВЫШЕНИЕ БОКОВОГО ВЕТРА (%s): %.1f %s > лимита %.0f %s",
            aircraftLabel?.takeIf { it.isNotBlank() } ?: "ВС",
            crossValDisp,
            windUnit.label,
            limitValDisp,
            windUnit.label,
          ),
          color = palette.tailwindNegative,
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
        )
      }
    }
  }
}

/**
 * Reusable Composable that calculates and displays crosswind and headwind/tailwind
 * components for a given runway heading based on the current METAR wind speed and direction.
 */
@Composable
fun RunwayWindComponentsDisplay(
  runwayHeadingDeg: Int,
  wind: WindData,
  windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  runwayLabel: String? = null,
  maxCrosswindKt: Int? = null,
  onToggleUnit: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val components = remember(runwayHeadingDeg, wind) {
    calculateRunwayWindComponents(runwayHeadingDeg = runwayHeadingDeg, wind = wind)
  }

  val headVal = UnitConverter.convertWindFromKnots(abs(components.headwindKt), windUnit)
  val crossVal = UnitConverter.convertWindFromKnots(components.crosswindKt, windUnit)
  val gustHeadVal = components.gustHeadwindKt?.let { UnitConverter.convertWindFromKnots(abs(it), windUnit) }
  val gustCrossVal = components.gustCrosswindKt?.let { UnitConverter.convertWindFromKnots(it, windUnit) }

  val headTitle = when {
    components.isCalm -> "Штиль"
    components.isVariable -> "Продольн. (VRB)"
    components.isTailwind -> "Попутный (Tailwind)"
    else -> "Встречный (Headwind)"
  }
  val headColor = when {
    components.isCalm || components.isVariable -> palette.textSecondary
    components.isTailwind -> palette.tailwindNegative
    else -> palette.headwindPositive
  }
  val crossSide = when {
    components.isCalm -> ""
    components.isVariable -> " (макс.)"
    components.signedCrosswindKt > 0.35 -> " · справа"
    components.signedCrosswindKt < -0.35 -> " · слева"
    else -> " · по оси"
  }
  val limitExceeded = maxCrosswindKt != null && (
    components.crosswindKt > maxCrosswindKt + 0.05 ||
      (components.gustCrosswindKt != null && components.gustCrosswindKt > maxCrosswindKt + 0.05)
    )
  val crossWarningColor = when {
    limitExceeded || components.crosswindKt >= 20.0 -> palette.tailwindNegative
    components.crosswindKt >= 12.0 -> palette.metarAmber
    else -> palette.textPrimary
  }

  val metarWindStr = when {
    wind.isCalm -> "Штиль 0 ${windUnit.label}"
    wind.isVariable -> "VRB ${UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)} ${windUnit.label}"
    wind.directionDeg != null -> {
      val spd = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit)
      val gst = wind.gustKt?.let { " G${UnitConverter.formatWindSpeedValue(it, windUnit)}" }.orEmpty()
      String.format(Locale.US, "%03d° %d%s %s", wind.directionDeg, spd, gst, windUnit.label)
    }
    else -> "--"
  }
  val headingLabel = runwayLabel ?: String.format(Locale.US, "Курс %03d°", components.runwayHeadingDeg)

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("runway_wind_components_display")
      .background(palette.fieldBackground, RoundedCornerShape(4.dp))
      .border(1.dp, if (limitExceeded) palette.tailwindNegative else palette.border, RoundedCornerShape(4.dp))
      .padding(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.weight(1f),
      ) {
        Icon(
          imageVector = Icons.Filled.Air,
          contentDescription = null,
          tint = palette.metarAmber,
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "$headingLabel · METAR $metarWindStr",
          color = palette.textPrimary,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
        )
      }
      if (onToggleUnit != null) {
        Text(
          text = windUnit.label,
          color = palette.metarAmber,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(palette.secondaryFieldBackground)
            .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
            .clickable(onClick = onToggleUnit)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }
    }

    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .semantics(mergeDescendants = true) {}
          .testTag("rwy_component_headwind_box")
          .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
          .border(1.dp, headColor.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 6.dp),
      ) {
        Text(
          text = headTitle,
          color = palette.textSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
        )
        val sign = if (components.isTailwind) "-" else if (components.headwindKt > 0.05) "+" else ""
        val gustSuffix = gustHeadVal?.let { String.format(Locale.US, " (G%.1f)", it) }.orEmpty()
        Text(
          text = String.format(Locale.US, "%s%.1f %s%s", sign, headVal, windUnit.label, gustSuffix),
          color = headColor,
          fontSize = 13.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
        )
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .semantics(mergeDescendants = true) {}
          .testTag("rwy_component_crosswind_box")
          .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
          .border(1.dp, crossWarningColor.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
          .padding(horizontal = 8.dp, vertical = 6.dp),
      ) {
        Text(
          text = "Боковой (Crosswind)$crossSide",
          color = palette.textSecondary,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
        )
        val gustCrossSuffix = gustCrossVal?.let { String.format(Locale.US, " (G%.1f)", it) }.orEmpty()
        Text(
          text = String.format(Locale.US, "%.1f %s%s", crossVal, windUnit.label, gustCrossSuffix),
          color = crossWarningColor,
          fontSize = 13.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
        )
      }
    }
  }
}

@Composable
fun InteractiveRunwayHeadingWindCard(
  wind: WindData,
  defaultHeadingDeg: Int,
  windUnit: WindSpeedUnit,
  onToggleUnit: () -> Unit,
  maxCrosswindKt: Int? = null,
  aircraftLabel: String? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var headingInput by remember { mutableStateOf(String.format(Locale.US, "%03d", normalizeRunwayHeading(defaultHeadingDeg))) }
  var useCustomWind by remember { mutableStateOf(false) }
  var windDirInput by remember {
    mutableStateOf(wind.directionDeg?.let { String.format(Locale.US, "%03d", normalizeRunwayHeading(it)) } ?: "270")
  }
  var windSpeedInput by remember {
    mutableStateOf(UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit).toString())
  }

  LaunchedEffect(defaultHeadingDeg) {
    headingInput = String.format(Locale.US, "%03d", normalizeRunwayHeading(defaultHeadingDeg))
  }

  LaunchedEffect(wind, windUnit, useCustomWind) {
    if (!useCustomWind) {
      windDirInput = wind.directionDeg?.let { String.format(Locale.US, "%03d", normalizeRunwayHeading(it)) } ?: "270"
      windSpeedInput = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit).toString()
    }
  }

  val activeHeading = headingInput.toIntOrNull()?.let { normalizeRunwayHeading(it) }
    ?: normalizeRunwayHeading(defaultHeadingDeg)

  val effectiveWind = remember(useCustomWind, windDirInput, windSpeedInput, wind, windUnit) {
    if (!useCustomWind) {
      wind
    } else {
      val parsedDir = windDirInput.toIntOrNull()?.let { normalizeRunwayHeading(it) }
        ?: (wind.directionDeg ?: 270)
      val parsedDispSpeed = windSpeedInput.trim().toDoubleOrNull()?.coerceIn(0.0, 200.0) ?: 0.0
      val speedKt = UnitConverter.convertWind(parsedDispSpeed, windUnit, WindSpeedUnit.KT).roundToInt()
      WindData(
        directionDeg = parsedDir,
        speedKt = speedKt,
        gustKt = null,
        speedMps = UnitConverter.convertKnotsToMps(speedKt.toDouble()),
        isVariable = false,
        isCalm = speedKt == 0,
      )
    }
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("interactive_runway_heading_calculator")
      .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
      .padding(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedTextField(
        value = headingInput,
        onValueChange = { raw ->
          headingInput = raw.filter { it.isDigit() }.take(3)
        },
        label = { Text("Курс ВПП (°)", fontSize = 10.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
          .width(120.dp)
          .testTag("custom_runway_heading_input"),
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.weight(1f),
      ) {
        OutlinedButton(
          onClick = {
            val next = normalizeRunwayHeading(activeHeading - 10)
            headingInput = String.format(Locale.US, "%03d", next)
          },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("custom_runway_heading_minus10"),
        ) {
          Text("-10°", fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        OutlinedButton(
          onClick = {
            val next = normalizeRunwayHeading(activeHeading + 10)
            headingInput = String.format(Locale.US, "%03d", next)
          },
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("custom_runway_heading_plus10"),
        ) {
          Text("+10°", fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        OutlinedButton(
          onClick = {
            val rev = normalizeRunwayHeading(activeHeading + 180)
            headingInput = String.format(Locale.US, "%03d", rev)
          },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .testTag("custom_runway_heading_flip"),
        ) {
          Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = "Обратный курс ВПП +180°",
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(2.dp))
          Text("180°", fontSize = 11.sp)
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedTextField(
        value = windDirInput,
        onValueChange = { raw ->
          useCustomWind = true
          windDirInput = raw.filter { it.isDigit() }.take(3)
        },
        label = { Text("Напр. ветра (°)", fontSize = 10.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
          .weight(1f)
          .testTag("custom_wind_dir_input"),
      )
      OutlinedTextField(
        value = windSpeedInput,
        onValueChange = { raw ->
          useCustomWind = true
          windSpeedInput = raw.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.').take(5)
        },
        label = { Text("Скор. (${windUnit.label})", fontSize = 10.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
          .weight(1f)
          .testTag("custom_wind_speed_input"),
      )
      OutlinedButton(
        onClick = {
          useCustomWind = false
          windDirInput = wind.directionDeg?.let { String.format(Locale.US, "%03d", normalizeRunwayHeading(it)) } ?: "270"
          windSpeedInput = UnitConverter.formatWindSpeedValue(wind.speedKt, windUnit).toString()
        },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("custom_wind_reset_metar_button"),
      ) {
        Text("Из METAR", fontSize = 11.sp)
      }
    }

    RunwayWindComponentsDisplay(
      runwayHeadingDeg = activeHeading,
      wind = effectiveWind,
      windUnit = windUnit,
      maxCrosswindKt = maxCrosswindKt,
      onToggleUnit = onToggleUnit,
    )

    RunwayOrientationWindVisualizer(
      runwayHeadingDeg = activeHeading,
      wind = effectiveWind,
      windUnit = windUnit,
      maxCrosswindKt = maxCrosswindKt,
      aircraftLabel = aircraftLabel,
      onToggleUnit = onToggleUnit,
    )
  }
}

@Composable
fun CrosswindCalculatorDialog(
  station: AirportStation,
  wind: WindData,
  defaultHeadingDeg: Int,
  windUnit: WindSpeedUnit,
  onToggleUnit: () -> Unit,
  maxCrosswindKt: Int? = null,
  aircraftLabel: String? = null,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(
          text = "Калькулятор бокового ветра · ${station.icao}",
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Расчёт встречного/попутного и бокового ветра для любого курса ВПП",
          color = palette.textSecondary,
          fontSize = 11.5.sp,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 480.dp)
          .verticalScroll(rememberScrollState())
          .testTag("crosswind_calculator_dialog"),
      ) {
        InteractiveRunwayHeadingWindCard(
          wind = wind,
          defaultHeadingDeg = defaultHeadingDeg,
          windUnit = windUnit,
          onToggleUnit = onToggleUnit,
          maxCrosswindKt = maxCrosswindKt,
          aircraftLabel = aircraftLabel,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("crosswind_dialog_close_button"),
      ) {
        Text("Закрыть")
      }
    },
  )
}
