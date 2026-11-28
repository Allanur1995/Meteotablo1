package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutoRefreshInterval
import com.example.diagnostics.CrashReporter
import com.example.domain.UnitConverter
import com.example.model.AircraftTypePreset
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.UnitSystem
import com.example.model.WeatherAlertConfig
import com.example.model.WindSpeedUnit
import com.example.ui.components.RunwayOrientationWindVisualizer
import com.example.ui.components.calculateRunwayWindComponents
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  station: AirportStation,
  weather: DecodedWeather,
  primaryRunwayCalc: RunwayCalculation,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isNightMode: Boolean,
  isFullscreen: Boolean,
  alertConfig: WeatherAlertConfig = WeatherAlertConfig(),
  autoRefreshEnabled: Boolean = true,
  autoRefreshIntervalMinutes: Int = AutoRefreshInterval.ONE_MIN.minutes,
  onBack: () -> Unit,
  onSelectUnitSystem: (UnitSystem) -> Unit,
  onSelectWindUnit: (WindSpeedUnit) -> Unit,
  onSelectPressureUnit: (PressureUnit) -> Unit,
  onToggleNight: () -> Unit,
  onSetNightMode: (Boolean) -> Unit = { desired -> if (desired != isNightMode) onToggleNight() },
  onToggleFullscreen: () -> Unit,
  onToggleAutoRefresh: (Boolean) -> Unit = {},
  onSelectAutoRefreshInterval: (Int) -> Unit = {},
  onUpdateAlertConfig: (WeatherAlertConfig) -> Unit = {},
  onOpenServerSettings: () -> Unit,
  onOpenAlertsSettings: () -> Unit,
  onOpenThresholdConfig: () -> Unit = {},
  onOpenHistory24h: () -> Unit = {},
  onOpenExportDialog: () -> Unit = {},
  onOpenDecoder: () -> Unit = {},
  onOpenPressureUtility: () -> Unit = {},
  onOpenManageRunways: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  val activeSystem = UnitConverter.resolveUnitSystem(windUnit, pressureUnit)
  val activeBadge = activeSystem?.shortTag ?: "CUSTOM · ${windUnit.label} / ${pressureUnit.label}"

  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  val windVal = UnitConverter.formatWindSpeedValue(weather.wind.speedKt, windUnit)
  val gustText = weather.wind.gustKt?.let { g ->
    " (порывы ${UnitConverter.formatWindSpeedValue(g, windUnit)} ${windUnit.label})"
  }.orEmpty()
  val previewWindText = if (weather.wind.isCalm) {
    "Штиль (0 ${windUnit.label})"
  } else {
    "$windVal ${windUnit.label}$gustText"
  }
  val (qnhValue, qnhUnitLabel) = UnitConverter.formatPressurePrimary(
    hpa = weather.qnhHpa,
    inHg = weather.qnhInHg,
    unit = pressureUnit,
  )
  val previewPressureText = "$qnhValue $qnhUnitLabel"
  val crosswindConverted = UnitConverter.convertWindFromKnots(
    abs(primaryRunwayCalc.crosswindKt),
    windUnit,
  ).roundToInt()
  val headwindConverted = UnitConverter.convertWindFromKnots(
    abs(primaryRunwayCalc.headwindKt),
    windUnit,
  ).roundToInt()
  val previewRunwayText = "ВПП ${primaryRunwayCalc.designator}: " +
    "боковой $crosswindConverted ${windUnit.label} · " +
    "встр./попутн. $headwindConverted ${windUnit.label}"

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 980.dp)
        .align(Alignment.TopCenter),
    ) {
      // Top Navigation Bar (pinned)
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
              .testTag("settings_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад к табло",
              tint = palette.textPrimary,
            )
          }
          Column {
            Text(
              text = "Настройки · ${AuthorBrand.APP_TITLE}",
              color = palette.textPrimary,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Единицы измерения погоды, экран и диагностика",
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
          }
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(palette.buttonActiveBackground)
            .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = activeBadge,
            color = palette.metarAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.testTag("settings_active_system_badge"),
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

      // Section 1: Metric vs Imperial Unit System Toggle
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("settings_units_card")
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
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.Speed,
              contentDescription = "Система единиц",
              tint = palette.metarAmber,
              modifier = Modifier.size(20.dp),
            )
            Column {
              Text(
                text = "Система единиц измерения",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = when (activeSystem) {
                  UnitSystem.METRIC -> "Активна метрическая система (hPa, м, м/с)"
                  UnitSystem.IMPERIAL -> "Активна имперская система (inHg, ft, knots)"
                  null -> "Пользовательская комбинация (${windUnit.label}, ${pressureUnit.label})"
                },
                color = palette.textSecondary,
                fontSize = 11.5.sp,
              )
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = if (activeSystem == UnitSystem.IMPERIAL) "Imperial" else "Metric",
              color = palette.metarAmber,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
            )
            Switch(
              checked = activeSystem == UnitSystem.IMPERIAL,
              onCheckedChange = { useImperial ->
                onSelectUnitSystem(if (useImperial) UnitSystem.IMPERIAL else UnitSystem.METRIC)
              },
              modifier = Modifier.testTag("settings_metric_imperial_switch"),
            )
          }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
          val sideBySide = maxWidth >= 520.dp
          if (sideBySide) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              UnitSystemOptionCard(
                system = UnitSystem.METRIC,
                title = "Метрическая (Metric)",
                unitBadge = "hPa · м · м/с",
                description = "Давление QNH/QFE в гектопаскалях (hPa), ВНГО и высоты в метрах (м), ветер в м/с.",
                selected = activeSystem == UnitSystem.METRIC,
                onClick = { onSelectUnitSystem(UnitSystem.METRIC) },
                modifier = Modifier.weight(1f),
              )
              UnitSystemOptionCard(
                system = UnitSystem.IMPERIAL,
                title = "Имперская (Imperial)",
                unitBadge = "inHg · ft · knots",
                description = "Давление QNH/QFE в дюймах (inHg), ВНГО и высоты в футах (ft), ветер в узлах (kt).",
                selected = activeSystem == UnitSystem.IMPERIAL,
                onClick = { onSelectUnitSystem(UnitSystem.IMPERIAL) },
                modifier = Modifier.weight(1f),
              )
            }
          } else {
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              UnitSystemOptionCard(
                system = UnitSystem.METRIC,
                title = "Метрическая (Metric)",
                unitBadge = "hPa · м · м/с",
                description = "Давление QNH/QFE в гектопаскалях (hPa), ВНГО и высоты в метрах (м), ветер в м/с.",
                selected = activeSystem == UnitSystem.METRIC,
                onClick = { onSelectUnitSystem(UnitSystem.METRIC) },
                modifier = Modifier.fillMaxWidth(),
              )
              UnitSystemOptionCard(
                system = UnitSystem.IMPERIAL,
                title = "Имперская (Imperial)",
                unitBadge = "inHg · ft · knots",
                description = "Давление QNH/QFE в дюймах (inHg), ВНГО и высоты в футах (ft), ветер в узлах (kt).",
                selected = activeSystem == UnitSystem.IMPERIAL,
                onClick = { onSelectUnitSystem(UnitSystem.IMPERIAL) },
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }

      // Section 2: Live Weather Display Preview
      Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(12.dp),
      ) {
        Text(
          text = "ПРЕДПРОСМОТР ТАБЛО · ${station.icao} (${station.city})",
          color = palette.textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.5.sp,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .background(palette.fieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
              .padding(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Air,
                contentDescription = null,
                tint = palette.metarAmber,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Ветер",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = previewWindText,
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.testTag("settings_preview_wind"),
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .background(palette.fieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
              .padding(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.Compress,
                contentDescription = null,
                tint = palette.metarAmber,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Давление QNH",
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = previewPressureText,
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.testTag("settings_preview_pressure"),
            )
          }
        }
        Text(
          text = previewRunwayText,
          color = palette.textSecondary,
          fontSize = 12.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.testTag("settings_preview_runway"),
        )
      }

      // Section 3: Fine-Grained Unit Overrides
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(12.dp),
      ) {
        Text(
          text = "Тонкая настройка отдельных параметров",
          color = palette.textPrimary,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
        )

        Text(
          text = "Единица скорости ветра:",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          val windLabels = mapOf(
            WindSpeedUnit.MPS to "м/с (m/s)",
            WindSpeedUnit.KT to "kt (knots)",
            WindSpeedUnit.KPH to "км/ч (km/h)",
          )
          listOf(WindSpeedUnit.MPS, WindSpeedUnit.KT, WindSpeedUnit.KPH).forEach { unit ->
            val selected = unit == windUnit
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_wind_unit_${unit.name}")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                .clickable { onSelectWindUnit(unit) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
              Text(
                text = windLabels[unit] ?: unit.label,
                color = if (selected) palette.metarAmber else palette.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = "Единица атмосферного давления (QNH / QFE):",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          PressureUnit.entries.forEach { unit ->
            val selected = unit == pressureUnit
            val label = if (unit == PressureUnit.HPA) "hPa (гПа · метрич.)" else "inHg (дм · импер.)"
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_pressure_unit_${unit.name}")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                .clickable { onSelectPressureUnit(unit) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
              Text(
                text = label,
                color = if (selected) palette.metarAmber else palette.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }

      // Section 3.2: Aircraft Type & Maximum Crosswind Threshold Panel
      val calcComp = remember(primaryRunwayCalc.headingDeg, weather.wind) {
        calculateRunwayWindComponents(primaryRunwayCalc.headingDeg, weather.wind)
      }
      val crossExceeded = alertConfig.isCrosswindExceeded(
        crosswindKt = primaryRunwayCalc.crosswindKt,
        gustCrosswindKt = calcComp.gustCrosswindKt,
      )
      var limitInputText by remember(alertConfig.maxCrosswindKt) {
        mutableStateOf(alertConfig.maxCrosswindKt.toString())
      }
      var customAircraftText by remember(alertConfig.customAircraftName) {
        mutableStateOf(alertConfig.customAircraftName)
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("settings_crosswind_limit_card")
          .background(palette.panelSurface)
          .border(
            width = if (crossExceeded) 1.5.dp else 1.dp,
            color = if (crossExceeded) palette.tailwindNegative else palette.border,
          )
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
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.Flight,
              contentDescription = "Лимит бокового ветра по типу ВС",
              tint = if (crossExceeded) palette.tailwindNegative else palette.metarAmber,
              modifier = Modifier.size(20.dp),
            )
            Column {
              Text(
                text = "Ограничение бокового ветра (Тип ВС)",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Предупреждение при превышении боковой составляющей для ${alertConfig.resolvedAircraftName()}",
                color = palette.textSecondary,
                fontSize = 11.5.sp,
              )
            }
          }
          Switch(
            checked = alertConfig.crosswindWarningEnabled,
            onCheckedChange = { enabled ->
              onUpdateAlertConfig(alertConfig.copy(crosswindWarningEnabled = enabled))
            },
            modifier = Modifier.testTag("settings_crosswind_switch"),
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          AircraftTypePreset.entries.forEach { preset ->
            val selected = alertConfig.aircraftTypeId == preset.id
            val presetDisp = UnitConverter.formatWindSpeedValue(preset.defaultMaxCrosswindKt, windUnit)
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .heightIn(min = 42.dp)
                .testTag("settings_aircraft_preset_${preset.id}")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) palette.metarAmber else palette.border,
                  shape = RoundedCornerShape(4.dp),
                )
                .clickable {
                  val nextLimit = if (preset == AircraftTypePreset.CUSTOM) {
                    alertConfig.maxCrosswindKt
                  } else {
                    preset.defaultMaxCrosswindKt
                  }
                  onUpdateAlertConfig(
                    alertConfig.copy(
                      aircraftTypeId = preset.id,
                      maxCrosswindKt = nextLimit,
                    ),
                  )
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text(
                text = if (preset == AircraftTypePreset.CUSTOM) {
                  preset.displayName
                } else {
                  "${preset.displayName} (${presetDisp} ${windUnit.label})"
                },
                color = if (selected) palette.metarAmber else palette.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              )
            }
          }
        }

        if (alertConfig.aircraftTypeId == AircraftTypePreset.CUSTOM.id) {
          OutlinedTextField(
            value = customAircraftText,
            onValueChange = { raw ->
              customAircraftText = raw.take(28)
              onUpdateAlertConfig(alertConfig.copy(customAircraftName = customAircraftText))
            },
            label = { Text("Название вашего ВС", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("settings_custom_aircraft_input"),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = {
              val next = (alertConfig.maxCrosswindKt - 2).coerceIn(5, 60)
              onUpdateAlertConfig(alertConfig.copy(maxCrosswindKt = next))
            },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("settings_crosswind_minus"),
          ) {
            Text("-2 kt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedTextField(
            value = limitInputText,
            onValueChange = { raw ->
              val digits = raw.filter { it.isDigit() }.take(2)
              limitInputText = digits
              val parsed = digits.toIntOrNull()
              if (parsed != null && parsed in 5..60) {
                onUpdateAlertConfig(alertConfig.copy(maxCrosswindKt = parsed))
              }
            },
            label = {
              val eqDisp = UnitConverter.formatWindSpeedValue(alertConfig.maxCrosswindKt, windUnit)
              Text("Макс. бок. ветер (kt · ≈ $eqDisp ${windUnit.label})", fontSize = 10.5.sp)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
              .weight(1f)
              .testTag("settings_crosswind_limit_input"),
          )

          OutlinedButton(
            onClick = {
              val next = (alertConfig.maxCrosswindKt + 2).coerceIn(5, 60)
              onUpdateAlertConfig(alertConfig.copy(maxCrosswindKt = next))
            },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("settings_crosswind_plus"),
          ) {
            Text("+2 kt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }

        val currentCrossDisp = UnitConverter.convertWindFromKnots(primaryRunwayCalc.crosswindKt, windUnit)
        val limitDisp = UnitConverter.convertWindFromKnots(alertConfig.maxCrosswindKt.toDouble(), windUnit)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_crosswind_preview_status")
            .semantics(mergeDescendants = true) {}
            .background(
              if (crossExceeded) palette.tailwindNegative.copy(alpha = 0.14f) else palette.headwindPositive.copy(alpha = 0.10f),
              RoundedCornerShape(4.dp),
            )
            .border(
              1.dp,
              if (crossExceeded) palette.tailwindNegative else palette.headwindPositive.copy(alpha = 0.55f),
              RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
          Icon(
            imageVector = if (crossExceeded) Icons.Filled.Warning else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (crossExceeded) palette.tailwindNegative else palette.headwindPositive,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = if (crossExceeded) {
              String.format(
                Locale.US,
                "ПРЕВЫШЕНИЕ на ВПП %s: боковой %.1f %s > лимита %.0f %s (%s)",
                primaryRunwayCalc.designator,
                currentCrossDisp,
                windUnit.label,
                limitDisp,
                windUnit.label,
                alertConfig.resolvedAircraftName(),
              )
            } else if (!alertConfig.crosswindWarningEnabled) {
              "Контроль ограничения бокового ветра отключён"
            } else {
              String.format(
                Locale.US,
                "В норме на ВПП %s: боковой %.1f %s ≤ лимита %.0f %s",
                primaryRunwayCalc.designator,
                currentCrossDisp,
                windUnit.label,
                limitDisp,
                windUnit.label,
              )
            },
            color = if (crossExceeded) palette.tailwindNegative else palette.headwindPositive,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
          )
        }

        RunwayOrientationWindVisualizer(
          runwayHeadingDeg = primaryRunwayCalc.headingDeg,
          runwayDesignator = primaryRunwayCalc.designator,
          wind = weather.wind,
          windUnit = windUnit,
          maxCrosswindKt = if (alertConfig.crosswindWarningEnabled) alertConfig.maxCrosswindKt else null,
          aircraftLabel = alertConfig.resolvedAircraftName(),
          onToggleUnit = {
            val nextUnit = when (windUnit) {
              WindSpeedUnit.KT -> WindSpeedUnit.MPS
              WindSpeedUnit.MPS -> WindSpeedUnit.KPH
              WindSpeedUnit.KPH -> WindSpeedUnit.KT
            }
            onSelectWindUnit(nextUnit)
          },
        )
      }

      // Section 3.5: Automatic Weather Refresh (5, 15, 30 min)
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("settings_auto_refresh_card")
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
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.Sync,
              contentDescription = "Автоматическое обновление данных",
              tint = if (autoRefreshEnabled) palette.metarAmber else palette.textMuted,
              modifier = Modifier.size(20.dp),
            )
            Column {
              Text(
                text = "Автообновление метеоданных",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (autoRefreshEnabled) {
                  val desc = AutoRefreshInterval.fromMinutes(autoRefreshIntervalMinutes).descriptionLabel
                  "Активно: загрузка METAR/TAF каждые $desc"
                } else {
                  "Отключено — обновляйте кнопкой «Обновить» на верхней панели"
                },
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.testTag("settings_auto_refresh_status"),
              )
            }
          }

          Switch(
            checked = autoRefreshEnabled,
            onCheckedChange = onToggleAutoRefresh,
            modifier = Modifier.testTag("settings_auto_refresh_switch"),
          )
        }

        Text(
          text = "Интервал автоматического обновления:",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          AutoRefreshInterval.entries.forEach { interval ->
            val selected = interval.minutes == autoRefreshIntervalMinutes
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("settings_auto_refresh_interval_${interval.minutes}")
                .clip(RoundedCornerShape(4.dp))
                .background(
                  when {
                    selected && autoRefreshEnabled -> palette.buttonActiveBackground
                    selected -> palette.buttonBackground
                    else -> palette.secondaryFieldBackground
                  },
                )
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = when {
                    selected && autoRefreshEnabled -> palette.metarAmber
                    selected -> palette.borderStrong
                    else -> palette.border
                  },
                  shape = RoundedCornerShape(4.dp),
                )
                .clickable { onSelectAutoRefreshInterval(interval.minutes) }
                .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
              Text(
                text = interval.label,
                color = when {
                  selected && autoRefreshEnabled -> palette.metarAmber
                  selected -> palette.textPrimary
                  else -> palette.textSecondary
                },
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }

      // Section 4: Cockpit UI Theme (Light / Dark)
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("settings_theme_card")
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
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = if (isNightMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
              contentDescription = "Тема интерфейса кокпита",
              tint = palette.metarAmber,
              modifier = Modifier.size(20.dp),
            )
            Column {
              Text(
                text = "Тема интерфейса (Кокпит)",
                color = palette.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (isNightMode) {
                  "Активен ночной кокпит (Dark Cockpit · низкая яркость)"
                } else {
                  "Активен дневной кокпит (Light Cockpit · высокий контраст)"
                },
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.testTag("settings_theme_status"),
              )
            }
          }

          Switch(
            checked = isNightMode,
            onCheckedChange = { onSetNightMode(it) },
            modifier = Modifier.testTag("settings_night_mode_switch"),
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          CockpitThemeOptionCard(
            title = "Дневной (Light)",
            subtitle = "Светлая панель",
            bgSwatch = Color(0xFFF3F5F8),
            accentSwatch = Color(0xFFC85A00),
            textSwatch = Color(0xFF0D1117),
            selected = !isNightMode,
            testTag = "settings_theme_light",
            onClick = { onSetNightMode(false) },
            modifier = Modifier.weight(1f),
          )
          CockpitThemeOptionCard(
            title = "Ночной (Dark)",
            subtitle = "Тёмный кокпит",
            bgSwatch = Color(0xFF111A27),
            accentSwatch = Color(0xFFFF981A),
            textSwatch = Color(0xFFEBF2FA),
            selected = isNightMode,
            testTag = "settings_theme_dark",
            onClick = { onSetNightMode(true) },
            modifier = Modifier.weight(1f),
          )
        }
      }

      // Section 5: Display & Operational Preferences
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(12.dp),
      ) {
        Text(
          text = "Отображение табло и сервисы",
          color = palette.textPrimary,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
        )

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
              imageVector = Icons.Filled.Fullscreen,
              contentDescription = null,
              tint = palette.textSecondary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Полноэкранный режим",
              color = palette.textPrimary,
              fontSize = 13.sp,
            )
          }
          Switch(
            checked = isFullscreen,
            onCheckedChange = { onToggleFullscreen() },
            modifier = Modifier.testTag("settings_fullscreen_switch"),
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = onOpenServerSettings,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("settings_open_server_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Dns,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Сервер METAR", fontSize = 12.sp)
          }
          OutlinedButton(
            onClick = onOpenAlertsSettings,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("settings_open_alerts_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.NotificationsActive,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Оповещения", fontSize = 12.sp)
          }
        }

        val thresholdWindDisp = UnitConverter.formatWindSpeedValue(alertConfig.tableWindAlertKt, windUnit)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("settings_open_thresholds_button")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.secondaryFieldBackground)
            .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
            .clickable(onClick = onOpenThresholdConfig)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(
              imageVector = Icons.Filled.Tune,
              contentDescription = "Пороги тревоги в таблице METAR/TAF",
              tint = palette.metarAmber,
              modifier = Modifier.size(18.dp),
            )
            Column {
              Text(
                text = "Пороги тревоги в таблице METAR/TAF",
                color = palette.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = if (alertConfig.tableAlertsEnabled) {
                  "Ветер ≥ $thresholdWindDisp ${windUnit.label} · Видимость ≤ ${alertConfig.tableVisibilityAlertMeters} м"
                } else {
                  "Визуальное выделение порогов отключено"
                },
                color = palette.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
          Text(
            text = "Настроить",
            color = palette.metarAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
        }

        OutlinedButton(
          onClick = onOpenHistory24h,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("settings_open_history_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("История METAR / TAF за 24ч и тренд QNH", fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onOpenExportDialog,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("settings_export_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("Экспорт сводки и трендов (PDF / TXT)", fontSize = 12.sp)
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = onOpenPressureUtility,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("settings_pressure_utility_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Compress,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("QNH / QFE", fontSize = 12.sp)
          }
          OutlinedButton(
            onClick = onOpenDecoder,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("settings_decoder_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Description,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("Декодер METAR", fontSize = 12.sp)
          }
        }

        OutlinedButton(
          onClick = onOpenManageRunways,
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("settings_manage_runways_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.FlightLand,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("ВПП аэродрома (${station.icao} · ${station.runways.size})", fontSize = 12.sp)
        }
      }

      // Section 5: Discreet App Owner & Crashlytics Diagnostics Card
      val diag = remember(windUnit, pressureUnit) { CrashReporter.getDiagnosticsSnapshot(context) }
      Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.statusBarBg)
          .border(1.dp, palette.border)
          .padding(12.dp)
          .testTag("settings_owner_info_card"),
      ) {
        Text(
          text = "${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · Авиационное метеотабло",
          color = palette.textSecondary,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = "ID пакета: ${context.packageName}",
          color = palette.textMuted,
          fontSize = 10.5.sp,
          fontFamily = FontFamily.Monospace,
        )
        Text(
          text = if (diag.isCrashlyticsActive) {
            "Firebase Crashlytics: активен · этап: ${diag.currentLaunchStage} · событий: ${diag.breadcrumbCount}"
          } else {
            "Диагностика сбоев: локальный режим · этап: ${diag.currentLaunchStage}"
          },
          color = if (diag.isCrashlyticsActive) palette.metarAmber else palette.textMuted,
          fontSize = 10.5.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.testTag("settings_crashlytics_status"),
        )
      }
      }
    }
  }
}

@Composable
private fun UnitSystemOptionCard(
  system: UnitSystem,
  title: String,
  unitBadge: String,
  description: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .heightIn(min = 96.dp)
      .testTag("settings_unit_system_${system.name}")
      .clip(RoundedCornerShape(6.dp))
      .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) palette.borderStrong else palette.border,
        shape = RoundedCornerShape(6.dp),
      )
      .clickable(onClick = onClick)
      .padding(12.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (selected) palette.metarAmber else palette.textMuted,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = title,
          color = palette.textPrimary,
          fontSize = 13.5.sp,
          fontWeight = FontWeight.Bold,
        )
      }

      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(3.dp))
          .background(palette.fieldBackground)
          .border(0.8.dp, if (selected) palette.metarAmber else palette.border, RoundedCornerShape(3.dp))
          .padding(horizontal = 7.dp, vertical = 2.dp),
      ) {
        Text(
          text = unitBadge,
          color = if (selected) palette.metarAmber else palette.textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = FontFamily.Monospace,
        )
      }
    }

    Text(
      text = description,
      color = palette.textSecondary,
      fontSize = 11.5.sp,
      lineHeight = 15.sp,
    )
  }
}

@Composable
private fun CockpitThemeOptionCard(
  title: String,
  subtitle: String,
  bgSwatch: Color,
  accentSwatch: Color,
  textSwatch: Color,
  selected: Boolean,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .heightIn(min = 72.dp)
      .testTag(testTag)
      .clip(RoundedCornerShape(6.dp))
      .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
      .border(
        width = if (selected) 1.5.dp else 1.dp,
        color = if (selected) palette.metarAmber else palette.border,
        shape = RoundedCornerShape(6.dp),
      )
      .clickable(onClick = onClick)
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
      ) {
        Icon(
          imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
          contentDescription = null,
          tint = if (selected) palette.metarAmber else palette.textMuted,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = title,
          color = palette.textPrimary,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.Bold,
        )
      }

      // Cockpit palette swatch preview
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .background(bgSwatch, RoundedCornerShape(3.dp))
          .border(1.dp, palette.border, RoundedCornerShape(3.dp))
          .padding(horizontal = 6.dp, vertical = 3.dp),
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .background(accentSwatch, RoundedCornerShape(2.dp)),
        )
        Box(
          modifier = Modifier
            .size(8.dp)
            .background(textSwatch, RoundedCornerShape(2.dp)),
        )
      }
    }

    Text(
      text = subtitle,
      color = palette.textSecondary,
      fontSize = 11.sp,
    )
  }
}
