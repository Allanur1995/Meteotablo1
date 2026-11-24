package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlightLand
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.SavedWeatherPreset
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendSummary
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale

@Composable
fun MeteoTopToolbar(
  station: AirportStation,
  metarObsUtc: String,
  isNightMode: Boolean,
  isFullscreen: Boolean,
  isLoading: Boolean,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isFavorite: Boolean,
  serverConfig: ServerConfig,
  autoRefreshEnabled: Boolean = true,
  autoRefreshIntervalMinutes: Int = 15,
  onRefresh: () -> Unit,
  onToggleNight: () -> Unit,
  onOpenLog: () -> Unit,
  onOpenMetarTable: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onToggleWindUnit: () -> Unit,
  onTogglePressureUnit: () -> Unit,
  onToggleFavorite: () -> Unit,
  onSelectStation: (String) -> Unit,
  onSelectSourceMode: (DataSourceMode) -> Unit,
  favoriteIcaos: List<String> = emptyList(),
  savedPresets: List<SavedWeatherPreset> = emptyList(),
  unreadAlertCount: Int = 0,
  alertsEnabled: Boolean = true,
  onSaveCurrentSetup: () -> Unit = {},
  onApplySavedPreset: (String) -> Unit = {},
  onOpenAlertsDialog: () -> Unit = {},
  onOpenStationDialog: () -> Unit,
  onOpenServerDialog: () -> Unit,
  onOpenAboutDialog: () -> Unit = {},
  onOpenSettings: () -> Unit = {},
  onOpenMap: () -> Unit = {},
  onOpenHistory24h: () -> Unit = {},
  onOpenExportDialog: () -> Unit = {},
  onOpenDecoder: () -> Unit = {},
  onOpenPressureUtility: () -> Unit = {},
  showDualWindRose: Boolean = false,
  onToggleDualWindRose: () -> Unit = {},
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var stationMenuExpanded by remember { mutableStateOf(false) }
  var quickControlsExpanded by remember { mutableStateOf(false) }
  var sourceSectionExpanded by remember { mutableStateOf(false) }
  var presetsSectionExpanded by remember { mutableStateOf(false) }

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .fillMaxWidth()
      .background(palette.panelSurface)
      .border(1.dp, palette.border)
      .padding(horizontal = 8.dp, vertical = if (compactMode) 3.dp else 6.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
      Text(
        text = com.example.model.AuthorBrand.APP_TITLE,
        color = palette.textPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .widthIn(max = 170.dp)
          .testTag("toolbar_brand_title")
          .clip(RoundedCornerShape(4.dp))
          .clickable(onClick = onOpenStationDialog)
          .padding(horizontal = 4.dp, vertical = 4.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Box {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .testTag("toolbar_station_selector")
            .clip(RoundedCornerShape(3.dp))
            .background(palette.secondaryFieldBackground)
            .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
            .clickable { stationMenuExpanded = true }
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        ) {
          Text(
            text = station.icao,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
          Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Выбрать аэропорт",
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
        DropdownMenu(
          expanded = stationMenuExpanded,
          onDismissRequest = { stationMenuExpanded = false },
        ) {
          if (savedPresets.isNotEmpty()) {
            DropdownMenuItem(
              text = {
                Text(
                  text = "Сохранённые ВПП / профили:",
                  color = palette.textSecondary,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                )
              },
              enabled = false,
              onClick = {},
            )
            savedPresets.forEach { preset ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = "★ ${preset.name}",
                    fontWeight = FontWeight.SemiBold,
                  )
                },
                onClick = {
                  stationMenuExpanded = false
                  onApplySavedPreset(preset.id)
                },
                modifier = Modifier.testTag("station_menu_preset_${preset.id}"),
              )
            }
            HorizontalDivider()
          }
          AirportCatalog.presets.filter { it.icao.startsWith("UT") }.forEach { preset ->
            DropdownMenuItem(
              text = {
                Text(
                  text = "${preset.icao} — ${preset.city}",
                  fontWeight = if (preset.icao == station.icao) FontWeight.Bold else FontWeight.Normal,
                )
              },
              onClick = {
                stationMenuExpanded = false
                onSelectStation(preset.icao)
              },
              modifier = Modifier.testTag("toolbar_station_item_${preset.icao}"),
            )
          }
          HorizontalDivider()
          DropdownMenuItem(
            text = { Text("Все аэропорты / Ввод METAR…") },
            onClick = {
              stationMenuExpanded = false
              onOpenStationDialog()
            },
          )
        }
      }
      Spacer(modifier = Modifier.width(4.dp))
      Icon(
        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
        contentDescription = if (isFavorite) "Убрать из Моих аэропортов" else "Добавить в Мои аэропорты",
        tint = if (isFavorite) palette.metarAmber else palette.textMuted,
        modifier = Modifier
          .size(22.dp)
          .testTag("toolbar_favorite_toggle")
          .clickable(onClick = onToggleFavorite)
          .padding(2.dp),
      )
    }

    Box(
      contentAlignment = Alignment.CenterEnd,
      modifier = Modifier.weight(1f),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
      ) {
        Text(
          text = "METAR $metarObsUtc",
          color = palette.metarAmber,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .testTag("metar_timestamp_badge")
            .clickable(onClick = onOpenStationDialog)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        )

        Text(
          text = if (autoRefreshEnabled) "АВТО ${autoRefreshIntervalMinutes}м" else "РУЧН.",
          color = if (autoRefreshEnabled) palette.headwindPositive else palette.textMuted,
          fontSize = 10.5.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .testTag("toolbar_auto_refresh_badge")
            .clip(RoundedCornerShape(3.dp))
            .background(palette.secondaryFieldBackground)
            .border(
              0.8.dp,
              if (autoRefreshEnabled) palette.borderStrong else palette.border,
              RoundedCornerShape(3.dp),
            )
            .clickable(onClick = onOpenSettings)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        )

        ToolbarButton(
          label = "Обновить",
          isLoading = isLoading,
          icon = {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Обновить метеоданные вручную",
              tint = palette.metarAmber,
              modifier = Modifier.size(15.dp),
            )
          },
          testTag = "refresh_button",
          onClick = onRefresh,
        )

        ToolbarButton(
          label = "Таблица",
          icon = {
            Icon(
              imageVector = Icons.Filled.TableChart,
              contentDescription = "Таблица METAR",
              tint = palette.textPrimary,
              modifier = Modifier.size(14.dp),
            )
          },
          testTag = "metar_table_button",
          onClick = onOpenMetarTable,
        )

        ToolbarButton(
          label = "Декодер",
          icon = {
            Icon(
              imageVector = Icons.Filled.Description,
              contentDescription = "Открытый декодер METAR и TAF",
              tint = palette.metarAmber,
              modifier = Modifier.size(14.dp),
            )
          },
          testTag = "toolbar_decoder_button",
          onClick = onOpenDecoder,
        )

        ToolbarButton(
          label = "2× ВПП",
          isActive = showDualWindRose,
          icon = {
            Icon(
              imageVector = Icons.Filled.FlightLand,
              contentDescription = "Двойная роза ветров ВПП",
              tint = if (showDualWindRose) palette.metarAmber else palette.textPrimary,
              modifier = Modifier.size(14.dp),
            )
          },
          testTag = "toolbar_dual_wind_rose_button",
          onClick = onToggleDualWindRose,
        )

        ToolbarButton(
          label = "Карта",
          icon = {
            Icon(
              imageVector = Icons.Filled.Map,
              contentDescription = "Карта аэропортов",
              tint = palette.textPrimary,
              modifier = Modifier.size(14.dp),
            )
          },
          testTag = "toolbar_map_button",
          onClick = onOpenMap,
        )

        ToolbarButton(
          label = "24ч",
          icon = {
            Icon(
              imageVector = Icons.Filled.History,
              contentDescription = "История 24ч и график QNH",
              tint = palette.metarAmber,
              modifier = Modifier.size(14.dp),
            )
          },
          testTag = "toolbar_history_24h_button",
          onClick = onOpenHistory24h,
        )

        // Single consolidated control panel for changeable settings/toggles (units, source, presets, alerts, theme, fullscreen, log)
        val activeMode = serverConfig.resolvedMode()
        Box {
          val quickLabel = buildString {
            append("${windUnit.label} · ${pressureUnit.label}")
            if (unreadAlertCount > 0) {
              append(" ($unreadAlertCount)")
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .testTag("toolbar_quick_controls_button")
              .heightIn(min = 36.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(if (unreadAlertCount > 0 || quickControlsExpanded) palette.buttonActiveBackground else palette.buttonBackground)
              .border(
                1.dp,
                if (unreadAlertCount > 0) palette.tailwindNegative else palette.borderStrong,
                RoundedCornerShape(3.dp),
              )
              .clickable { quickControlsExpanded = true }
              .padding(horizontal = 9.dp, vertical = 6.dp),
          ) {
            Text(
              text = quickLabel,
              color = if (unreadAlertCount > 0) palette.tailwindNegative else palette.metarAmber,
              fontSize = 11.5.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
            )
            Icon(
              imageVector = Icons.Filled.ArrowDropDown,
              contentDescription = "Быстрые параметры и инструменты",
              tint = palette.textSecondary,
              modifier = Modifier.size(16.dp),
            )
          }

          DropdownMenu(
            expanded = quickControlsExpanded,
            onDismissRequest = {
              quickControlsExpanded = false
              sourceSectionExpanded = false
              presetsSectionExpanded = false
            },
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier
                .width(292.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("toolbar_quick_controls_menu"),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "БЫСТРЫЕ ПАРАМЕТРЫ ТАБЛО",
                  color = palette.textSecondary,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.ExtraBold,
                  letterSpacing = 0.5.sp,
                )
                Text(
                  text = "✕",
                  color = palette.textSecondary,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier
                    .testTag("toolbar_quick_controls_close")
                    .clip(RoundedCornerShape(3.dp))
                    .clickable {
                      quickControlsExpanded = false
                      sourceSectionExpanded = false
                      presetsSectionExpanded = false
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }

              // Consolidated Wind & Pressure Unit Toggles
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Box(modifier = Modifier.weight(1f)) {
                  ToolbarButton(
                    label = "Ветер: ${windUnit.label}",
                    isActive = windUnit != WindSpeedUnit.KT,
                    testTag = "toolbar_wind_unit_toggle",
                    onClick = onToggleWindUnit,
                  )
                }
                Box(modifier = Modifier.weight(1f)) {
                  ToolbarButton(
                    label = "Давл.: ${pressureUnit.label}",
                    isActive = pressureUnit != PressureUnit.HPA,
                    testTag = "toolbar_pressure_unit_toggle",
                    onClick = onTogglePressureUnit,
                  )
                }
              }

              // Consolidated Data Source Selector
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("toolbar_source_selector")
                  .heightIn(min = 38.dp)
                  .clip(RoundedCornerShape(3.dp))
                  .background(palette.secondaryFieldBackground)
                  .border(1.dp, palette.borderStrong, RoundedCornerShape(3.dp))
                  .clickable { sourceSectionExpanded = !sourceSectionExpanded }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(
                  text = "Источник: ${activeMode.shortLabel}",
                  color = palette.textPrimary,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                )
                Icon(
                  imageVector = Icons.Filled.ArrowDropDown,
                  contentDescription = "Выбрать источник данных",
                  tint = palette.textSecondary,
                  modifier = Modifier.size(16.dp),
                )
              }

              if (sourceSectionExpanded) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.fieldBackground, RoundedCornerShape(4.dp))
                    .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
                    .padding(6.dp),
                ) {
                  DataSourceMode.entries.forEach { mode ->
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("source_mode_item_${mode.name}")
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (mode == activeMode) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                        .clickable {
                          sourceSectionExpanded = false
                          quickControlsExpanded = false
                          onSelectSourceMode(mode)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                      Text(
                        text = mode.displayName,
                        color = palette.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = if (mode == activeMode) FontWeight.Bold else FontWeight.Normal,
                      )
                    }
                  }
                  HorizontalDivider()
                  Text(
                    text = "Настройки IP и сайтов…",
                    color = palette.metarAmber,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("source_mode_open_settings")
                      .clickable {
                        sourceSectionExpanded = false
                        quickControlsExpanded = false
                        onOpenServerDialog()
                      }
                      .padding(horizontal = 8.dp, vertical = 5.dp),
                  )
                  Text(
                    text = "О программе / Автор…",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("source_mode_open_about")
                      .clickable {
                        sourceSectionExpanded = false
                        quickControlsExpanded = false
                        onOpenAboutDialog()
                      }
                      .padding(horizontal = 8.dp, vertical = 5.dp),
                  )
                }
              }

              // Consolidated Presets Selector
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("toolbar_presets_button")
                  .heightIn(min = 38.dp)
                  .clip(RoundedCornerShape(3.dp))
                  .background(if (savedPresets.isNotEmpty()) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                  .border(1.dp, palette.border, RoundedCornerShape(3.dp))
                  .clickable { presetsSectionExpanded = !presetsSectionExpanded }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Сохранённые профили погоды",
                    tint = if (savedPresets.isNotEmpty()) palette.metarAmber else palette.textPrimary,
                    modifier = Modifier.size(14.dp),
                  )
                  Text(
                    text = if (savedPresets.isEmpty()) "Профили ВПП" else "Профили (${savedPresets.size})",
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
                Icon(
                  imageVector = Icons.Filled.ArrowDropDown,
                  contentDescription = null,
                  tint = palette.textSecondary,
                  modifier = Modifier.size(16.dp),
                )
              }

              if (presetsSectionExpanded) {
                Column(
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.fieldBackground, RoundedCornerShape(4.dp))
                    .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
                    .padding(6.dp),
                ) {
                  Text(
                    text = "+ Сохранить текущую ВПП (${station.icao})",
                    color = palette.metarAmber,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("preset_menu_save_current")
                      .clickable {
                        presetsSectionExpanded = false
                        quickControlsExpanded = false
                        onSaveCurrentSetup()
                      }
                      .padding(horizontal = 8.dp, vertical = 6.dp),
                  )
                  savedPresets.forEach { preset ->
                    Text(
                      text = preset.name,
                      color = palette.textPrimary,
                      fontSize = 11.5.sp,
                      modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preset_menu_item_${preset.id}")
                        .clickable {
                          presetsSectionExpanded = false
                          quickControlsExpanded = false
                          onApplySavedPreset(preset.id)
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                  }
                  HorizontalDivider()
                  Text(
                    text = "Управление профилями…",
                    color = palette.textSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("preset_menu_manage")
                      .clickable {
                        presetsSectionExpanded = false
                        quickControlsExpanded = false
                        onOpenStationDialog()
                      }
                      .padding(horizontal = 8.dp, vertical = 5.dp),
                  )
                }
              }

              HorizontalDivider()

              // Alerts, Theme (Light/Dark), Fullscreen, and Log
              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Box(modifier = Modifier.weight(1f)) {
                  ToolbarButton(
                    label = if (unreadAlertCount > 0) "Тревога ($unreadAlertCount)" else "Оповещ.",
                    isActive = unreadAlertCount > 0,
                    icon = {
                      Icon(
                        imageVector = if (unreadAlertCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                        contentDescription = "Метеооповещения избранных аэродромов",
                        tint = when {
                          unreadAlertCount > 0 -> palette.tailwindNegative
                          alertsEnabled -> palette.metarAmber
                          else -> palette.textMuted
                        },
                        modifier = Modifier.size(14.dp),
                      )
                    },
                    testTag = "toolbar_alerts_button",
                    onClick = {
                      quickControlsExpanded = false
                      onOpenAlertsDialog()
                    },
                  )
                }
                Box(modifier = Modifier.weight(1f)) {
                  ToolbarButton(
                    label = if (isNightMode) "День" else "Ночь",
                    icon = {
                      Icon(
                        imageVector = if (isNightMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isNightMode) "Дневной режим" else "Ночной режим",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(14.dp),
                      )
                    },
                    testTag = "night_mode_button",
                    onClick = onToggleNight,
                  )
                }
              }

              Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Box(modifier = Modifier.weight(1f)) {
                  ToolbarButton(
                    label = if (isFullscreen) "Окно" else "Весь экран",
                    icon = {
                      Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = "Полноэкранный режим",
                        tint = palette.textPrimary,
                        modifier = Modifier.size(14.dp),
                      )
                    },
                    testTag = "fullscreen_button",
                    onClick = {
                      quickControlsExpanded = false
                      onToggleFullscreen()
                    },
                  )
                }
                Box(modifier = Modifier.weight(1f)) {
                  ToolbarButton(
                    label = "Лог",
                    testTag = "log_button",
                    onClick = {
                      quickControlsExpanded = false
                      onOpenLog()
                    },
                  )
                }
              }

              ToolbarButton(
                label = "Калькулятор QNH / QFE",
                icon = {
                  Icon(
                    imageVector = Icons.Filled.Compress,
                    contentDescription = "Калькулятор давления QNH и QFE (hPa / inHg)",
                    tint = palette.metarAmber,
                    modifier = Modifier.size(14.dp),
                  )
                },
                testTag = "toolbar_pressure_utility_button",
                onClick = {
                  quickControlsExpanded = false
                  onOpenPressureUtility()
                },
              )

              ToolbarButton(
                label = "Экспорт отчёта (PDF / TXT)",
                icon = {
                  Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = "Экспорт погоды и трендов в PDF или текст",
                    tint = palette.metarAmber,
                    modifier = Modifier.size(14.dp),
                  )
                },
                testTag = "toolbar_export_button",
                onClick = {
                  quickControlsExpanded = false
                  onOpenExportDialog()
                },
              )
            }
          }
        }

        ToolbarButton(
          label = "Настройки",
          icon = {
            Icon(
              imageVector = Icons.Filled.Settings,
              contentDescription = "Настройки единиц, темы и обновления",
              tint = palette.textPrimary,
              modifier = Modifier.size(14.dp),
            )
          },
          testTag = "toolbar_settings_button",
          onClick = onOpenSettings,
        )
      }
    }
  }
}

@Composable
private fun ToolbarButton(
  label: String,
  isActive: Boolean = false,
  isLoading: Boolean = false,
  icon: (@Composable () -> Unit)? = null,
  testTag: String,
  onClick: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  val bg = if (isActive) palette.buttonActiveBackground else palette.buttonBackground

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier
      .testTag(testTag)
      .heightIn(min = 36.dp)
      .clip(RoundedCornerShape(3.dp))
      .background(bg)
      .border(1.dp, if (isActive) palette.borderStrong else palette.border, RoundedCornerShape(3.dp))
      .clickable(enabled = !isLoading, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        strokeWidth = 2.dp,
        color = palette.metarAmber,
        modifier = Modifier.size(13.dp),
      )
    } else if (icon != null) {
      icon()
    }
    Text(
      text = label,
      color = palette.textPrimary,
      fontSize = 12.sp,
      fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
    )
  }
}

@Composable
fun RunwayColumnPanel(
  station: AirportStation,
  selectedRunwayIndex: Int,
  calculation: RunwayCalculation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isPrimarySelector: Boolean,
  onSelectRunwayPair: (Int) -> Unit,
  onSelectStation: (String) -> Unit,
  onOpenStationDialog: () -> Unit,
  onToggleUnit: () -> Unit,
  onTogglePressureUnit: () -> Unit,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var dropdownExpanded by remember { mutableStateOf(false) }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.fillMaxWidth(),
  ) {
    // Header: either Dropdown selector (left panel) or bold text (right panel)
    if (isPrimarySelector) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = if (compactMode) 3.dp else 6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .testTag("runway_dropdown_button")
            .heightIn(min = if (compactMode) 28.dp else 36.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(palette.fieldBackground)
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
            .clickable { dropdownExpanded = true }
            .padding(horizontal = 10.dp, vertical = if (compactMode) 3.dp else 5.dp),
        ) {
          Text(
            text = "${station.icao} ${calculation.designator}",
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Выбрать ВПП или аэропорт",
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp),
          )
        }

        DropdownMenu(
          expanded = dropdownExpanded,
          onDismissRequest = { dropdownExpanded = false },
        ) {
          station.runways.forEachIndexed { idx, pair ->
            DropdownMenuItem(
              text = {
                Text("${station.icao} ${pair.primaryDesignator} / ${pair.reciprocalDesignator}")
              },
              onClick = {
                dropdownExpanded = false
                onSelectRunwayPair(idx)
              },
            )
          }
          HorizontalDivider()
          AirportCatalog.presets.filter { it.icao.startsWith("UT") }.forEach { preset ->
            DropdownMenuItem(
              text = {
                Text(
                  text = "${preset.icao} — ${preset.city}",
                  fontWeight = if (preset.icao == station.icao) FontWeight.Bold else FontWeight.Normal,
                )
              },
              onClick = {
                dropdownExpanded = false
                onSelectStation(preset.icao)
              },
            )
          }
          HorizontalDivider()
          DropdownMenuItem(
            text = { Text("Другой аэропорт / Ввести METAR…") },
            onClick = {
              dropdownExpanded = false
              onOpenStationDialog()
            },
          )
        }
      }
    } else {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = if (compactMode) 28.dp else 36.dp)
          .padding(bottom = if (compactMode) 3.dp else 6.dp),
      ) {
        Text(
          text = "${station.icao} ${calculation.designator}",
          color = palette.textPrimary,
          fontSize = if (compactMode) 15.sp else 18.sp,
          fontWeight = FontWeight.Medium,
        )
      }
    }

    // Wind Rose Dial Box
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .border(1.dp, palette.border)
        .background(palette.panelSurface),
    ) {
      RunwayWindRose(
        calculation = calculation,
        wind = weather.wind,
        windUnit = windUnit,
        onToggleUnit = onToggleUnit,
        compactMode = compactMode,
      )
    }

    Spacer(modifier = Modifier.height(if (compactMode) 4.dp else 8.dp))

    // RVR & QFE Block
    Column(
      verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      InstrumentValueRow(
        label = "RVR",
        value = calculation.rvrMeters?.toString() ?: "----",
        unit = "м",
        isHighlighted = true,
        compactMode = compactMode,
      )
      val (qfeVal, qfeUnit) = UnitConverter.formatPressurePrimary(
        hpa = weather.qfeHpa,
        inHg = weather.qfeInHg,
        unit = pressureUnit,
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onTogglePressureUnit),
      ) {
        InstrumentValueRow(
          label = "QFE",
          value = qfeVal,
          unit = qfeUnit,
          isHighlighted = true,
          compactMode = compactMode,
        )
      }
      if (compactMode) {
        val secondaryQfe = UnitConverter.formatPressureSecondary(
          hpa = weather.qfeHpa,
          mmHg = weather.qfeMmHg,
          inHg = weather.qfeInHg,
          activeUnit = pressureUnit,
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.secondaryFieldBackground)
            .padding(vertical = 2.dp, horizontal = 6.dp),
        ) {
          Text(
            text = secondaryQfe,
            color = palette.textSecondary,
            fontSize = 11.sp,
            maxLines = 1,
          )
        }
      } else {
        InstrumentValueRow(
          label = "",
          value = weather.qfeMmHg?.toString() ?: "---",
          unit = "мм.",
          isHighlighted = false,
        )
        val altValue = if (pressureUnit == PressureUnit.HPA) {
          weather.qfeInHg?.let { String.format(Locale("ru"), "%.2f", it) } ?: "--,--"
        } else {
          weather.qfeHpa?.toString() ?: "----"
        }
        val altUnit = if (pressureUnit == PressureUnit.HPA) "дм." else "гПа"
        InstrumentValueRow(
          label = "",
          value = altValue,
          unit = altUnit,
          isHighlighted = false,
        )
      }
    }
  }
}

@Composable
fun CenterWeatherPanel(
  weather: DecodedWeather,
  localClock: String,
  pressureUnit: PressureUnit,
  onTogglePressureUnit: () -> Unit,
  windTrend: WindTrendSummary? = null,
  windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val cloudsSummary = if (weather.clouds.isNotEmpty()) {
    weather.clouds.joinToString(" ") { it.rawToken }
  } else if (weather.isCavok) {
    "CAVOK"
  } else {
    "NSC"
  }

  if (compactMode) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = modifier.fillMaxWidth(),
    ) {
      // Row 1: Время, Восход, Заход
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        CompactLabeledBox(label = "Время", value = localClock, strong = true, modifier = Modifier.weight(1f))
        CompactLabeledBox(label = "Восход", value = weather.sunriseLocal, strong = false, modifier = Modifier.weight(1f))
        CompactLabeledBox(label = "Заход", value = weather.sunsetLocal, strong = false, modifier = Modifier.weight(1f))
      }

      // Row 2: QNH + secondary mmHg/inHg/hPa
      val (qnhVal, qnhUnit) = UnitConverter.formatPressurePrimary(
        hpa = weather.qnhHpa,
        inHg = weather.qnhInHg,
        unit = pressureUnit,
      )
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(onClick = onTogglePressureUnit),
      ) {
        InstrumentValueRow(
          label = "QNH",
          value = qnhVal,
          unit = qnhUnit,
          isHighlighted = true,
          compactMode = true,
        )
      }
      val qnhSecondary = UnitConverter.formatPressureSecondary(
        hpa = weather.qnhHpa,
        mmHg = weather.qnhMmHg,
        inHg = weather.qnhInHg,
        activeUnit = pressureUnit,
      )
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.secondaryFieldBackground)
          .padding(vertical = 2.dp, horizontal = 6.dp),
      ) {
        Text(
          text = qnhSecondary,
          color = palette.textSecondary,
          fontSize = 11.sp,
          maxLines = 1,
        )
      }

      // Row 3: T & DP side by side
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        CompactInlineMetric(
          label = "T",
          value = weather.temperatureC?.toString() ?: "--",
          unit = "°C",
          modifier = Modifier.weight(1f),
        )
        CompactInlineMetric(
          label = "DP",
          value = weather.dewPointC?.toString() ?: "--",
          unit = "°C",
          modifier = Modifier.weight(1f),
        )
      }

      // Row 4: Явления погоды
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Явления",
          color = palette.textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.width(56.dp),
        )
        Box(
          contentAlignment = Alignment.CenterStart,
          modifier = Modifier
            .weight(1f)
            .background(palette.fieldBackground)
            .border(1.dp, palette.border)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
          Text(
            text = weather.weatherPhenomena,
            color = palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // Row 5: MOR & ВНГО side by side
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        CompactInlineMetric(
          label = "MOR",
          value = weather.visibilityMeters?.toString() ?: "----",
          unit = "м",
          modifier = Modifier.weight(1f),
        )
        CompactInlineMetric(
          label = "ВНГО",
          value = weather.ceilingFeet?.toString() ?: if (weather.isCavok) "CAVOK" else "---",
          unit = if (weather.ceilingFeet != null) "ft" else "",
          modifier = Modifier.weight(1f),
        )
      }

      // Row 6: Cloud tag
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .fillMaxWidth(0.76f)
          .background(palette.secondaryFieldBackground)
          .padding(vertical = 2.dp, horizontal = 8.dp),
      ) {
        Text(
          text = cloudsSummary,
          color = palette.textSecondary,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (windTrend != null) {
        WindTrendIndicator(
          trend = windTrend,
          windUnit = windUnit,
          compactMode = true,
        )
      }
    }
    return
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(5.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    // Время
    Text(
      text = "Время",
      color = palette.textSecondary,
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
    )
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth(0.55f)
        .background(palette.fieldBackground)
        .border(1.dp, palette.borderStrong)
        .padding(vertical = 5.dp),
    ) {
      Text(
        text = localClock,
        color = palette.textPrimary,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
      )
    }

    // Восход / Заход
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 2.dp),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = "Восход",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.fieldBackground)
            .border(1.dp, palette.border)
            .padding(vertical = 5.dp),
        ) {
          Text(
            text = weather.sunriseLocal,
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f),
      ) {
        Text(
          text = "Заход",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.fieldBackground)
            .border(1.dp, palette.border)
            .padding(vertical = 5.dp),
        ) {
          Text(
            text = weather.sunsetLocal,
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(2.dp))

    // QNH (гПа, мм, дм)
    val (qnhValFull, qnhUnitFull) = UnitConverter.formatPressurePrimary(
      hpa = weather.qnhHpa,
      inHg = weather.qnhInHg,
      unit = pressureUnit,
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onTogglePressureUnit),
    ) {
      InstrumentValueRow(
        label = "QNH",
        value = qnhValFull,
        unit = qnhUnitFull,
        isHighlighted = true,
      )
    }
    InstrumentValueRow(
      label = "",
      value = weather.qnhMmHg?.toString() ?: "---",
      unit = "мм.",
      isHighlighted = false,
    )
    val qnhAltVal = if (pressureUnit == PressureUnit.HPA) {
      weather.qnhInHg?.let { String.format(Locale("ru"), "%.2f", it) } ?: "--,--"
    } else {
      weather.qnhHpa?.toString() ?: "----"
    }
    val qnhAltUnit = if (pressureUnit == PressureUnit.HPA) "дм." else "гПа"
    InstrumentValueRow(
      label = "",
      value = qnhAltVal,
      unit = qnhAltUnit,
      isHighlighted = false,
    )

    // T & DP
    InstrumentValueRow(
      label = "T",
      value = weather.temperatureC?.toString() ?: "--",
      unit = "°C",
      isHighlighted = true,
    )
    InstrumentValueRow(
      label = "DP",
      value = weather.dewPointC?.toString() ?: "--",
      unit = "°C",
      isHighlighted = true,
    )

    // Явления погоды
    Text(
      text = "Явления погоды",
      color = palette.textSecondary,
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.padding(top = 2.dp),
    )
    Box(
      contentAlignment = Alignment.CenterStart,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.fieldBackground)
        .border(1.dp, palette.border)
        .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
      Text(
        text = weather.weatherPhenomena,
        color = palette.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }

    // MOR & ВНГО
    InstrumentValueRow(
      label = "MOR",
      value = weather.visibilityMeters?.toString() ?: "----",
      unit = "м",
      isHighlighted = true,
    )
    InstrumentValueRow(
      label = "ВНГО",
      value = weather.ceilingFeet?.toString() ?: if (weather.isCavok) "CAVOK" else "---",
      unit = if (weather.ceilingFeet != null) "ft" else "",
      isHighlighted = true,
    )

    // Cloud code pill (e.g. BKN230 or all cloud layers)
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth(0.76f)
        .background(palette.secondaryFieldBackground)
        .padding(vertical = 5.dp, horizontal = 8.dp),
    ) {
      Text(
        text = cloudsSummary,
        color = palette.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    if (windTrend != null) {
      WindTrendIndicator(
        trend = windTrend,
        windUnit = windUnit,
        compactMode = false,
      )
    }
  }
}

@Composable
private fun CompactLabeledBox(
  label: String,
  value: String,
  strong: Boolean,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier,
  ) {
    Text(
      text = label,
      color = palette.textSecondary,
      fontSize = 10.5.sp,
    )
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.fieldBackground)
        .border(1.dp, if (strong) palette.borderStrong else palette.border)
        .padding(vertical = 3.dp),
    ) {
      Text(
        text = value,
        color = palette.textPrimary,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
private fun CompactInlineMetric(
  label: String,
  value: String,
  unit: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
  ) {
    Text(
      text = label,
      color = palette.textSecondary,
      fontSize = 11.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.width(34.dp),
    )
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .weight(1f)
        .background(palette.fieldBackground)
        .border(1.dp, palette.borderStrong)
        .padding(vertical = 3.dp, horizontal = 4.dp),
    ) {
      Text(
        text = value,
        color = palette.textPrimary,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }
    if (unit.isNotEmpty()) {
      Text(
        text = unit,
        color = palette.textSecondary,
        fontSize = 10.5.sp,
        modifier = Modifier.padding(start = 3.dp),
      )
    }
  }
}

@Composable
fun InstrumentValueRow(
  label: String,
  value: String,
  unit: String,
  isHighlighted: Boolean,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier.fillMaxWidth(),
  ) {
    Text(
      text = label,
      color = palette.textSecondary,
      fontSize = if (compactMode) 11.5.sp else 13.sp,
      fontWeight = FontWeight.Medium,
      modifier = Modifier.width(if (compactMode) 36.dp else 46.dp),
    )

    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .weight(1f)
        .background(if (isHighlighted) palette.fieldBackground else palette.secondaryFieldBackground)
        .then(
          if (isHighlighted) {
            Modifier.border(1.dp, palette.borderStrong)
          } else {
            Modifier
          },
        )
        .padding(vertical = if (compactMode) 2.5.dp else 5.dp, horizontal = 6.dp),
    ) {
      Text(
        text = value,
        color = if (isHighlighted) palette.textPrimary else palette.textSecondary,
        fontSize = when {
          compactMode && isHighlighted -> 14.sp
          compactMode -> 12.sp
          isHighlighted -> 17.sp
          else -> 14.sp
        },
        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
    }

    Text(
      text = unit,
      color = palette.textSecondary,
      fontSize = if (compactMode) 11.sp else 12.sp,
      modifier = Modifier
        .width(if (compactMode) 28.dp else 34.dp)
        .padding(start = 5.dp),
    )
  }
}

@Composable
fun MetarTafFooter(
  weather: DecodedWeather,
  station: AirportStation,
  onOpenMetarTable: () -> Unit,
  onOpenAboutDialog: () -> Unit = {},
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val labelWidth = if (compactMode) 48.dp else 62.dp
  val textFontSize = if (compactMode) 12.sp else 13.5.sp
  val textLineHeight = if (compactMode) 15.sp else 17.5.sp
  val boxPadV = if (compactMode) 3.dp else 6.dp

  Column(
    verticalArrangement = Arrangement.spacedBy(if (compactMode) 3.dp else 5.dp),
    modifier = modifier.fillMaxWidth(),
  ) {
    // TAF row
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("taf_footer_row"),
    ) {
      Text(
        text = "TAF",
        color = palette.textPrimary,
        fontSize = if (compactMode) 12.sp else 13.5.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.End,
        modifier = Modifier
          .width(labelWidth)
          .padding(end = 6.dp),
      )
      Box(
        modifier = Modifier
          .weight(1f)
          .background(palette.fieldBackground)
          .border(1.dp, palette.border)
          .padding(horizontal = 6.dp, vertical = boxPadV),
      ) {
        Text(
          text = weather.rawTaf,
          color = palette.textPrimary,
          fontSize = textFontSize,
          fontFamily = FontFamily.Monospace,
          lineHeight = textLineHeight,
          maxLines = if (compactMode) 3 else 5,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // METAR row (tappable to open structured METAR table)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("metar_footer_row")
        .clickable(onClick = onOpenMetarTable),
    ) {
      Text(
        text = "METAR",
        color = palette.metarAmber,
        fontSize = if (compactMode) 12.sp else 13.5.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        modifier = Modifier
          .width(labelWidth)
          .padding(end = 6.dp),
      )
      Box(
        modifier = Modifier
          .weight(1f)
          .background(palette.fieldBackground)
          .border(1.dp, palette.border)
          .padding(horizontal = 6.dp, vertical = boxPadV),
      ) {
        Text(
          text = weather.rawMetar,
          color = palette.textPrimary,
          fontSize = textFontSize,
          fontFamily = FontFamily.Monospace,
          lineHeight = textLineHeight,
          maxLines = if (compactMode) 3 else 4,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Status bar
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.statusBarBg)
        .border(1.dp, palette.border)
        .padding(horizontal = 6.dp, vertical = if (compactMode) 2.5.dp else 4.dp),
    ) {
      Text(
        text = "Источник: ${weather.sourceLabel} · Особые условия: ${weather.specialConditions} · наблюдение ${weather.localObsTime} (${station.timezoneLabel}) · получено ${weather.receivedTimeLocal}",
        color = palette.textSecondary,
        fontSize = if (compactMode) 10.sp else 11.sp,
        lineHeight = if (compactMode) 12.sp else 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }

    // Discreet app owner & application info footer
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(3.dp))
        .clickable(onClick = onOpenAboutDialog)
        .padding(horizontal = 4.dp, vertical = if (compactMode) 1.dp else 2.dp)
        .testTag("board_owner_footer"),
    ) {
      Text(
        text = "${com.example.model.AuthorBrand.APP_TITLE} v${com.example.model.AuthorBrand.APP_VERSION}",
        color = palette.textMuted,
        fontSize = if (compactMode) 9.5.sp else 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier.padding(end = 8.dp),
      )
      Text(
        text = com.example.model.AuthorBrand.COPYRIGHT_LINE,
        color = palette.textMuted,
        fontSize = if (compactMode) 9.5.sp else 10.sp,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
    }
  }
}
