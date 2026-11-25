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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.roundToInt

private enum class PressureCalcDirection(val label: String) {
  QNH_TO_QFE("QNH → QFE"),
  QFE_TO_QNH("QFE → QNH"),
}

@Composable
fun PressureConversionUtilityDialog(
  station: AirportStation,
  weather: DecodedWeather,
  boardPressureUnit: PressureUnit,
  onSelectBoardPressureUnit: (PressureUnit) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Compress,
          contentDescription = null,
          tint = palette.metarAmber,
        )
        Text(
          text = "Калькулятор давления QNH / QFE",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 460.dp)
          .verticalScroll(rememberScrollState())
          .testTag("pressure_utility_dialog"),
      ) {
        PressureConversionUtilityPanel(
          station = station,
          weather = weather,
          boardPressureUnit = boardPressureUnit,
          onSelectBoardPressureUnit = onSelectBoardPressureUnit,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("pressure_utility_close_button"),
      ) {
        Text("Закрыть")
      }
    },
  )
}

@Composable
fun PressureConversionUtilityPanel(
  station: AirportStation,
  weather: DecodedWeather,
  boardPressureUnit: PressureUnit,
  onSelectBoardPressureUnit: ((PressureUnit) -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var activeUnit by remember(boardPressureUnit) { mutableStateOf(boardPressureUnit) }
  var direction by remember { mutableStateOf(PressureCalcDirection.QNH_TO_QFE) }
  var autoSyncMetar by remember { mutableStateOf(true) }
  var elevationInput by remember(station.icao) { mutableStateOf(station.elevationMeters.toString()) }
  var pressureInput by remember(station.icao) {
    val initialHpa = (weather.qnhHpa ?: 1013).toDouble()
    mutableStateOf(
      if (boardPressureUnit == PressureUnit.HPA) {
        initialHpa.roundToInt().toString()
      } else {
        String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(initialHpa))
      },
    )
  }

  LaunchedEffect(station.icao) {
    elevationInput = station.elevationMeters.toString()
  }

  LaunchedEffect(station.icao, weather.rawMetar, weather.qnhHpa, weather.qfeHpa, autoSyncMetar) {
    if (autoSyncMetar) {
      val sourceHpa = when (direction) {
        PressureCalcDirection.QNH_TO_QFE -> (weather.qnhHpa ?: 1013).toDouble()
        PressureCalcDirection.QFE_TO_QNH -> {
          val eMeters = elevationInput.toIntOrNull()?.coerceIn(-500, 6000) ?: station.elevationMeters
          (weather.qfeHpa?.toDouble()) ?: UnitConverter.computeQfeFromQnhHpa((weather.qnhHpa ?: 1013).toDouble(), eMeters)
        }
      }
      pressureInput = if (activeUnit == PressureUnit.HPA) {
        sourceHpa.roundToInt().toString()
      } else {
        String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(sourceHpa))
      }
    }
  }

  val switchUnit: (PressureUnit) -> Unit = { targetUnit ->
    if (targetUnit != activeUnit) {
      val parsed = pressureInput.replace(',', '.').toDoubleOrNull()
      if (parsed != null) {
        pressureInput = if (targetUnit == PressureUnit.INHG) {
          String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(parsed))
        } else {
          UnitConverter.convertInHgToHpa(parsed).roundToInt().toString()
        }
      }
      activeUnit = targetUnit
    }
  }

  val elevMeters = elevationInput.toIntOrNull()?.coerceIn(-500, 6000) ?: station.elevationMeters
  val elevFeet = (elevMeters * 3.28084).roundToInt()

  val rawEntered = pressureInput.replace(',', '.').toDoubleOrNull()
  val conversion = rawEntered?.let { value ->
    UnitConverter.convertQnhQfe(
      pressureValue = value,
      unit = activeUnit,
      elevationMeters = elevMeters,
      fromQnhToQfe = direction == PressureCalcDirection.QNH_TO_QFE,
    )
  }

  val qnhHpaExact = conversion?.qnhHpa
  val qfeHpaExact = conversion?.qfeHpa
  val qnhInHgExact = conversion?.qnhInHg
  val qnhMmHg = conversion?.qnhMmHg
  val qfeInHgExact = conversion?.qfeInHg
  val qfeMmHg = conversion?.qfeMmHg
  val pressureAltFt = conversion?.pressureAltitudeFeet
  val deltaHpa = conversion?.baricOffsetHpa

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("pressure_conversion_utility")
      .background(palette.panelSurface, RoundedCornerShape(4.dp))
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
      .padding(10.dp),
  ) {
    // Live METAR auto-update banner + switch
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
        .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Авто-обновление от METAR (${station.icao})",
          color = palette.textPrimary,
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "METAR ${weather.observationTimeUtc} · QNH ${weather.qnhHpa ?: "--"} гПа",
          color = palette.textSecondary,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
        )
      }
      Switch(
        checked = autoSyncMetar,
        onCheckedChange = { autoSyncMetar = it },
        modifier = Modifier.testTag("pressure_utility_auto_sync_switch"),
      )
    }
    // Unit Toggle: hPa <-> inHg
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("pressure_utility_unit_toggle"),
    ) {
      PressureUnit.entries.forEach { unit ->
        val selected = unit == activeUnit
        val label = if (unit == PressureUnit.HPA) "hPa (гПа)" else "inHg (дм рт. ст.)"
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("pressure_utility_unit_${unit.name}")
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
            .border(
              width = if (selected) 1.5.dp else 1.dp,
              color = if (selected) MaterialTheme.colorScheme.primary else palette.border,
              shape = RoundedCornerShape(4.dp),
            )
            .clickable { switchUnit(unit) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
          Text(
            text = label,
            color = if (selected) palette.metarAmber else palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
          )
        }
      }
    }

    // Conversion Direction Toggle: QNH -> QFE vs QFE -> QNH
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("pressure_utility_mode_toggle"),
    ) {
      PressureCalcDirection.entries.forEach { mode ->
        val selected = mode == direction
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("pressure_utility_mode_${mode.name}")
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.buttonActiveBackground else palette.fieldBackground)
            .border(
              width = if (selected) 1.4.dp else 1.dp,
              color = if (selected) palette.borderStrong else palette.border,
              shape = RoundedCornerShape(4.dp),
            )
            .clickable { direction = mode }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
          Text(
            text = mode.label,
            color = if (selected) palette.textPrimary else palette.textSecondary,
            fontSize = 11.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
          )
        }
      }
    }

    // Pressure Input & Field Elevation Input
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Top,
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedTextField(
        value = pressureInput,
        onValueChange = { raw ->
          if (raw.length <= 8) pressureInput = raw
        },
        label = {
          Text(
            text = "${if (direction == PressureCalcDirection.QNH_TO_QFE) "QNH" else "QFE"} (${activeUnit.label})",
            fontSize = 11.sp,
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier
          .weight(1.1f)
          .testTag("pressure_utility_value_input"),
      )

      OutlinedTextField(
        value = elevationInput,
        onValueChange = { raw ->
          val cleaned = raw.filterIndexed { idx, c -> c.isDigit() || (idx == 0 && c == '-') }.take(5)
          elevationInput = cleaned
        },
        label = { Text("Превыш. (м)", fontSize = 11.sp) },
        supportingText = {
          Text(
            text = "${elevFeet} ft",
            fontSize = 10.sp,
            color = palette.textMuted,
          )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
          .weight(0.9f)
          .testTag("pressure_utility_elevation_input"),
      )
    }

    // Quick Presets + Elevation Nudge Strip
    Row(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      OutlinedButton(
        onClick = {
          pressureInput = if (activeUnit == PressureUnit.HPA) "1013" else "29.92"
        },
        modifier = Modifier
          .heightIn(min = 38.dp)
          .testTag("pressure_utility_std_button"),
      ) {
        Text(
          text = if (activeUnit == PressureUnit.HPA) "STD 1013" else "STD 29.92",
          fontSize = 10.5.sp,
          fontFamily = FontFamily.Monospace,
        )
      }

      OutlinedButton(
        onClick = {
          val qnh = (weather.qnhHpa ?: 1013).toDouble()
          direction = PressureCalcDirection.QNH_TO_QFE
          elevationInput = station.elevationMeters.toString()
          pressureInput = if (activeUnit == PressureUnit.HPA) {
            qnh.roundToInt().toString()
          } else {
            String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(qnh))
          }
        },
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 38.dp)
          .testTag("pressure_utility_sync_metar_button"),
      ) {
        Text(
          text = "METAR ${station.icao}",
          fontSize = 10.5.sp,
          maxLines = 1,
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(38.dp)
            .testTag("pressure_utility_elev_minus")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.secondaryFieldBackground)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable { elevationInput = (elevMeters - 50).coerceAtLeast(-300).toString() },
        ) {
          Text("-50", color = palette.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .size(38.dp)
            .testTag("pressure_utility_elev_plus")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.secondaryFieldBackground)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable { elevationInput = (elevMeters + 50).coerceAtMost(5000).toString() },
        ) {
          Text("+50", color = palette.textPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }

    HorizontalDivider(thickness = 1.dp, color = palette.border)

    // Results Grid: QNH vs QFE in hPa, inHg, mmHg
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      PressureResultCard(
        title = "QNH (Уровень моря)",
        primaryText = when {
          qnhHpaExact == null -> "---"
          activeUnit == PressureUnit.HPA -> "${qnhHpaExact.roundToInt()} гПа"
          else -> "${String.format(Locale.US, "%.2f", qnhInHgExact)} inHg"
        },
        secondaryText = when {
          qnhHpaExact == null -> "Введите корректное давление"
          activeUnit == PressureUnit.HPA ->
            "${String.format(Locale.US, "%.2f", qnhInHgExact)} inHg · ${qnhMmHg} мм рт. ст."
          else ->
            "${qnhHpaExact.roundToInt()} гПа · ${qnhMmHg} мм рт. ст."
        },
        isHighlighted = direction == PressureCalcDirection.QFE_TO_QNH,
        testTag = "pressure_utility_qnh_result",
        modifier = Modifier.weight(1f),
      )

      PressureResultCard(
        title = "QFE (Порог ВПП)",
        primaryText = when {
          qfeHpaExact == null -> "---"
          activeUnit == PressureUnit.HPA -> "${qfeHpaExact.roundToInt()} гПа"
          else -> "${String.format(Locale.US, "%.2f", qfeInHgExact)} inHg"
        },
        secondaryText = when {
          qfeHpaExact == null -> "Введите корректное давление"
          activeUnit == PressureUnit.HPA ->
            "${String.format(Locale.US, "%.2f", qfeInHgExact)} inHg · ${qfeMmHg} мм рт. ст."
          else ->
            "${qfeHpaExact.roundToInt()} гПа · ${qfeMmHg} мм рт. ст."
        },
        isHighlighted = direction == PressureCalcDirection.QNH_TO_QFE,
        testTag = "pressure_utility_qfe_result",
        modifier = Modifier.weight(1f),
      )
    }

    // Flight Planning Metrics Summary (Pressure Altitude + Baric Offset)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
        .testTag("pressure_utility_planning_metrics")
        .background(palette.fieldBackground, RoundedCornerShape(4.dp))
        .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
        .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
      Column {
        Text(
          text = "Баром. высота (PA)",
          color = palette.textMuted,
          fontSize = 10.sp,
        )
        Text(
          text = pressureAltFt?.let { paFt ->
            val paM = (paFt / 3.28084).roundToInt()
            "$paFt ft ($paM м)"
          } ?: "---",
          color = palette.textPrimary,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
        )
      }

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "Поправка (QNH − QFE)",
          color = palette.textMuted,
          fontSize = 10.sp,
        )
        Text(
          text = deltaHpa?.let { dHpa ->
            val dIn = UnitConverter.convertHpaToInHg(dHpa)
            String.format(Locale.US, "+%.1f гПа / +%.2f inHg", dHpa, dIn)
          } ?: "---",
          color = palette.metarAmber,
          fontSize = 11.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    // Apply Selected Pressure Unit to Main Board
    if (onSelectBoardPressureUnit != null) {
      val alreadySynced = activeUnit == boardPressureUnit
      Button(
        enabled = !alreadySynced,
        onClick = { onSelectBoardPressureUnit(activeUnit) },
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 44.dp)
          .testTag("pressure_utility_apply_unit_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.SwapHoriz,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (alreadySynced) {
            "Табло отображает ${activeUnit.label}"
          } else {
            "Применить ${activeUnit.label} ко всему табло"
          },
          fontSize = 12.sp,
        )
      }
    }
  }
}

@Composable
private fun PressureResultCard(
  title: String,
  primaryText: String,
  secondaryText: String,
  isHighlighted: Boolean,
  testTag: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  Column(
    verticalArrangement = Arrangement.spacedBy(3.dp),
    modifier = modifier
      .semantics(mergeDescendants = true) {}
      .testTag(testTag)
      .background(
        if (isHighlighted) palette.buttonActiveBackground else palette.fieldBackground,
        RoundedCornerShape(4.dp),
      )
      .border(
        width = if (isHighlighted) 1.4.dp else 1.dp,
        color = if (isHighlighted) palette.metarAmber else palette.borderStrong,
        shape = RoundedCornerShape(4.dp),
      )
      .padding(horizontal = 9.dp, vertical = 7.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Terrain,
        contentDescription = null,
        tint = if (isHighlighted) palette.metarAmber else palette.textMuted,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text = title,
        color = palette.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
      )
    }
    Text(
      text = primaryText,
      color = if (isHighlighted) palette.metarAmber else palette.textPrimary,
      fontSize = 15.5.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.ExtraBold,
    )
    Text(
      text = secondaryText,
      color = palette.textSecondary,
      fontSize = 10.5.sp,
      fontFamily = FontFamily.Monospace,
      lineHeight = 13.5.sp,
    )
  }
}
