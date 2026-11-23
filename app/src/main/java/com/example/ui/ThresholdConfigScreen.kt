package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.TafParser
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.WeatherAlertConfig
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThresholdConfigScreen(
  station: AirportStation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  alertConfig: WeatherAlertConfig,
  isFullscreen: Boolean,
  onUpdateAlertConfig: (WeatherAlertConfig) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  val windThresholdDisplay = UnitConverter.formatWindSpeedValue(alertConfig.tableWindAlertKt, windUnit)
  var windInputText by remember { mutableStateOf(windThresholdDisplay.toString()) }
  var visInputText by remember { mutableStateOf(alertConfig.tableVisibilityAlertMeters.toString()) }

  LaunchedEffect(alertConfig.tableWindAlertKt, windUnit) {
    windInputText = UnitConverter.formatWindSpeedValue(alertConfig.tableWindAlertKt, windUnit).toString()
  }
  LaunchedEffect(alertConfig.tableVisibilityAlertMeters) {
    visInputText = alertConfig.tableVisibilityAlertMeters.toString()
  }

  fun toKnotsFromDisplay(displayVal: Int): Int {
    val rawKt = when (windUnit) {
      WindSpeedUnit.KT -> displayVal.toDouble()
      WindSpeedUnit.MPS -> displayVal / 0.514444
      WindSpeedUnit.KPH -> displayVal / 1.852
    }
    return rawKt.roundToInt().coerceIn(5, 80)
  }

  fun updateWindKt(newKt: Int) {
    onUpdateAlertConfig(alertConfig.copy(tableWindAlertKt = newKt.coerceIn(5, 80)))
  }

  fun updateVisibilityMeters(newMeters: Int) {
    onUpdateAlertConfig(alertConfig.copy(tableVisibilityAlertMeters = newMeters.coerceIn(200, 9999)))
  }

  val decodedTafPreview = remember(weather.rawTaf, windUnit, alertConfig) {
    TafParser.parse(weather.rawTaf, windUnit, alertConfig)
  }
  val currentPeakWindKt = weather.wind.gustKt ?: weather.wind.speedKt
  val metarWindTriggered = alertConfig.tableAlertsEnabled && currentPeakWindKt >= alertConfig.tableWindAlertKt
  val metarVisTriggered = alertConfig.tableAlertsEnabled &&
    !weather.isCavok &&
    weather.visibilityMeters != null &&
    weather.visibilityMeters <= alertConfig.tableVisibilityAlertMeters
  val tafWindTriggeredCount = decodedTafPreview.periods.count { it.isWindAlert }
  val tafVisTriggeredCount = decodedTafPreview.periods.count { it.isVisibilityAlert }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("threshold_config_screen")
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 920.dp)
        .align(Alignment.TopCenter),
    ) {
      // Top Navigation Bar
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(horizontal = 6.dp, vertical = 4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.weight(1f),
        ) {
          IconButton(
            onClick = onBack,
            modifier = Modifier
              .size(48.dp)
              .testTag("thresholds_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад",
              tint = palette.textPrimary,
            )
          }
          Column {
            Text(
              text = "Пороги тревоги METAR / TAF",
              color = palette.textPrimary,
              fontSize = 15.5.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Визуальное выделение опасного ветра и видимости в таблице",
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (alertConfig.tableAlertsEnabled) palette.buttonActiveBackground else palette.secondaryFieldBackground)
            .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
          Text(
            text = if (alertConfig.tableAlertsEnabled) {
              "≥$windThresholdDisplay ${windUnit.label} · ≤${alertConfig.tableVisibilityAlertMeters} м"
            } else {
              "ОТКЛ"
            },
            color = if (alertConfig.tableAlertsEnabled) palette.metarAmber else palette.textMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        // Master Switch Card
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.panelSurface)
            .border(1.dp, palette.border)
            .padding(12.dp),
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
              text = "Подсветка порогов в таблице METAR/TAF",
              color = palette.textPrimary,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Выделяет строки красной рамкой и меткой ⚠ при превышении заданных значений.",
              color = palette.textSecondary,
              fontSize = 11.5.sp,
            )
          }
          Switch(
            checked = alertConfig.tableAlertsEnabled,
            onCheckedChange = { enabled ->
              onUpdateAlertConfig(alertConfig.copy(tableAlertsEnabled = enabled))
            },
            modifier = Modifier.testTag("thresholds_enabled_switch"),
          )
        }

        // Wind Speed Threshold Card
        Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("threshold_wind_card")
            .background(palette.panelSurface)
            .border(1.dp, palette.border)
            .padding(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Air,
                contentDescription = "Порог скорости ветра",
                tint = palette.metarAmber,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = "Порог сильного ветра / порывов",
                  color = palette.textPrimary,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Срабатывает при ветре или порыве ≥ порога",
                  color = palette.textSecondary,
                  fontSize = 11.sp,
                )
              }
            }
            Text(
              text = "$windThresholdDisplay ${windUnit.label} (${alertConfig.tableWindAlertKt} kt)",
              color = palette.metarAmber,
              fontSize = 13.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.ExtraBold,
              modifier = Modifier.testTag("threshold_wind_value_label"),
            )
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            listOf(10, 15, 20, 25, 30).forEach { presetKt ->
              val selected = alertConfig.tableWindAlertKt == presetKt
              val disp = UnitConverter.formatWindSpeedValue(presetKt, windUnit)
              ThresholdPresetChip(
                text = "$disp ${windUnit.label}",
                selected = selected,
                testTag = "threshold_wind_chip_$presetKt",
                onClick = { updateWindKt(presetKt) },
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            IconButton(
              onClick = { updateWindKt(alertConfig.tableWindAlertKt - 1) },
              modifier = Modifier
                .size(48.dp)
                .testTag("threshold_wind_minus")
                .background(palette.buttonBackground, RoundedCornerShape(4.dp))
                .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
            ) {
              Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Уменьшить порог ветра",
                tint = palette.textPrimary,
              )
            }

            Slider(
              value = alertConfig.tableWindAlertKt.toFloat(),
              onValueChange = { v -> updateWindKt(v.roundToInt()) },
              valueRange = 5f..80f,
              modifier = Modifier
                .weight(1f)
                .testTag("threshold_wind_slider"),
            )

            IconButton(
              onClick = { updateWindKt(alertConfig.tableWindAlertKt + 1) },
              modifier = Modifier
                .size(48.dp)
                .testTag("threshold_wind_plus")
                .background(palette.buttonBackground, RoundedCornerShape(4.dp))
                .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Увеличить порог ветра",
                tint = palette.textPrimary,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = windInputText,
              onValueChange = { txt -> windInputText = txt.filter { it.isDigit() }.take(3) },
              label = { Text("Точный порог (${windUnit.label})") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier
                .weight(1f)
                .testTag("threshold_wind_input"),
            )
            Button(
              onClick = {
                val parsed = windInputText.toIntOrNull()
                if (parsed != null) {
                  updateWindKt(toKnotsFromDisplay(parsed))
                }
              },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("threshold_wind_apply"),
            ) {
              Text("Применить")
            }
          }
        }

        // Visibility Threshold Card
        Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("threshold_visibility_card")
            .background(palette.panelSurface)
            .border(1.dp, palette.border)
            .padding(12.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Visibility,
                contentDescription = "Порог видимости",
                tint = palette.metarAmber,
                modifier = Modifier.size(20.dp),
              )
              Column {
                Text(
                  text = "Порог ограниченной видимости (MOR)",
                  color = palette.textPrimary,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Срабатывает при видимости ≤ порога (м)",
                  color = palette.textSecondary,
                  fontSize = 11.sp,
                )
              }
            }
            Text(
              text = "≤ ${alertConfig.tableVisibilityAlertMeters} м",
              color = palette.metarAmber,
              fontSize = 13.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.ExtraBold,
              modifier = Modifier.testTag("threshold_vis_value_label"),
            )
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            listOf(800, 1500, 3000, 5000, 8000).forEach { presetMeters ->
              val selected = alertConfig.tableVisibilityAlertMeters == presetMeters
              ThresholdPresetChip(
                text = "$presetMeters м",
                selected = selected,
                testTag = "threshold_vis_chip_$presetMeters",
                onClick = { updateVisibilityMeters(presetMeters) },
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            IconButton(
              onClick = { updateVisibilityMeters(alertConfig.tableVisibilityAlertMeters - 100) },
              modifier = Modifier
                .size(48.dp)
                .testTag("threshold_vis_minus")
                .background(palette.buttonBackground, RoundedCornerShape(4.dp))
                .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
            ) {
              Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = "Уменьшить порог видимости",
                tint = palette.textPrimary,
              )
            }

            Slider(
              value = alertConfig.tableVisibilityAlertMeters.toFloat(),
              onValueChange = { v ->
                val snapped = ((v / 100f).roundToInt() * 100).coerceIn(200, 9999)
                updateVisibilityMeters(snapped)
              },
              valueRange = 200f..9999f,
              modifier = Modifier
                .weight(1f)
                .testTag("threshold_vis_slider"),
            )

            IconButton(
              onClick = { updateVisibilityMeters(alertConfig.tableVisibilityAlertMeters + 100) },
              modifier = Modifier
                .size(48.dp)
                .testTag("threshold_vis_plus")
                .background(palette.buttonBackground, RoundedCornerShape(4.dp))
                .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Увеличить порог видимости",
                tint = palette.textPrimary,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = visInputText,
              onValueChange = { txt -> visInputText = txt.filter { it.isDigit() }.take(4) },
              label = { Text("Точный порог видимости (м)") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              modifier = Modifier
                .weight(1f)
                .testTag("threshold_vis_input"),
            )
            Button(
              onClick = {
                val parsed = visInputText.toIntOrNull()
                if (parsed != null) {
                  updateVisibilityMeters(parsed)
                }
              },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("threshold_vis_apply"),
            ) {
              Text("Применить")
            }
          }
        }

        // Live Preview Card against current station's METAR & TAF
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("threshold_live_preview_card")
            .background(palette.panelSurface)
            .border(1.dp, palette.borderStrong)
            .padding(12.dp),
        ) {
          Text(
            text = "ПРОВЕРКА ПО ТЕКУЩЕЙ ПОГОДЕ · ${station.icao}",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
          )
          val curWindDisp = UnitConverter.formatWindSpeedValue(currentPeakWindKt, windUnit)
          val curVisText = when {
            weather.isCavok -> "CAVOK (≥ 10 км)"
            weather.visibilityMeters != null -> "${weather.visibilityMeters} м"
            else -> "нет данных"
          }
          PreviewStatusRow(
            label = "METAR Ветер ($curWindDisp ${windUnit.label})",
            triggered = metarWindTriggered,
            triggerText = "ТРЕВОГА (≥ $windThresholdDisplay ${windUnit.label})",
            okText = "В норме",
          )
          HorizontalDivider(color = palette.border)
          PreviewStatusRow(
            label = "METAR Видимость ($curVisText)",
            triggered = metarVisTriggered,
            triggerText = "ТРЕВОГА (≤ ${alertConfig.tableVisibilityAlertMeters} м)",
            okText = "В норме",
          )
          HorizontalDivider(color = palette.border)
          val tafAlertTotal = tafWindTriggeredCount + tafVisTriggeredCount
          PreviewStatusRow(
            label = "TAF Прогноз (${decodedTafPreview.periods.size} пер.)",
            triggered = alertConfig.tableAlertsEnabled && tafAlertTotal > 0,
            triggerText = "Срабатываний в TAF: $tafAlertTotal",
            okText = "Опасных периодов нет",
          )
        }

        // Bottom Actions Row
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = {
              onUpdateAlertConfig(
                alertConfig.copy(
                  tableAlertsEnabled = true,
                  tableWindAlertKt = 20,
                  tableVisibilityAlertMeters = 3000,
                ),
              )
            },
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("thresholds_reset_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Restore,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Сбросить (20 kt / 3000 м)", fontSize = 12.sp)
          }

          Button(
            onClick = onBack,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("thresholds_done_button"),
          ) {
            Text("Готово")
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
      }
    }
  }
}

@Composable
private fun ThresholdPresetChip(
  text: String,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .testTag(testTag)
      .clip(RoundedCornerShape(4.dp))
      .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else palette.border,
        shape = RoundedCornerShape(4.dp),
      )
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(
      text = text,
      color = if (selected) palette.metarAmber else palette.textPrimary,
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
    )
  }
}

@Composable
private fun PreviewStatusRow(
  label: String,
  triggered: Boolean,
  triggerText: String,
  okText: String,
) {
  val palette = LocalMeteoPalette.current
  val statusColor = if (triggered) palette.tailwindNegative else palette.headwindPositive
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      color = palette.textPrimary,
      fontSize = 12.5.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.weight(1f),
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = if (triggered) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
        contentDescription = if (triggered) triggerText else okText,
        tint = statusColor,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = if (triggered) triggerText else okText,
        color = statusColor,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
