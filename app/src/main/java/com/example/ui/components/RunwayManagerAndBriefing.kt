package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AirportRunwaysSerializer
import com.example.domain.DispatcherBriefingHelper
import com.example.domain.FlightCategory
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.SigmetAlert
import com.example.model.WeatherAlertConfig
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DispatcherBriefingCard(
  station: AirportStation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  alertConfig: WeatherAlertConfig,
  activeSigmets: List<SigmetAlert>,
  isLoadingSigmets: Boolean,
  onOpenSigmetDialog: () -> Unit,
  onOpenManageRunways: () -> Unit,
  onSelectRunwayPair: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val briefing = remember(station, weather, alertConfig) {
    DispatcherBriefingHelper.evaluate(station, weather, alertConfig)
  }

  val catColor = when (briefing.flightCategory) {
    FlightCategory.VFR -> palette.headwindPositive
    FlightCategory.MVFR -> Color(0xFF0284C7)
    FlightCategory.IFR -> palette.tailwindNegative
    FlightCategory.UNKNOWN -> palette.textMuted
  }
  val sigmetSevereCount = activeSigmets.count { it.hazard.isSevere }
  val sigmetColor = when {
    sigmetSevereCount > 0 -> palette.tailwindNegative
    activeSigmets.isNotEmpty() -> palette.metarAmber
    else -> palette.textSecondary
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("dispatcher_briefing_card")
      .background(palette.panelSurface)
      .border(1.dp, palette.border)
      .padding(10.dp),
  ) {
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
        Text(
          text = briefing.flightCategory.code,
          color = catColor,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier
            .background(catColor.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
            .border(1.dp, catColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text(
          text = "БРИФИНГ ДИСПЕТЧЕРА · ${station.icao}",
          color = palette.textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
        )
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .heightIn(min = 36.dp)
            .testTag("dispatcher_sigmet_chip")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.secondaryFieldBackground)
            .border(1.dp, sigmetColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(onClick = onOpenSigmetDialog)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          val label = when {
            isLoadingSigmets -> "SIGMET…"
            activeSigmets.isNotEmpty() -> "SIGMET (${activeSigmets.size})"
            else -> "SIGMET: 0"
          }
          Text(
            text = label,
            color = sigmetColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
        }

        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .heightIn(min = 36.dp)
            .testTag("dispatcher_manage_runways_chip")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onOpenManageRunways)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Text(
            text = "ВПП (${station.runways.size})",
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      briefing.recommendedRunway?.let { rec ->
        val crossDisp = UnitConverter.convertWindFromKnots(abs(rec.crosswindKt), windUnit)
        val headDisp = UnitConverter.convertWindFromKnots(rec.headwindKt, windUnit)
        val headTag = if (headDisp >= 0) "встр." else "поп."
        Text(
          text = String.format(
            Locale.US,
            "Реком. ВПП %s (%03d°): %s %.1f · бок. %.1f %s",
            rec.designator,
            rec.headingDeg,
            headTag,
            abs(headDisp),
            crossDisp,
            windUnit.label,
          ),
          color = palette.headwindPositive,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }

      briefing.tempDewSpreadC?.let { spread ->
        val spreadColor = if (spread <= 2) palette.metarAmber else palette.textSecondary
        Text(
          text = "T−Td: $spread°C",
          color = spreadColor,
          fontSize = 11.5.sp,
          fontFamily = FontFamily.Monospace,
        )
      }

      briefing.gustSpreadKt?.takeIf { it > 0 }?.let { gustSpread ->
        val gDisp = UnitConverter.formatWindSpeedValue(gustSpread, windUnit)
        Text(
          text = "Порывы: +$gDisp ${windUnit.label}",
          color = if (gustSpread >= 10) palette.metarAmber else palette.textSecondary,
          fontSize = 11.5.sp,
          fontFamily = FontFamily.Monospace,
        )
      }

      briefing.metarAgeMinutes?.let { age ->
        Text(
          text = "Возраст METAR: ${age}м",
          color = if (briefing.isMetarStale) palette.tailwindNegative else palette.textSecondary,
          fontSize = 11.5.sp,
          fontFamily = FontFamily.Monospace,
        )
      }
    }

    if (briefing.hazards.isNotEmpty()) {
      Text(
        text = "Факторы: ${briefing.hazards.joinToString(" · ")}",
        color = palette.tailwindNegative,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
      )
    }

    // Quick clickable strip for each configured runway pair of this airport
    if (station.runways.size > 1) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        station.runways.forEachIndexed { idx, pair ->
          val pairEval = briefing.runwayEvaluations.firstOrNull {
            it.calculation.designator == pair.primaryDesignator
          }
          val ok = pairEval?.isWithinCrosswindLimit ?: true
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(3.dp))
              .background(palette.secondaryFieldBackground)
              .border(
                0.8.dp,
                if (ok) palette.border else palette.tailwindNegative,
                RoundedCornerShape(3.dp),
              )
              .clickable { onSelectRunwayPair(idx) }
              .padding(horizontal = 7.dp, vertical = 3.dp),
          ) {
            Text(
              text = "${pair.primaryDesignator}/${pair.reciprocalDesignator} (${AirportRunwaysSerializer.formatMagneticHeading(pair.primaryHeadingDeg)})",
              color = if (ok) palette.textPrimary else palette.tailwindNegative,
              fontSize = 10.5.sp,
              fontFamily = FontFamily.Monospace,
            )
          }
        }
      }
    }
  }
}

@Composable
fun RunwayManagerDialog(
  station: AirportStation,
  selectedRunwayIndex: Int,
  onSelectRunwayPair: (Int) -> Unit,
  onAddRunwayPair: (primaryDesignator: String, primaryHeadingDeg: Int) -> Unit,
  onRemoveRunwayPair: (Int) -> Unit,
  onResetRunwaysToDefault: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  var designatorInput by remember(station.icao) { mutableStateOf("") }
  var headingInput by remember(station.icao) { mutableStateOf("") }
  var errorText by remember { mutableStateOf<String?>(null) }

  val parsedHdg = headingInput.trim().toIntOrNull()
  val previewPair = remember(designatorInput, parsedHdg) {
    if (parsedHdg != null && parsedHdg in 1..360) {
      AirportRunwaysSerializer.buildRunwayPair(
        primaryDesignatorInput = designatorInput,
        primaryMagneticHeadingDeg = parsedHdg,
      )
    } else {
      null
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.panelSurface,
    titleContentColor = palette.textPrimary,
    textContentColor = palette.textPrimary,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.FlightLand,
          contentDescription = null,
          tint = palette.metarAmber,
        )
        Text(
          text = "ВПП аэродрома ${station.icao}",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("runway_manager_dialog")
          .verticalScroll(rememberScrollState()),
      ) {
        Text(
          text = "Управление полосами и магнитными курсами для расчёта бокового и встречного ветра:",
          color = palette.textSecondary,
          fontSize = 11.5.sp,
        )

        station.runways.forEachIndexed { idx, pair ->
          val isSelected = idx == selectedRunwayIndex
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(4.dp))
              .background(if (isSelected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
              .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) palette.metarAmber else palette.border,
                shape = RoundedCornerShape(4.dp),
              )
              .clickable { onSelectRunwayPair(idx) }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "ВПП ${pair.primaryDesignator} / ${pair.reciprocalDesignator}",
                color = palette.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "МКпос: ${AirportRunwaysSerializer.formatMagneticHeading(pair.primaryHeadingDeg)} / ${AirportRunwaysSerializer.formatMagneticHeading(pair.reciprocalHeadingDeg)}",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
            if (station.runways.size > 1) {
              IconButton(
                onClick = { onRemoveRunwayPair(idx) },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("remove_runway_$idx"),
              ) {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = "Удалить ВПП ${pair.primaryDesignator}",
                  tint = palette.tailwindNegative,
                )
              }
            }
          }
        }

        HorizontalDivider(color = palette.border)

        Text(
          text = "Добавить новую ВПП",
          color = palette.textPrimary,
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedTextField(
            value = designatorInput,
            onValueChange = {
              designatorInput = it.uppercase(Locale.US).take(4)
              errorText = null
            },
            label = { Text("Номер (09L)", fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier
              .weight(1f)
              .testTag("runway_designator_input"),
          )
          OutlinedTextField(
            value = headingInput,
            onValueChange = {
              headingInput = it.filter(Char::isDigit).take(3)
              errorText = null
            },
            label = { Text("Курс 1..360°", fontSize = 11.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .weight(1f)
              .testTag("runway_heading_input"),
          )
        }

        if (previewPair != null) {
          Text(
            text = "Будет добавлена: ${previewPair.primaryDesignator} (${AirportRunwaysSerializer.formatMagneticHeading(previewPair.primaryHeadingDeg)}) / ${previewPair.reciprocalDesignator} (${AirportRunwaysSerializer.formatMagneticHeading(previewPair.reciprocalHeadingDeg)})",
            color = palette.headwindPositive,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
          )
        }

        if (errorText != null) {
          Text(
            text = errorText.orEmpty(),
            color = palette.tailwindNegative,
            fontSize = 11.sp,
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            onClick = {
              val hdg = headingInput.trim().toIntOrNull()
              if (hdg == null || hdg !in 1..360) {
                errorText = "Введите магнитный курс от 1 до 360°"
              } else {
                onAddRunwayPair(designatorInput, hdg)
                designatorInput = ""
                headingInput = ""
              }
            },
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("add_runway_button"),
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Добавить")
          }

          OutlinedButton(
            onClick = onResetRunwaysToDefault,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("reset_runways_button"),
          ) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Сброс")
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("runway_manager_close_button"),
      ) {
        Text("Закрыть", color = palette.metarAmber, fontWeight = FontWeight.Bold)
      }
    },
  )
}

@Composable
fun SigmetAlertsDialog(
  station: AirportStation,
  pressureUnit: PressureUnit,
  sigmets: List<SigmetAlert>,
  isLoading: Boolean,
  onRefresh: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = palette.panelSurface,
    titleContentColor = palette.textPrimary,
    textContentColor = palette.textPrimary,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = if (sigmets.any { it.hazard.isSevere }) palette.tailwindNegative else palette.metarAmber,
          )
          Text(
            text = "SIGMET / AIRMET · ${station.icao}",
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
          )
        }
        IconButton(
          onClick = onRefresh,
          modifier = Modifier
            .size(48.dp)
            .testTag("sigmet_refresh_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Обновить SIGMET",
              tint = palette.metarAmber,
            )
          }
        }
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sigmet_dialog")
          .verticalScroll(rememberScrollState()),
      ) {
        if (sigmets.isEmpty()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
              .padding(12.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = palette.headwindPositive,
            )
            Text(
              text = if (isLoading) {
                "Загрузка активных SIGMET/AIRMET для района ${station.icao}…"
              } else {
                "Активных штормовых предупреждений SIGMET/AIRMET в радиусе 1400 км от ${station.icao} не обнаружено."
              },
              color = palette.textSecondary,
              fontSize = 12.sp,
            )
          }
        } else {
          sigmets.forEachIndexed { idx, s ->
            val accent = if (s.hazard.isSevere) palette.tailwindNegative else palette.metarAmber
            Column(
              verticalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("sigmet_item_$idx")
                .background(palette.fieldBackground, RoundedCornerShape(4.dp))
                .border(1.dp, accent, RoundedCornerShape(4.dp))
                .padding(10.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "${if (s.isAirmet) "AIRMET" else "SIGMET"} · ${s.hazard.label}",
                  color = accent,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                )
                s.distanceKm?.let { d ->
                  Text(
                    text = "~$d км",
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                  )
                }
              }
              Text(
                text = "FIR: ${s.firName.ifBlank { s.icaoId }} · Высоты: ${s.altitudeRangeLabel(pressureUnit)}",
                color = palette.textSecondary,
                fontSize = 11.sp,
              )
              Text(
                text = "Действует (UTC): ${s.validFromUtc} – ${s.validToUtc}",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = s.rawText,
                color = palette.textPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("sigmet_close_button"),
      ) {
        Text("Закрыть", color = palette.metarAmber, fontWeight = FontWeight.Bold)
      }
    },
  )
}
