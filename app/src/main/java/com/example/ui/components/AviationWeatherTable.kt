package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.TafParser
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedTaf
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.TafChangeType
import com.example.model.TafForecastPeriod
import com.example.model.UnitSystem
import com.example.model.WeatherAlertConfig
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendDirection
import com.example.model.WindTrendSummary
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.roundToInt

enum class AviationTableTab(val shortLabel: String) {
  ALL("Все (METAR + TAF)"),
  METAR("METAR"),
  TAF("TAF"),
}

private enum class RowSeverity {
  NORMAL,
  CAUTION,
  ALERT,
}

private data class MetarTableRowModel(
  val icon: ImageVector,
  val label: String,
  val subLabel: String,
  val primaryValue: String,
  val secondaryDetails: String,
  val rawToken: String,
  val severity: RowSeverity,
  val testTag: String,
  val alertBadgeText: String? = null,
  val alertBadgeTestTag: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AviationWeatherTable(
  station: AirportStation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  modifier: Modifier = Modifier,
  initialTab: AviationTableTab = AviationTableTab.ALL,
  windTrend: WindTrendSummary? = null,
  alertConfig: WeatherAlertConfig = WeatherAlertConfig(),
  onToggleWindUnit: (() -> Unit)? = null,
  onTogglePressureUnit: (() -> Unit)? = null,
  onSelectUnitSystem: ((UnitSystem) -> Unit)? = null,
  onOpenThresholdConfig: (() -> Unit)? = null,
) {
  val palette = LocalMeteoPalette.current
  var activeTab by remember(initialTab) { mutableStateOf(initialTab) }

  val flightCategory = remember(weather) {
    AirportMapProjection.evaluateFlightCategory(weather)
  }
  val metarRows = remember(station, weather, windUnit, pressureUnit, alertConfig) {
    buildAviationMetarRows(station, weather, windUnit, pressureUnit, alertConfig)
  }
  val decodedTaf = remember(weather.rawTaf, windUnit, alertConfig, pressureUnit) {
    TafParser.parse(weather.rawTaf, windUnit, alertConfig, pressureUnit)
  }
  var containerWidthPx by remember { mutableIntStateOf(0) }
  val isWideLayout = with(LocalDensity.current) { containerWidthPx.toDp() >= 600.dp }

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .onSizeChanged { containerWidthPx = it.width },
  ) {
    // Operational Header Strip: Flight Category + Quick Trend + Unit Toggles + Section Tabs
    Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("aviation_table_header")
        .background(palette.panelSurface, RoundedCornerShape(4.dp))
        .border(1.dp, palette.border, RoundedCornerShape(4.dp))
        .padding(10.dp),
    ) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        FlightCategoryChip(category = flightCategory)

        Column(modifier = Modifier.weight(1f, fill = false)) {
          Text(
            text = "${station.icao} · ${station.city.ifBlank { station.name }}",
            color = palette.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "METAR ${weather.observationTimeUtc} · TAF ${decodedTaf.validityWindow}",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
          )
        }

        if (windTrend != null && windTrend.direction != WindTrendDirection.INSUFFICIENT) {
          val trendColor = when (windTrend.direction) {
            WindTrendDirection.INCREASING -> palette.metarAmber
            WindTrendDirection.DECREASING -> palette.headwindPositive
            WindTrendDirection.STEADY -> palette.textSecondary
            WindTrendDirection.INSUFFICIENT -> palette.textMuted
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, trendColor.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
              .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Timeline,
              contentDescription = "Тренд ветра",
              tint = trendColor,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "${windTrend.direction.arrow} ${windTrend.direction.label}",
              color = trendColor,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        if (onToggleWindUnit != null) {
          UnitPillButton(
            text = windUnit.label,
            onClick = onToggleWindUnit,
            testTag = "aviation_table_wind_unit",
          )
        }
        if (onTogglePressureUnit != null) {
          UnitPillButton(
            text = pressureUnit.label,
            onClick = onTogglePressureUnit,
            testTag = "aviation_table_pressure_unit",
          )
        }
        if (onOpenThresholdConfig != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("aviation_table_thresholds_button")
              .clip(RoundedCornerShape(4.dp))
              .background(palette.buttonBackground)
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
              .clickable(onClick = onOpenThresholdConfig)
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Tune,
              contentDescription = "Настроить пороги тревоги",
              tint = palette.metarAmber,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Пороги",
              color = palette.textPrimary,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Metric <-> Imperial Segmented Toggle Component throughout the table displays
      val activeSystem = UnitConverter.resolveUnitSystem(windUnit, pressureUnit)
      if (onSelectUnitSystem != null || (onToggleWindUnit != null && onTogglePressureUnit != null)) {
        UnitSystemTableToggle(
          activeSystem = activeSystem,
          windUnit = windUnit,
          pressureUnit = pressureUnit,
          onSelectSystem = { targetSystem ->
            if (onSelectUnitSystem != null) {
              onSelectUnitSystem(targetSystem)
            } else {
              if (targetSystem == UnitSystem.METRIC) {
                if (pressureUnit != PressureUnit.HPA) onTogglePressureUnit?.invoke()
              } else {
                if (pressureUnit != PressureUnit.INHG) onTogglePressureUnit?.invoke()
              }
            }
          },
        )
      }

      // Clean Segmented Selector Tabs
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        AviationTableTab.entries.forEach { tab ->
          val selected = tab == activeTab
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("table_tab_${tab.name}")
              .clip(RoundedCornerShape(4.dp))
              .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
              .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else palette.border,
                shape = RoundedCornerShape(4.dp),
              )
              .clickable { activeTab = tab }
              .padding(horizontal = 8.dp, vertical = 10.dp),
          ) {
            Text(
              text = tab.shortLabel,
              color = if (selected) palette.textPrimary else palette.textSecondary,
              fontSize = 11.5.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }

    // Active Threshold Alert Summary Banner (METAR + TAF)
    val metarAlertLabels = remember(metarRows) {
      metarRows.mapNotNull { row ->
        row.alertBadgeText?.let { "${row.label}: $it" }
      }
    }
    val tafWindAlertCount = remember(decodedTaf) { decodedTaf.periods.count { it.isWindAlert } }
    val tafVisAlertCount = remember(decodedTaf) { decodedTaf.periods.count { it.isVisibilityAlert } }
    if (alertConfig.tableAlertsEnabled && (metarAlertLabels.isNotEmpty() || tafWindAlertCount > 0 || tafVisAlertCount > 0)) {
      val summaryParts = buildList {
        addAll(metarAlertLabels)
        if (tafWindAlertCount > 0) add("TAF ветер ($tafWindAlertCount)")
        if (tafVisAlertCount > 0) add("TAF видимость ($tafVisAlertCount)")
      }.joinToString(" · ")
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("table_active_alerts_banner")
          .background(palette.tailwindNegative.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
          .border(1.2.dp, palette.tailwindNegative, RoundedCornerShape(4.dp))
          .then(if (onOpenThresholdConfig != null) Modifier.clickable(onClick = onOpenThresholdConfig) else Modifier)
          .padding(horizontal = 10.dp, vertical = 7.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.WarningAmber,
          contentDescription = "Превышение порогов погоды",
          tint = palette.tailwindNegative,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "ТРЕВОГА ПО ПОРОГАМ: $summaryParts",
          color = palette.tailwindNegative,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.ExtraBold,
          modifier = Modifier.weight(1f),
        )
      }
    }

    // METAR Structured Row Table
    if (activeTab == AviationTableTab.ALL || activeTab == AviationTableTab.METAR) {
      MetarStructuredSection(
        station = station,
        weather = weather,
        rows = metarRows,
        flightCategory = flightCategory,
      )
    }

    // TAF Structured Forecast Table
    if (activeTab == AviationTableTab.ALL || activeTab == AviationTableTab.TAF) {
      TafStructuredSection(
        decodedTaf = decodedTaf,
        windUnit = windUnit,
        pressureUnit = pressureUnit,
        alertConfig = alertConfig,
        isWide = isWideLayout,
      )
    }
  }
}

@Composable
private fun MetarStructuredSection(
  station: AirportStation,
  weather: DecodedWeather,
  rows: List<MetarTableRowModel>,
  flightCategory: FlightCategory,
) {
  val palette = LocalMeteoPalette.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("metar_structured_table")
      .background(palette.panelSurface, RoundedCornerShape(4.dp))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp)),
  ) {
    // Section Header
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.statusBarBg)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.FlightTakeoff,
          contentDescription = "Расшифровка METAR",
          tint = palette.metarAmber,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "METAR · ${station.icao} (${weather.observationTimeUtc})",
          color = palette.textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.4.sp,
        )
      }
      Text(
        text = flightCategory.code,
        color = tableFlightCategoryColor(flightCategory),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
      )
    }

    HorizontalDivider(thickness = 1.dp, color = palette.border)

    // Table Column Header
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.secondaryFieldBackground)
        .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
      Text(
        text = "ПАРАМЕТР",
        color = palette.textSecondary,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.weight(1.05f),
      )
      Text(
        text = "ЗНАЧЕНИЕ / РАСШИФРОВКА",
        color = palette.textSecondary,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.weight(1.55f),
      )
      Text(
        text = "КОД",
        color = palette.textSecondary,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(0.65f),
      )
    }

    HorizontalDivider(thickness = 1.dp, color = palette.borderStrong)

    rows.forEachIndexed { index, row ->
      AviationMetarRowItem(
        row = row,
        isAlternate = index % 2 == 1,
      )
      if (index < rows.lastIndex) {
        HorizontalDivider(thickness = 0.8.dp, color = palette.border)
      }
    }
  }
}

@Composable
private fun AviationMetarRowItem(
  row: MetarTableRowModel,
  isAlternate: Boolean,
) {
  val palette = LocalMeteoPalette.current
  val accentColor = when (row.severity) {
    RowSeverity.ALERT -> palette.tailwindNegative
    RowSeverity.CAUTION -> palette.metarAmber
    RowSeverity.NORMAL -> Color.Transparent
  }
  val rowBackground = when {
    row.severity == RowSeverity.ALERT -> palette.tailwindNegative.copy(alpha = 0.08f)
    isAlternate -> palette.secondaryFieldBackground.copy(alpha = 0.45f)
    else -> palette.fieldBackground
  }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min)
      .testTag(row.testTag)
      .background(rowBackground),
  ) {
    // 3.dp Left-Edge Operational Status Strip
    Box(
      modifier = Modifier
        .width(3.dp)
        .fillMaxHeight()
        .background(accentColor),
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 9.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
      // Column 1: Icon + Parameter Name
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .weight(1.05f)
          .padding(end = 8.dp),
      ) {
        Icon(
          imageVector = row.icon,
          contentDescription = row.label,
          tint = when (row.severity) {
            RowSeverity.ALERT -> palette.tailwindNegative
            RowSeverity.CAUTION -> palette.metarAmber
            RowSeverity.NORMAL -> palette.metarAmber
          },
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = row.label,
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = row.subLabel,
            color = palette.textMuted,
            fontSize = 10.5.sp,
          )
        }
      }

      // Column 2: Primary Readout + Secondary Details
      Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
          .weight(1.55f)
          .padding(end = 8.dp),
      ) {
        Text(
          text = row.primaryValue,
          color = if (row.severity == RowSeverity.ALERT) palette.tailwindNegative else palette.textPrimary,
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
        )
        if (row.secondaryDetails.isNotBlank()) {
          Text(
            text = row.secondaryDetails,
            color = palette.textSecondary,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
          )
        }
        if (row.alertBadgeText != null) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .padding(top = 2.dp)
              .then(if (row.alertBadgeTestTag != null) Modifier.testTag(row.alertBadgeTestTag) else Modifier)
              .background(palette.tailwindNegative.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
              .border(0.8.dp, palette.tailwindNegative, RoundedCornerShape(3.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp),
          ) {
            Text(
              text = row.alertBadgeText,
              color = palette.tailwindNegative,
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.ExtraBold,
            )
          }
        }
      }

      // Column 3: Monospace Raw METAR Code Pill
      Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = Modifier.weight(0.65f),
      ) {
        Box(
          modifier = Modifier
            .background(palette.secondaryFieldBackground, RoundedCornerShape(3.dp))
            .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
          Text(
            text = row.rawToken,
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TafStructuredSection(
  decodedTaf: DecodedTaf,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  alertConfig: WeatherAlertConfig,
  isWide: Boolean,
) {
  val palette = LocalMeteoPalette.current
  val clipboardManager = LocalClipboardManager.current
  var copiedTaf by remember(decodedTaf.rawTaf) { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("taf_structured_table")
      .background(palette.panelSurface, RoundedCornerShape(4.dp))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp)),
  ) {
    // Section Header
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.statusBarBg)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "TAF · ${decodedTaf.stationIcao} · ПРОГНОЗ ПО ПЕРИОДАМ",
          color = palette.textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.4.sp,
        )
        Text(
          text = "Выпущен: ${decodedTaf.issuedUtc} · Действует: ${decodedTaf.validityWindow} (${windUnit.label}, облачность в ${if (pressureUnit == PressureUnit.HPA) "м" else "ft"})",
          color = palette.textSecondary,
          fontSize = 10.5.sp,
        )
      }

      if (decodedTaf.rawTaf.isNotBlank()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("copy_raw_taf_button")
            .clip(RoundedCornerShape(3.dp))
            .background(palette.secondaryFieldBackground)
            .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
            .clickable {
              clipboardManager.setText(AnnotatedString(decodedTaf.rawTaf))
              copiedTaf = true
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Копировать TAF",
            tint = palette.textSecondary,
            modifier = Modifier.size(13.dp),
          )
          Text(
            text = if (copiedTaf) "Скопировано" else "Копировать",
            color = if (copiedTaf) palette.headwindPositive else palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
          )
        }
      }
    }

    HorizontalDivider(thickness = 1.dp, color = palette.border)

    if (decodedTaf.rawTaf.isNotBlank()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("raw_taf_full_text")
          .background(palette.fieldBackground)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        Text(
          text = decodedTaf.rawTaf,
          color = palette.textPrimary,
          fontSize = 13.5.sp,
          fontFamily = FontFamily.Monospace,
          lineHeight = 19.sp,
        )
      }
      HorizontalDivider(thickness = 0.8.dp, color = palette.border)
    }

    when {
      decodedTaf.isCancelled -> {
        TafEmptyOrStatusCard(
          icon = Icons.Filled.WarningAmber,
          title = "Прогноз TAF отменён (CNL)",
          subtitle = "Бюллетень содержит отметку отмены прогноза для ${decodedTaf.stationIcao}.",
          accentColor = palette.tailwindNegative,
        )
      }
      decodedTaf.isNil || decodedTaf.periods.isEmpty() -> {
        TafEmptyOrStatusCard(
          icon = Icons.Filled.CloudQueue,
          title = "Данные TAF недоступны",
          subtitle = "Ожидается обновление прогноза аэродрома или сводка содержит NIL.",
          accentColor = palette.textMuted,
        )
      }
      else -> {
        // Table Column Header for TAF rows
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.secondaryFieldBackground)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
          Text(
            text = "ГРУППА / ПЕРИОД (UTC)",
            color = palette.textSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "УСЛОВИЯ ПРОГНОЗА",
            color = palette.textSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.End,
          )
        }

        HorizontalDivider(thickness = 1.dp, color = palette.borderStrong)

        decodedTaf.periods.forEachIndexed { index, period ->
          TafForecastPeriodRow(
            index = index,
            period = period,
            windUnit = windUnit,
            alertConfig = alertConfig,
            isAlternate = index % 2 == 1,
            isWide = isWide,
          )
          if (index < decodedTaf.periods.lastIndex) {
            HorizontalDivider(thickness = 0.8.dp, color = palette.border)
          }
        }
      }
    }
  }
}

@Composable
private fun TafForecastPeriodRow(
  index: Int,
  period: TafForecastPeriod,
  windUnit: WindSpeedUnit,
  alertConfig: WeatherAlertConfig,
  isAlternate: Boolean,
  isWide: Boolean,
) {
  val palette = LocalMeteoPalette.current
  val hasThresholdAlert = period.isWindAlert || period.isVisibilityAlert
  val changeColor = when {
    hasThresholdAlert || period.isHazardous -> palette.tailwindNegative
    period.type == TafChangeType.BASE -> palette.headwindPositive
    period.type == TafChangeType.TEMPO || period.type == TafChangeType.PROB -> palette.metarAmber
    else -> MaterialTheme.colorScheme.primary
  }
  val rowBg = when {
    period.isHazardous -> palette.tailwindNegative.copy(alpha = 0.06f)
    isAlternate -> palette.secondaryFieldBackground.copy(alpha = 0.45f)
    else -> palette.fieldBackground
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min)
      .testTag("taf_row_$index")
      .background(rowBg),
  ) {
    Box(
      modifier = Modifier
        .width(3.dp)
        .fillMaxHeight()
        .background(changeColor),
    )

    Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 9.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
      // Period Badge + Time Window + Optional Hazard Pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = period.badgeLabel,
            color = Color.White,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
              .background(changeColor, RoundedCornerShape(3.dp))
              .padding(horizontal = 7.dp, vertical = 2.dp),
          )
          Text(
            text = period.validityLabel,
            color = palette.textPrimary,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
        }

        if (period.isHazardous) {
          Text(
            text = "СМУ",
            color = palette.tailwindNegative,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
              .background(palette.tailwindNegative.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
              .border(0.8.dp, palette.tailwindNegative, RoundedCornerShape(3.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }

      // 4 Aviation Parameters in Adaptive 2x2 or 4-column Strip
      val windAlertBadge = if (period.isWindAlert) {
        val limDisp = UnitConverter.formatWindSpeedValue(alertConfig.tableWindAlertKt, windUnit)
        "⚠ ≥ $limDisp ${windUnit.label}"
      } else {
        null
      }
      val visAlertBadge = if (period.isVisibilityAlert) {
        "⚠ ≤ ${alertConfig.tableVisibilityAlertMeters} м"
      } else {
        null
      }
      if (isWide) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          TafCell(
            label = "Ветер",
            value = period.windSummary,
            isAlert = period.isWindAlert,
            alertBadgeText = windAlertBadge,
            alertBadgeTestTag = "taf_wind_alert_badge_$index",
            modifier = Modifier.weight(1f),
          )
          TafCell(
            label = "Видимость",
            value = period.visibilitySummary,
            isAlert = period.isVisibilityAlert,
            alertBadgeText = visAlertBadge,
            alertBadgeTestTag = "taf_vis_alert_badge_$index",
            modifier = Modifier.weight(1f),
          )
          TafCell("Облачность", period.cloudsSummary, modifier = Modifier.weight(1f))
          TafCell("Явления", period.phenomenaSummary, modifier = Modifier.weight(1f))
        }
      } else {
        Column(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            TafCell(
              label = "Ветер",
              value = period.windSummary,
              isAlert = period.isWindAlert,
              alertBadgeText = windAlertBadge,
              alertBadgeTestTag = "taf_wind_alert_badge_$index",
              modifier = Modifier.weight(1f),
            )
            TafCell(
              label = "Видимость",
              value = period.visibilitySummary,
              isAlert = period.isVisibilityAlert,
              alertBadgeText = visAlertBadge,
              alertBadgeTestTag = "taf_vis_alert_badge_$index",
              modifier = Modifier.weight(1f),
            )
          }
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            TafCell("Облачность", period.cloudsSummary, modifier = Modifier.weight(1f))
            TafCell("Явления", period.phenomenaSummary, modifier = Modifier.weight(1f))
          }
        }
      }

      // Raw TAF Segment Verification Strip
      Text(
        text = period.rawSegment,
        color = palette.textMuted,
        fontSize = 10.5.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.secondaryFieldBackground.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
          .padding(horizontal = 7.dp, vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun TafCell(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  isAlert: Boolean = false,
  alertBadgeText: String? = null,
  alertBadgeTestTag: String? = null,
) {
  val palette = LocalMeteoPalette.current
  Column(
    verticalArrangement = Arrangement.spacedBy(2.dp),
    modifier = modifier,
  ) {
    Text(
      text = label.uppercase(Locale.US),
      color = if (isAlert) palette.tailwindNegative else palette.textMuted,
      fontSize = 9.5.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
    )
    Text(
      text = value,
      color = if (isAlert) palette.tailwindNegative else palette.textPrimary,
      fontSize = 12.5.sp,
      fontWeight = if (isAlert) FontWeight.ExtraBold else FontWeight.SemiBold,
      lineHeight = 16.sp,
    )
    if (alertBadgeText != null) {
      Box(
        modifier = Modifier
          .then(if (alertBadgeTestTag != null) Modifier.testTag(alertBadgeTestTag) else Modifier)
          .background(palette.tailwindNegative.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
          .border(0.8.dp, palette.tailwindNegative, RoundedCornerShape(3.dp))
          .padding(horizontal = 5.dp, vertical = 1.dp),
      ) {
        Text(
          text = alertBadgeText,
          color = palette.tailwindNegative,
          fontSize = 9.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
        )
      }
    }
  }
}

@Composable
private fun TafEmptyOrStatusCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  accentColor: Color,
) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier
      .fillMaxWidth()
      .background(palette.fieldBackground)
      .padding(14.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = title,
      tint = accentColor,
      modifier = Modifier.size(22.dp),
    )
    Column {
      Text(
        text = title,
        color = palette.textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = subtitle,
        color = palette.textSecondary,
        fontSize = 11.5.sp,
      )
    }
  }
}

@Composable
private fun FlightCategoryChip(category: FlightCategory) {
  val palette = LocalMeteoPalette.current
  val color = tableFlightCategoryColor(category)
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .testTag("aviation_table_flight_category")
      .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
      .border(1.2.dp, color, RoundedCornerShape(4.dp))
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(
      text = category.code,
      color = if (category == FlightCategory.UNKNOWN) palette.textSecondary else color,
      fontSize = 12.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.ExtraBold,
    )
  }
}

@Composable
private fun tableFlightCategoryColor(category: FlightCategory): Color {
  val palette = LocalMeteoPalette.current
  return when (category) {
    FlightCategory.VFR -> palette.headwindPositive
    FlightCategory.MVFR -> Color(0xFF0284C7)
    FlightCategory.IFR -> palette.tailwindNegative
    FlightCategory.UNKNOWN -> palette.textMuted
  }
}

@Composable
fun UnitSystemTableToggle(
  activeSystem: UnitSystem?,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  onSelectSystem: (UnitSystem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("aviation_table_unit_system_toggle")
      .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
      .padding(horizontal = 6.dp, vertical = 5.dp),
  ) {
    Text(
      text = "СИСТЕМА:",
      color = palette.textMuted,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.5.sp,
      modifier = Modifier.padding(horizontal = 4.dp),
    )

    val isMetric = activeSystem == UnitSystem.METRIC
    val isImperial = activeSystem == UnitSystem.IMPERIAL

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .weight(1f)
        .minimumInteractiveComponentSize()
        .testTag("table_unit_metric")
        .clip(RoundedCornerShape(4.dp))
        .background(if (isMetric) palette.buttonActiveBackground else palette.fieldBackground)
        .border(
          width = if (isMetric) 1.5.dp else 1.dp,
          color = if (isMetric) MaterialTheme.colorScheme.primary else palette.border,
          shape = RoundedCornerShape(4.dp),
        )
        .clickable { onSelectSystem(UnitSystem.METRIC) }
        .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
      Text(
        text = "Metric · гПа, м/с, °C",
        color = if (isMetric) palette.metarAmber else palette.textSecondary,
        fontSize = 11.sp,
        fontWeight = if (isMetric) FontWeight.ExtraBold else FontWeight.Medium,
        textAlign = TextAlign.Center,
      )
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .weight(1f)
        .minimumInteractiveComponentSize()
        .testTag("table_unit_imperial")
        .clip(RoundedCornerShape(4.dp))
        .background(if (isImperial) palette.buttonActiveBackground else palette.fieldBackground)
        .border(
          width = if (isImperial) 1.5.dp else 1.dp,
          color = if (isImperial) MaterialTheme.colorScheme.primary else palette.border,
          shape = RoundedCornerShape(4.dp),
        )
        .clickable { onSelectSystem(UnitSystem.IMPERIAL) }
        .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
      Text(
        text = "Imperial · inHg, kt, °F",
        color = if (isImperial) palette.metarAmber else palette.textSecondary,
        fontSize = 11.sp,
        fontWeight = if (isImperial) FontWeight.ExtraBold else FontWeight.Medium,
        textAlign = TextAlign.Center,
      )
    }

    if (activeSystem == null) {
      Text(
        text = "${windUnit.label}/${pressureUnit.label}",
        color = palette.textMuted,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
  }
}

@Composable
private fun UnitPillButton(
  text: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val palette = LocalMeteoPalette.current
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .testTag(testTag)
      .clip(RoundedCornerShape(4.dp))
      .background(palette.buttonBackground)
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    Text(
      text = text,
      color = palette.textPrimary,
      fontSize = 11.5.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}

private fun buildAviationMetarRows(
  station: AirportStation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  alertConfig: WeatherAlertConfig = WeatherAlertConfig(),
): List<MetarTableRowModel> {
  val tokens = weather.rawMetar.split(" ").filter { it.isNotBlank() }

  val windToken = tokens.firstOrNull { Regex("^(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?(KT|MPS)$").matches(it) } ?: "---"
  val visToken = when {
    weather.isCavok -> "CAVOK"
    else -> tokens.firstOrNull { Regex("^(\\d{4}|\\d+SM)$").matches(it) && it != windToken } ?: "---"
  }
  val qnhToken = tokens.firstOrNull { Regex("^[QA]\\d{4}$").matches(it) } ?: "---"
  val obsToken = tokens.firstOrNull { Regex("^\\d{6}Z$").matches(it) } ?: "---"
  val tempToken = tokens.firstOrNull { Regex("^M?\\d{2}/M?\\d{2}$").matches(it) } ?: "---"
  val rwyStateToken = tokens.firstOrNull { Regex("^R\\d{2}[LRC]?/.*$").matches(it) } ?: "---"
  val cloudTokens = weather.clouds.joinToString(" ") { it.rawToken }.ifBlank {
    if (weather.isCavok) "CAVOK" else "NSC"
  }

  val windDirText = when {
    weather.wind.isCalm -> "Штиль"
    weather.wind.isVariable -> "Переменный (VRB)"
    weather.wind.directionDeg != null -> String.format(Locale.US, "%03d°", weather.wind.directionDeg)
    else -> "--"
  }
  val activeSpeed = UnitConverter.formatWindSpeedValue(weather.wind.speedKt, windUnit)
  val speedKt = weather.wind.speedKt
  val speedMps = weather.wind.speedMps.roundToInt()
  val speedKph = UnitConverter.formatWindSpeedValue(weather.wind.speedKt, WindSpeedUnit.KPH)
  val windSpeedPrimary = "$activeSpeed ${windUnit.label}"
  val allWindConversions = "$speedKt kt · $speedMps м/с · $speedKph км/ч"

  val gustPart = weather.wind.gustKt?.let { gKt ->
    val gVal = UnitConverter.formatWindSpeedValue(gKt, windUnit)
    " · порывы $gVal ${windUnit.label}"
  }.orEmpty()
  val varPart = if (weather.wind.variableFromDeg != null && weather.wind.variableToDeg != null) {
    " · сектор ${weather.wind.variableFromDeg}°–${weather.wind.variableToDeg}°"
  } else {
    ""
  }
  val peakWindKt = weather.wind.gustKt ?: weather.wind.speedKt
  val windThresholdExceeded = alertConfig.tableAlertsEnabled && peakWindKt >= alertConfig.tableWindAlertKt
  val windSeverity = when {
    windThresholdExceeded -> RowSeverity.ALERT
    alertConfig.tableAlertsEnabled && peakWindKt >= (alertConfig.tableWindAlertKt - 5).coerceAtLeast(1) -> RowSeverity.CAUTION
    !alertConfig.tableAlertsEnabled && peakWindKt >= 18 -> RowSeverity.CAUTION
    else -> RowSeverity.NORMAL
  }
  val windAlertBadge = if (windThresholdExceeded) {
    val limDisp = UnitConverter.formatWindSpeedValue(alertConfig.tableWindAlertKt, windUnit)
    "⚠ ≥ $limDisp ${windUnit.label}"
  } else {
    null
  }

  val isImperialDisplay = pressureUnit == PressureUnit.INHG
  val visPrimary = when {
    weather.isCavok -> if (isImperialDisplay) "≥ 10 SM (CAVOK)" else "≥ 10 км (CAVOK)"
    weather.visibilityMeters != null && weather.visibilityMeters >= 9999 -> if (isImperialDisplay) "≥ 6 SM (9999 м)" else "≥ 10 км (9999 м)"
    weather.visibilityMeters != null -> {
      if (isImperialDisplay) {
        val miles = weather.visibilityMeters / 1609.34
        String.format(Locale.US, "%.1f SM (%d м)", miles, weather.visibilityMeters)
      } else {
        "${weather.visibilityMeters} м"
      }
    }
    else -> "Нет данных"
  }
  val rvrDetail = buildString {
    append("Явления: ${weather.weatherPhenomena}")
    if (weather.rvrPrimaryMeters != null) {
      if (isImperialDisplay) {
        val rvrFt = (weather.rvrPrimaryMeters * 3.28084).roundToInt()
        append(" · RVR $rvrFt ft (${weather.rvrPrimaryMeters} м)")
      } else {
        append(" · RVR ${weather.rvrPrimaryMeters} м")
      }
    }
  }
  val visThresholdExceeded = alertConfig.tableAlertsEnabled &&
    !weather.isCavok &&
    weather.visibilityMeters != null &&
    weather.visibilityMeters <= alertConfig.tableVisibilityAlertMeters
  val visSeverity = when {
    visThresholdExceeded -> RowSeverity.ALERT
    !weather.isCavok && weather.visibilityMeters != null && weather.visibilityMeters < 5000 -> RowSeverity.CAUTION
    else -> RowSeverity.NORMAL
  }
  val visAlertBadge = if (visThresholdExceeded) {
    "⚠ ≤ ${alertConfig.tableVisibilityAlertMeters} м"
  } else {
    null
  }

  val (qnhVal, qnhUnitLabel) = UnitConverter.formatPressurePrimary(
    hpa = weather.qnhHpa,
    inHg = weather.qnhInHg,
    unit = pressureUnit,
  )
  val qnhPrimary = "$qnhVal $qnhUnitLabel"
  val qnhSecondary = buildString {
    append(
      UnitConverter.formatPressureSecondary(
        hpa = weather.qnhHpa,
        mmHg = weather.qnhMmHg,
        inHg = weather.qnhInHg,
        activeUnit = pressureUnit,
      ),
    )
    if (weather.qfeHpa != null && weather.qfeMmHg != null) {
      val (qfeVal, qfeUnit) = UnitConverter.formatPressurePrimary(weather.qfeHpa, weather.qfeInHg, pressureUnit)
      append("\nQFE (${UnitConverter.formatElevation(station.elevationMeters, pressureUnit)}): $qfeVal $qfeUnit / ${weather.qfeMmHg} мм")
    }
  }

  val ceilingPrimary = if (isImperialDisplay) {
    weather.ceilingFeet?.let { "ВНГО $it ft" }
  } else {
    weather.ceilingMeters?.let { "ВНГО $it м" }
      ?: weather.ceilingFeet?.let { "ВНГО ${UnitConverter.feetToMeters(it)} м" }
  } ?: if (weather.isCavok) "CAVOK" else "Без значимой облачности"
  val cloudsDetail = if (weather.clouds.isNotEmpty()) {
    weather.clouds.joinToString(", ") {
      if (isImperialDisplay) "${it.cover} ${it.baseFeet} ft" else "${it.cover} ${it.baseMeters} м"
    }
  } else {
    "Слои облачности не зафиксированы"
  }
  val cloudSeverity = when {
    weather.ceilingFeet != null && weather.ceilingFeet <= 500 -> RowSeverity.ALERT
    weather.ceilingFeet != null && weather.ceilingFeet <= 1500 -> RowSeverity.CAUTION
    else -> RowSeverity.NORMAL
  }

  val tempPrimary = if (weather.temperatureC != null && weather.dewPointC != null) {
    if (isImperialDisplay) {
      val tF = (weather.temperatureC * 9.0 / 5.0 + 32.0).roundToInt()
      val dpF = (weather.dewPointC * 9.0 / 5.0 + 32.0).roundToInt()
      "$tF °F / $dpF °F"
    } else {
      "${weather.temperatureC} °C / ${weather.dewPointC} °C"
    }
  } else {
    "-- / --"
  }
  val spread = if (weather.temperatureC != null && weather.dewPointC != null) {
    weather.temperatureC - weather.dewPointC
  } else {
    null
  }
  val spreadDetail = buildString {
    if (isImperialDisplay && weather.temperatureC != null && weather.dewPointC != null) {
      append("${weather.temperatureC} °C / ${weather.dewPointC} °C · ")
    }
    spread?.let { append("Дефицит точки росы: $it °C") }
  }
  val tempSeverity = if (spread != null && spread <= 2) RowSeverity.CAUTION else RowSeverity.NORMAL

  val runwaySeverity = if (weather.frictionCoeff != null && weather.frictionCoeff < 0.35) {
    RowSeverity.ALERT
  } else {
    RowSeverity.NORMAL
  }

  return listOf(
    MetarTableRowModel(
      icon = Icons.Filled.Air,
      label = "Ветер",
      subLabel = "Wind",
      primaryValue = "$windDirText, $windSpeedPrimary",
      secondaryDetails = "$allWindConversions$gustPart$varPart",
      rawToken = windToken,
      severity = windSeverity,
      testTag = "metar_row_wind",
      alertBadgeText = windAlertBadge,
      alertBadgeTestTag = if (windAlertBadge != null) "metar_wind_alert_badge" else null,
    ),
    MetarTableRowModel(
      icon = Icons.Filled.Visibility,
      label = "Видимость",
      subLabel = "Visibility (MOR)",
      primaryValue = visPrimary,
      secondaryDetails = rvrDetail,
      rawToken = visToken,
      severity = visSeverity,
      testTag = "metar_row_visibility",
      alertBadgeText = visAlertBadge,
      alertBadgeTestTag = if (visAlertBadge != null) "metar_vis_alert_badge" else null,
    ),
    MetarTableRowModel(
      icon = Icons.Filled.Compress,
      label = "Давление QNH",
      subLabel = "Altimeter / QNH",
      primaryValue = qnhPrimary,
      secondaryDetails = qnhSecondary,
      rawToken = qnhToken,
      severity = RowSeverity.NORMAL,
      testTag = "metar_row_qnh",
    ),
    MetarTableRowModel(
      icon = Icons.Filled.Cloud,
      label = "Облачность",
      subLabel = "Clouds / ВНГО",
      primaryValue = ceilingPrimary,
      secondaryDetails = cloudsDetail,
      rawToken = cloudTokens,
      severity = cloudSeverity,
      testTag = "metar_row_clouds",
    ),
    MetarTableRowModel(
      icon = Icons.Filled.Thermostat,
      label = "Температура / DP",
      subLabel = "Temp / Dew Point",
      primaryValue = tempPrimary,
      secondaryDetails = spreadDetail,
      rawToken = tempToken,
      severity = tempSeverity,
      testTag = "metar_row_temp",
    ),
    MetarTableRowModel(
      icon = Icons.Filled.WaterDrop,
      label = "Состояние ВПП",
      subLabel = "Runway / КСц",
      primaryValue = weather.frictionCoeff?.let { String.format(Locale.US, "КСц %.2f", it) } ?: "Нет данных",
      secondaryDetails = "Особые условия: ${weather.specialConditions}",
      rawToken = rwyStateToken,
      severity = runwaySeverity,
      testTag = "metar_row_runway",
    ),
    MetarTableRowModel(
      icon = Icons.Filled.Schedule,
      label = "Время наблюдения",
      subLabel = "Observation Time",
      primaryValue = "${weather.observationTimeUtc} (${weather.localObsTime} ${station.timezoneLabel})",
      secondaryDetails = "Восход ${weather.sunriseLocal} · Заход ${weather.sunsetLocal}",
      rawToken = obsToken,
      severity = RowSeverity.NORMAL,
      testTag = "metar_row_time",
    ),
  )
}
