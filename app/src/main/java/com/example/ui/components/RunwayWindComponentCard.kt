package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
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
 * Reusable Composable that calculates and displays crosswind and headwind/tailwind
 * components for a given runway heading based on the current METAR wind speed and direction.
 */
@Composable
fun RunwayWindComponentsDisplay(
  runwayHeadingDeg: Int,
  wind: WindData,
  windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  runwayLabel: String? = null,
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
  val crossWarningColor = when {
    components.crosswindKt >= 20.0 -> palette.tailwindNegative
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
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
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
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var headingInput by remember { mutableStateOf(String.format(Locale.US, "%03d", normalizeRunwayHeading(defaultHeadingDeg))) }

  LaunchedEffect(defaultHeadingDeg) {
    headingInput = String.format(Locale.US, "%03d", normalizeRunwayHeading(defaultHeadingDeg))
  }

  val activeHeading = headingInput.toIntOrNull()?.let { normalizeRunwayHeading(it) }
    ?: normalizeRunwayHeading(defaultHeadingDeg)

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
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
          val digits = raw.filter { it.isDigit() }.take(3)
          headingInput = digits
        },
        label = { Text("Курс ВПП (°)", fontSize = 10.5.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
          .width(126.dp)
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
            .heightIn(min = 42.dp)
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
            .heightIn(min = 42.dp)
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
            .heightIn(min = 42.dp)
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

    RunwayWindComponentsDisplay(
      runwayHeadingDeg = activeHeading,
      wind = wind,
      windUnit = windUnit,
      onToggleUnit = onToggleUnit,
    )
  }
}
