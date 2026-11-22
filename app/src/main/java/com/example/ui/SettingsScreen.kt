package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diagnostics.CrashReporter
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.UnitSystem
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
  station: AirportStation,
  weather: DecodedWeather,
  primaryRunwayCalc: RunwayCalculation,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isNightMode: Boolean,
  isFullscreen: Boolean,
  onBack: () -> Unit,
  onSelectUnitSystem: (UnitSystem) -> Unit,
  onSelectWindUnit: (WindSpeedUnit) -> Unit,
  onSelectPressureUnit: (PressureUnit) -> Unit,
  onToggleNight: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onOpenServerSettings: () -> Unit,
  onOpenAlertsSettings: () -> Unit,
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
                  UnitSystem.METRIC -> "Активна метрическая система (hPa, м/с)"
                  UnitSystem.IMPERIAL -> "Активна имперская система (inHg, knots)"
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
                unitBadge = "hPa · м/с",
                description = "Давление QNH/QFE в гектопаскалях (hPa), скорость ветра и порывов в м/с.",
                selected = activeSystem == UnitSystem.METRIC,
                onClick = { onSelectUnitSystem(UnitSystem.METRIC) },
                modifier = Modifier.weight(1f),
              )
              UnitSystemOptionCard(
                system = UnitSystem.IMPERIAL,
                title = "Имперская (Imperial)",
                unitBadge = "inHg · knots",
                description = "Давление QNH/QFE в дюймах рт. ст. (inHg), скорость ветра и порывов в узлах (kt).",
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
                unitBadge = "hPa · м/с",
                description = "Давление QNH/QFE в гектопаскалях (hPa), скорость ветра и порывов в м/с.",
                selected = activeSystem == UnitSystem.METRIC,
                onClick = { onSelectUnitSystem(UnitSystem.METRIC) },
                modifier = Modifier.fillMaxWidth(),
              )
              UnitSystemOptionCard(
                system = UnitSystem.IMPERIAL,
                title = "Имперская (Imperial)",
                unitBadge = "inHg · knots",
                description = "Давление QNH/QFE в дюймах рт. ст. (inHg), скорость ветра и порывов в узлах (kt).",
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

      // Section 4: Display & Operational Preferences
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
              imageVector = Icons.Filled.DarkMode,
              contentDescription = null,
              tint = palette.textSecondary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "Ночной режим индикации",
              color = palette.textPrimary,
              fontSize = 13.sp,
            )
          }
          Switch(
            checked = isNightMode,
            onCheckedChange = { onToggleNight() },
            modifier = Modifier.testTag("settings_night_mode_switch"),
          )
        }

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
          text = "${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · Автор: ${AuthorBrand.AUTHOR_NAME} (${AuthorBrand.AUTHOR_HANDLE})",
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
