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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.PressureUnit
import com.example.model.QnhTrendSummary
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherHistoryEntry
import com.example.model.WindSpeedUnit
import com.example.ui.components.QnhHistoryLineChart
import com.example.ui.theme.LocalMeteoPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class HistoryFilterTab(val label: String) {
  ALL("Все (METAR + TAF)"),
  METAR("Только METAR"),
  TAF("Только TAF"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherHistory24hScreen(
  station: AirportStation,
  entries: List<WeatherHistoryEntry>,
  qnhTrend: QnhTrendSummary,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  alertConfig: WeatherAlertConfig,
  isFullscreen: Boolean,
  isLoading: Boolean,
  statusMessage: String? = null,
  onBack: () -> Unit,
  onRefresh24h: () -> Unit,
  onToggleWindUnit: () -> Unit,
  onTogglePressureUnit: () -> Unit,
  onOpenThresholdConfig: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  var activeFilter by remember { mutableStateOf(HistoryFilterTab.ALL) }

  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  val sortedDesc = remember(entries) {
    entries.sortedByDescending { it.observedAtMillis }
  }
  val filteredEntries = remember(sortedDesc, activeFilter) {
    when (activeFilter) {
      HistoryFilterTab.ALL -> sortedDesc
      HistoryFilterTab.METAR -> sortedDesc.filter { it.rawMetar.isNotBlank() }
      HistoryFilterTab.TAF -> sortedDesc.filter {
        it.rawTaf.isNotBlank() && !it.rawTaf.contains("нет данных", ignoreCase = true)
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("history_24h_screen")
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 980.dp)
        .align(Alignment.TopCenter),
    ) {
      // Top Navigation & Actions Bar
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
              .testTag("history_24h_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад",
              tint = palette.textPrimary,
            )
          }
          Column {
            Text(
              text = "История 24ч · ${station.icao}",
              color = palette.textPrimary,
              fontSize = 15.5.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${station.city.ifBlank { station.name }} · METAR, TAF и тренд QNH",
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          HistoryActionPill(
            text = windUnit.label,
            testTag = "history_24h_wind_toggle",
            onClick = onToggleWindUnit,
          )
          HistoryActionPill(
            text = pressureUnit.label,
            testTag = "history_24h_pressure_toggle",
            onClick = onTogglePressureUnit,
          )
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("history_24h_refresh_button")
              .clip(RoundedCornerShape(4.dp))
              .background(palette.buttonActiveBackground)
              .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
              .clickable(enabled = !isLoading, onClick = onRefresh24h)
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = palette.metarAmber,
                modifier = Modifier.size(16.dp),
              )
            } else {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = "Обновить историю за 24 часа",
                  tint = palette.metarAmber,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "24ч",
                  color = palette.textPrimary,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }
      }

      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        // 1. 24h QNH Trend Line Chart
        item(key = "qnh_chart") {
          QnhHistoryLineChart(
            trend = qnhTrend,
            pressureUnit = pressureUnit,
            onTogglePressureUnit = onTogglePressureUnit,
          )
        }

        // Optional status message
        if (!statusMessage.isNullOrBlank()) {
          item(key = "status_msg") {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .background(palette.panelSurface, RoundedCornerShape(4.dp))
                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
              Text(
                text = statusMessage,
                color = palette.textSecondary,
                fontSize = 11.5.sp,
                modifier = Modifier.weight(1f),
              )
            }
          }
        }

        // 2. Filter Bar + Entry Count Badge + Thresholds Shortcut
        item(key = "filter_bar") {
          Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
              .fillMaxWidth()
              .background(palette.panelSurface, RoundedCornerShape(4.dp))
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
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
                  imageVector = Icons.Filled.History,
                  contentDescription = null,
                  tint = palette.metarAmber,
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "АРХИВ СВОДОК ЗА 24 ЧАСА",
                  color = palette.textPrimary,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.ExtraBold,
                  letterSpacing = 0.4.sp,
                )
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                if (onOpenThresholdConfig != null) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("history_open_thresholds_button")
                      .clip(RoundedCornerShape(4.dp))
                      .background(palette.buttonBackground)
                      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                      .clickable(onClick = onOpenThresholdConfig)
                      .padding(horizontal = 8.dp, vertical = 4.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Tune,
                      contentDescription = "Пороги тревоги",
                      tint = palette.metarAmber,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = "Пороги",
                      color = palette.textPrimary,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.SemiBold,
                    )
                  }
                }

                Box(
                  modifier = Modifier
                    .testTag("history_entries_count")
                    .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
                    .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                  Text(
                    text = "${filteredEntries.size} зап.",
                    color = palette.metarAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                  )
                }
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              HistoryFilterTab.entries.forEach { tab ->
                val selected = tab == activeFilter
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("history_filter_${tab.name}")
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                    .border(
                      width = if (selected) 1.5.dp else 1.dp,
                      color = if (selected) MaterialTheme.colorScheme.primary else palette.border,
                      shape = RoundedCornerShape(4.dp),
                    )
                    .clickable { activeFilter = tab }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                ) {
                  Text(
                    text = tab.label,
                    color = if (selected) palette.textPrimary else palette.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                  )
                }
              }
            }
          }
        }

        // 3. Empty State or Chronological Entries
        if (filteredEntries.isEmpty()) {
          item(key = "history_empty") {
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("history_24h_empty")
                .background(palette.panelSurface, RoundedCornerShape(4.dp))
                .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                .padding(20.dp),
            ) {
              Text(
                text = if (isLoading) {
                  "Загрузка сводок за последние 24 часа…"
                } else {
                  "Нет записей за последние 24 часа для выбранного фильтра. Нажмите «24ч» сверху для загрузки с сервера."
                },
                color = palette.textSecondary,
                fontSize = 12.5.sp,
                textAlign = TextAlign.Center,
              )
            }
          }
        } else {
          itemsIndexed(
            items = filteredEntries,
            key = { idx, item -> "${item.observationTimeUtc}_${item.observedAtMillis}_$idx" },
          ) { index, entry ->
            WeatherHistoryEntryCard(
              index = index,
              entry = entry,
              station = station,
              windUnit = windUnit,
              pressureUnit = pressureUnit,
              alertConfig = alertConfig,
              filter = activeFilter,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeatherHistoryEntryCard(
  index: Int,
  entry: WeatherHistoryEntry,
  station: AirportStation,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  alertConfig: WeatherAlertConfig,
  filter: HistoryFilterTab,
) {
  val palette = LocalMeteoPalette.current
  val peakWindKt = entry.gustKt ?: entry.windSpeedKt
  val windAlert = alertConfig.tableAlertsEnabled && peakWindKt >= alertConfig.tableWindAlertKt
  val visAlert = alertConfig.tableAlertsEnabled &&
    !entry.isCavok &&
    entry.visibilityMeters != null &&
    entry.visibilityMeters <= alertConfig.tableVisibilityAlertMeters
  val hasAnyAlert = windAlert || visAlert

  val borderColor = if (hasAnyAlert) palette.tailwindNegative else palette.borderStrong
  val bgColor = if (hasAnyAlert) palette.tailwindNegative.copy(alpha = 0.06f) else palette.panelSurface

  val localTimeStr = remember(entry.observedAtMillis, station.utcOffsetHours) {
    formatLocalClock(entry.observedAtMillis, station.utcOffsetHours)
  }

  val windVal = UnitConverter.formatWindSpeedValue(entry.windSpeedKt, windUnit)
  val windText = buildString {
    val dir = entry.windDirectionDeg?.let { String.format(Locale.US, "%03d°", it) } ?: "VRB"
    if (entry.windSpeedKt == 0) {
      append("Штиль")
    } else {
      append("$dir $windVal ${windUnit.label}")
      entry.gustKt?.let { g ->
        append(" G${UnitConverter.formatWindSpeedValue(g, windUnit)}")
      }
    }
  }
  val visText = when {
    entry.isCavok -> "CAVOK"
    entry.visibilityMeters != null && entry.visibilityMeters >= 9999 -> "≥ 10 км"
    entry.visibilityMeters != null -> "${entry.visibilityMeters} м"
    else -> "--"
  }
  val cloudText = entry.ceilingFeet?.let { "ВНГО $it ft" } ?: if (entry.isCavok) "CAVOK" else "NSC"
  val (qnhVal, qnhUnitLbl) = UnitConverter.formatPressurePrimary(entry.qnhHpa, entry.qnhInHg, pressureUnit)
  val tempText = if (entry.temperatureC != null && entry.dewPointC != null) {
    "${entry.temperatureC}°/${entry.dewPointC}°"
  } else {
    "--/--"
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("history_entry_$index")
      .background(bgColor, RoundedCornerShape(4.dp))
      .border(if (hasAnyAlert) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(4.dp))
      .padding(10.dp),
  ) {
    // Card Top Bar: UTC + Local time + Flight Category + Source
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
          text = entry.observationTimeUtc,
          color = palette.metarAmber,
          fontSize = 13.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(
          text = "$localTimeStr ${station.timezoneLabel}",
          color = palette.textSecondary,
          fontSize = 11.5.sp,
          fontFamily = FontFamily.Monospace,
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (entry.sourceLabel.isNotBlank()) {
          Text(
            text = entry.sourceLabel,
            color = palette.textMuted,
            fontSize = 10.sp,
          )
        }
        val catColor = when (entry.flightCategoryCode) {
          "VFR" -> palette.headwindPositive
          "MVFR" -> Color(0xFF0284C7)
          "IFR" -> palette.tailwindNegative
          else -> palette.textMuted
        }
        Box(
          modifier = Modifier
            .background(catColor.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
            .border(1.dp, catColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
          Text(
            text = entry.flightCategoryCode,
            color = catColor,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
          )
        }
      }
    }

    // Decoded Summary Metrics FlowRow
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      HistoryMetricPill("Ветер: $windText", isAlert = windAlert)
      HistoryMetricPill("Вид: $visText", isAlert = visAlert)
      HistoryMetricPill(cloudText, isAlert = false)
      HistoryMetricPill("QNH $qnhVal $qnhUnitLbl", isAlert = false)
      HistoryMetricPill("T/DP $tempText", isAlert = false)
    }

    // Threshold breach pills
    if (hasAnyAlert) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        if (windAlert) {
          val limDisp = UnitConverter.formatWindSpeedValue(alertConfig.tableWindAlertKt, windUnit)
          HistoryBreachBadge("⚠ Ветер ≥ $limDisp ${windUnit.label}")
        }
        if (visAlert) {
          HistoryBreachBadge("⚠ Видимость ≤ ${alertConfig.tableVisibilityAlertMeters} м")
        }
      }
    }

    // Raw METAR block
    if (filter != HistoryFilterTab.TAF && entry.rawMetar.isNotBlank()) {
      Text(
        text = "METAR: ${entry.rawMetar}",
        color = palette.textPrimary,
        fontSize = 11.5.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 16.sp,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.fieldBackground, RoundedCornerShape(3.dp))
          .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
          .padding(horizontal = 8.dp, vertical = 6.dp),
      )
    }

    // Paired TAF block
    val hasTaf = entry.rawTaf.isNotBlank() && !entry.rawTaf.contains("нет данных", ignoreCase = true)
    if (filter != HistoryFilterTab.METAR && hasTaf) {
      Text(
        text = "TAF: ${entry.rawTaf}",
        color = palette.textSecondary,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 15.5.sp,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.secondaryFieldBackground, RoundedCornerShape(3.dp))
          .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
          .padding(horizontal = 8.dp, vertical = 6.dp),
      )
    }
  }
}

@Composable
private fun HistoryMetricPill(
  text: String,
  isAlert: Boolean,
) {
  val palette = LocalMeteoPalette.current
  Box(
    modifier = Modifier
      .background(
        if (isAlert) palette.tailwindNegative.copy(alpha = 0.14f) else palette.secondaryFieldBackground,
        RoundedCornerShape(3.dp),
      )
      .border(
        0.8.dp,
        if (isAlert) palette.tailwindNegative else palette.border,
        RoundedCornerShape(3.dp),
      )
      .padding(horizontal = 7.dp, vertical = 3.dp),
  ) {
    Text(
      text = text,
      color = if (isAlert) palette.tailwindNegative else palette.textPrimary,
      fontSize = 11.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = if (isAlert) FontWeight.Bold else FontWeight.Medium,
    )
  }
}

@Composable
private fun HistoryBreachBadge(text: String) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .background(palette.tailwindNegative.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
      .border(1.dp, palette.tailwindNegative, RoundedCornerShape(3.dp))
      .padding(horizontal = 7.dp, vertical = 2.dp),
  ) {
    Icon(
      imageVector = Icons.Filled.WarningAmber,
      contentDescription = text,
      tint = palette.tailwindNegative,
      modifier = Modifier.size(12.dp),
    )
    Text(
      text = text,
      color = palette.tailwindNegative,
      fontSize = 10.5.sp,
      fontWeight = FontWeight.ExtraBold,
    )
  }
}

@Composable
private fun HistoryActionPill(
  text: String,
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

private fun formatLocalClock(utcMillis: Long, utcOffsetHours: Int): String {
  val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.US).apply {
    val sign = if (utcOffsetHours >= 0) "+" else "-"
    val absH = kotlin.math.abs(utcOffsetHours)
    timeZone = TimeZone.getTimeZone("GMT$sign$absH")
  }
  return fmt.format(Date(utcMillis))
}
