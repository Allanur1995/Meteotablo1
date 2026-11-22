package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.diagnostics.CrashReporter
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.LogEntry
import com.example.model.PressureUnit
import com.example.model.SavedWeatherPreset
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherChangeAlert
import com.example.model.WindSpeedUnit
import com.example.notifications.AndroidWeatherNotifier
import com.example.ui.theme.LocalMeteoPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StationAndMetarDialog(
  currentStation: AirportStation,
  currentMetar: String,
  currentTaf: String,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  favoriteIcaos: List<String>,
  serverConfig: ServerConfig,
  selectedRunwayPairIndex: Int = 0,
  savedPresets: List<SavedWeatherPreset> = emptyList(),
  onSaveCurrentSetup: (String?) -> Unit = {},
  onApplySavedPreset: (String) -> Unit = {},
  onDeleteSavedPreset: (String) -> Unit = {},
  onSelectStation: (String) -> Unit,
  onApplyCustomMetar: (String, String) -> Unit,
  onSelectWindUnit: (WindSpeedUnit) -> Unit,
  onSelectPressureUnit: (PressureUnit) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onSaveServerConfig: (ServerConfig) -> Unit,
  onDismiss: () -> Unit,
  initialTab: Int = 0,
) {
  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
  var customIcao by remember { mutableStateOf(currentStation.icao) }
  var presetNameInput by remember { mutableStateOf("") }
  var metarInput by remember { mutableStateOf(currentMetar) }
  var tafInput by remember { mutableStateOf(currentTaf) }
  var serverUrlInput by remember(serverConfig) { mutableStateOf(serverConfig.url) }
  var serverUserInput by remember(serverConfig) { mutableStateOf(serverConfig.username) }
  var serverPassInput by remember(serverConfig) { mutableStateOf(serverConfig.password) }
  var sourceMode by remember(serverConfig) { mutableStateOf(serverConfig.resolvedMode()) }
  var useAwc by remember(serverConfig) { mutableStateOf(serverConfig.useAviationWeatherGov) }
  var useTgftp by remember(serverConfig) { mutableStateOf(serverConfig.useNoaaTgftp) }
  var useVatsim by remember(serverConfig) { mutableStateOf(serverConfig.useVatsimMetar) }
  var showPassword by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Аэродром и данные METAR",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 380.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Аэродромы", maxLines = 1, fontSize = 11.sp) },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("METAR", maxLines = 1, fontSize = 11.sp) },
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("Сервер", maxLines = 1, fontSize = 11.sp) },
            modifier = Modifier.testTag("dialog_tab_server"),
          )
          Tab(
            selected = selectedTab == 3,
            onClick = { selectedTab = 3 },
            text = { Text("Автор", maxLines = 1, fontSize = 11.sp) },
            modifier = Modifier.testTag("dialog_tab_about"),
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
          if (favoriteIcaos.isNotEmpty()) {
            Text(
              text = "Избранные аэропорты:",
              color = palette.textSecondary,
              fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              favoriteIcaos.forEach { fav ->
                val isSelected = fav == currentStation.icao
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) palette.buttonActiveBackground else palette.fieldBackground)
                    .border(1.dp, if (isSelected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp)),
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                      .clickable {
                        onSelectStation(fav)
                        onDismiss()
                      }
                      .padding(start = 8.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Star,
                      contentDescription = null,
                      tint = palette.metarAmber,
                      modifier = Modifier.size(14.dp),
                    )
                    Text(
                      text = fav,
                      color = palette.textPrimary,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .clickable { onToggleFavorite(fav) }
                      .padding(horizontal = 6.dp, vertical = 6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Удалить $fav",
                      tint = palette.textMuted,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          val activePair = currentStation.runways.getOrElse(selectedRunwayPairIndex) { currentStation.runways.first() }
          val activeRunwayLabel = "${activePair.primaryDesignator}/${activePair.reciprocalDesignator}"
          Text(
            text = "Сохранённые профили (Аэропорт + ВПП):",
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Текущая: ${currentStation.icao} · ВПП $activeRunwayLabel · ${windUnit.label}, ${pressureUnit.label}",
            color = palette.metarAmber,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = presetNameInput,
              onValueChange = { presetNameInput = it },
              label = { Text("Название (необяз.)") },
              singleLine = true,
              modifier = Modifier
                .weight(1f)
                .testTag("preset_name_input"),
            )
            Button(
              onClick = {
                onSaveCurrentSetup(presetNameInput.trim().takeIf { it.isNotBlank() })
                presetNameInput = ""
              },
              modifier = Modifier.testTag("save_weather_preset_button"),
            ) {
              Text("Сохранить ВПП", fontSize = 11.sp)
            }
          }
          if (savedPresets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              savedPresets.forEach { preset ->
                val isCurrentPreset = preset.icao == currentStation.icao &&
                  preset.runwayPairIndex == selectedRunwayPairIndex &&
                  preset.windUnit == windUnit &&
                  preset.pressureUnit == pressureUnit
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isCurrentPreset) palette.buttonActiveBackground else palette.fieldBackground)
                    .border(1.dp, if (isCurrentPreset) palette.borderStrong else palette.border, RoundedCornerShape(4.dp)),
                ) {
                  Text(
                    text = preset.name,
                    color = palette.textPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrentPreset) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                      .testTag("recall_preset_${preset.id}")
                      .clickable {
                        onApplySavedPreset(preset.id)
                        onDismiss()
                      }
                      .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                  )
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                      .testTag("delete_preset_${preset.id}")
                      .clickable { onDeleteSavedPreset(preset.id) }
                      .padding(horizontal = 6.dp, vertical = 6.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Удалить ${preset.name}",
                      tint = palette.textMuted,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Быстрый выбор (Туркменистан и др.):",
            color = palette.textSecondary,
            fontSize = 12.sp,
          )
          Spacer(modifier = Modifier.height(8.dp))

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            AirportCatalog.presets.forEach { preset ->
              val isSelected = preset.icao == currentStation.icao
              Column(
                modifier = Modifier
                  .testTag("station_chip_${preset.icao}")
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isSelected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                  .border(
                    width = 1.dp,
                    color = if (isSelected) palette.borderStrong else palette.border,
                    shape = RoundedCornerShape(4.dp),
                  )
                  .clickable {
                    onSelectStation(preset.icao)
                    onDismiss()
                  }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              ) {
                Text(
                  text = preset.icao,
                  color = palette.textPrimary,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = preset.city,
                  color = palette.textSecondary,
                  fontSize = 11.sp,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = customIcao,
              onValueChange = { if (it.length <= 4) customIcao = it.uppercase() },
              label = { Text("Код ICAO (4 буквы)") },
              singleLine = true,
              modifier = Modifier
                .weight(1f)
                .testTag("icao_input_field"),
            )
            val cleanIcao = customIcao.trim().uppercase()
            val isSaved = cleanIcao.length == 4 && cleanIcao in favoriteIcaos
            IconButton(
              enabled = cleanIcao.length == 4,
              onClick = { onToggleFavorite(cleanIcao) },
              modifier = Modifier.testTag("dialog_save_favorite_button"),
            ) {
              Icon(
                imageVector = if (isSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Сохранить в избранное",
                tint = if (isSaved) palette.metarAmber else palette.textSecondary,
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Единицы скорости ветра:",
            color = palette.textSecondary,
            fontSize = 12.sp,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            WindSpeedUnit.entries.forEach { unit ->
              val selected = unit == windUnit
              Text(
                text = unit.label,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                  .weight(1f)
                  .testTag("dialog_wind_unit_${unit.name}")
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                  .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                  .clickable { onSelectWindUnit(unit) }
                  .padding(vertical = 8.dp, horizontal = 8.dp),
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Единицы давления QNH / QFE:",
            color = palette.textSecondary,
            fontSize = 12.sp,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            PressureUnit.entries.forEach { unit ->
              val selected = unit == pressureUnit
              val label = if (unit == PressureUnit.HPA) "hPa (гПа)" else "inHg (дм)"
              Text(
                text = label,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                  .weight(1f)
                  .testTag("dialog_pressure_unit_${unit.name}")
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                  .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                  .clickable { onSelectPressureUnit(unit) }
                  .padding(vertical = 8.dp, horizontal = 8.dp),
              )
            }
          }
        } else if (selectedTab == 1) {
          OutlinedTextField(
            value = metarInput,
            onValueChange = { metarInput = it },
            label = { Text("Строка METAR") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("custom_metar_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = tafInput,
            onValueChange = { tafInput = it },
            label = { Text("Строка TAF (необязательно)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
          )
        } else if (selectedTab == 2) {
          Text(
            text = "Режим источника данных:",
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            DataSourceMode.entries.forEach { mode ->
              val selected = mode == sourceMode
              Text(
                text = mode.displayName,
                color = palette.textPrimary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                  .testTag("dialog_source_mode_${mode.name}")
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                  .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                  .clickable { sourceMode = mode }
                  .padding(vertical = 6.dp, horizontal = 8.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = serverUrlInput,
            onValueChange = { serverUrlInput = it },
            label = { Text("URL / IP сервера") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("server_url_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = serverUserInput,
              onValueChange = { serverUserInput = it },
              label = { Text("Логин") },
              singleLine = true,
              modifier = Modifier
                .weight(1f)
                .testTag("server_user_input"),
            )
            OutlinedTextField(
              value = serverPassInput,
              onValueChange = { serverPassInput = it },
              label = { Text("Пароль") },
              singleLine = true,
              visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                  Icon(
                    imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Показать пароль",
                  )
                }
              },
              modifier = Modifier
                .weight(1f)
                .testTag("server_pass_input"),
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Интернет-сайты METAR / TAF:",
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(text = "aviationweather.gov", color = palette.textPrimary, fontSize = 12.sp)
            Switch(checked = useAwc, onCheckedChange = { useAwc = it })
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(text = "NOAA (tgftp.nws.noaa.gov)", color = palette.textPrimary, fontSize = 12.sp)
            Switch(checked = useTgftp, onCheckedChange = { useTgftp = it })
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(text = "VATSIM (metar.vatsim.net)", color = palette.textPrimary, fontSize = 12.sp)
            Switch(checked = useVatsim, onCheckedChange = { useVatsim = it })
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · ${AuthorBrand.COPYRIGHT_LINE}",
            color = palette.textMuted,
            fontSize = 10.sp,
          )
        } else {
          val pkgOk = AuthorBrand.isExpectedBrand(
            runtimeTitle = AuthorBrand.APP_TITLE,
            runtimePackageId = context.packageName,
          )
          Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(palette.secondaryFieldBackground)
              .border(1.dp, palette.border, RoundedCornerShape(6.dp))
              .padding(12.dp)
              .testTag("about_author_card"),
          ) {
            Text(
              text = "${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION}",
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Автор: ${AuthorBrand.AUTHOR_NAME} (${AuthorBrand.AUTHOR_HANDLE})",
              color = palette.metarAmber,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Email: ${AuthorBrand.AUTHOR_EMAIL}",
              color = palette.textSecondary,
              fontSize = 12.sp,
            )
            Text(
              text = "GitHub: ${AuthorBrand.AUTHOR_URL}",
              color = palette.textSecondary,
              fontSize = 12.sp,
            )
            Text(
              text = "ID сборки: ${context.packageName}" + if (pkgOk) " (Оригинал)" else "",
              color = palette.textMuted,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
            )
            Text(
              text = AuthorBrand.OWNERSHIP_NOTICE,
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
            val diag = remember(selectedTab) { CrashReporter.getDiagnosticsSnapshot(context) }
            Text(
              text = if (diag.isCrashlyticsActive) {
                "Firebase Crashlytics: активен · этап: ${diag.currentLaunchStage} · событий: ${diag.breadcrumbCount}"
              } else {
                "Диагностика сбоев: локальный режим · этап: ${diag.currentLaunchStage}"
              },
              color = if (diag.isCrashlyticsActive) palette.metarAmber else palette.textMuted,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.testTag("crashlytics_status_text"),
            )
            diag.lastCrashSummary?.let { lastCrash ->
              Text(
                text = "Последний сбой: $lastCrash",
                color = palette.tailwindNegative,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      if (selectedTab != 3) {
        Button(
          onClick = {
            when (selectedTab) {
              0 -> {
                if (customIcao.trim().length == 4) {
                  onSelectStation(customIcao.trim())
                }
              }
              1 -> onApplyCustomMetar(metarInput, tafInput)
              else -> onSaveServerConfig(
                ServerConfig(
                  url = serverUrlInput,
                  username = serverUserInput,
                  password = serverPassInput,
                  useCustomServer = sourceMode != DataSourceMode.INTERNET_ONLY,
                  fallbackToNoaa = sourceMode != DataSourceMode.IP_ONLY,
                  sourceMode = sourceMode,
                  useAviationWeatherGov = useAwc,
                  useNoaaTgftp = useTgftp,
                  useVatsimMetar = useVatsim,
                ),
              )
            }
            onDismiss()
          },
          modifier = Modifier.testTag("confirm_station_dialog"),
        ) {
          Text(
            when (selectedTab) {
              0 -> "Выбрать"
              1 -> "Декодировать"
              else -> "Сохранить и загрузить"
            },
          )
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Закрыть")
      }
    },
  )
}

@Composable
fun LogViewerDialog(
  logs: List<LogEntry>,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Журнал событий (Лог)",
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      if (logs.isEmpty()) {
        Text("Записей пока нет.", color = palette.textSecondary)
      } else {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
        ) {
          items(logs) { entry ->
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
                .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
                .padding(8.dp),
            ) {
              Text(
                text = "[${entry.timestamp}] ${entry.level}",
                color = if (entry.level == "ERR") palette.tailwindNegative else palette.metarAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
              )
              Text(
                text = entry.message,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Закрыть")
      }
    },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherAlertsDialog(
  favoriteIcaos: List<String>,
  alertConfig: WeatherAlertConfig,
  alerts: List<WeatherChangeAlert>,
  isCheckingFavorites: Boolean,
  onUpdateConfig: (WeatherAlertConfig) -> Unit,
  onCheckFavoritesNow: () -> Unit,
  onOpenAlertStation: (String) -> Unit,
  onMarkAllRead: () -> Unit,
  onClearAlerts: () -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  var hasPermission by remember {
    mutableStateOf(AndroidWeatherNotifier.hasNotificationPermission(context))
  }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasPermission = granted
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.NotificationsActive,
          contentDescription = null,
          tint = palette.metarAmber,
        )
        Text(
          text = "Метеооповещения избранных",
          fontSize = 17.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 390.dp)
          .verticalScroll(rememberScrollState())
          .testTag("weather_alerts_dialog"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Мониторинг избранных (${favoriteIcaos.size})",
              color = palette.textPrimary,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = if (favoriteIcaos.isEmpty()) "Добавьте аэропорты в избранное ★"
              else favoriteIcaos.joinToString(" · "),
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
          }
          Switch(
            checked = alertConfig.enabled,
            onCheckedChange = { enabled ->
              onUpdateConfig(alertConfig.copy(enabled = enabled))
              if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            },
            modifier = Modifier.testTag("alerts_enabled_switch"),
          )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
              .fillMaxWidth()
              .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.metarAmber, RoundedCornerShape(4.dp))
              .padding(8.dp),
          ) {
            Text(
              text = "Для push-уведомлений нужно разрешение Android",
              color = palette.metarAmber,
              fontSize = 11.sp,
              modifier = Modifier.weight(1f),
            )
            TextButton(
              onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
              modifier = Modifier.testTag("request_notification_permission_button"),
            ) {
              Text("Разрешить", fontSize = 11.sp)
            }
          }
        }

        Text(
          text = "Порог по ветру (сдвиг/порывы):",
          color = palette.textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(5, 8, 12).forEach { kt ->
            val selected = alertConfig.windChangeThresholdKt == kt
            Text(
              text = "±$kt kt",
              color = palette.textPrimary,
              fontSize = 11.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier
                .testTag("alert_wind_threshold_$kt")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                .clickable { onUpdateConfig(alertConfig.copy(windChangeThresholdKt = kt)) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
            )
          }
        }

        Text(
          text = "Порог видимости / ВНГО:",
          color = palette.textSecondary,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(1500 to 150, 3000 to 300, 5000 to 450).forEach { (visM, ceilM) ->
            val selected = alertConfig.visibilityThresholdMeters == visM
            Text(
              text = "< ${visM}м / ${ceilM}м",
              color = palette.textPrimary,
              fontSize = 11.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier
                .testTag("alert_vis_threshold_$visM")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(1.dp, if (selected) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
                .clickable {
                  onUpdateConfig(
                    alertConfig.copy(
                      visibilityThresholdMeters = visM,
                      ceilingThresholdMeters = ceilM,
                    ),
                  )
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            )
          }
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            enabled = favoriteIcaos.isNotEmpty() && !isCheckingFavorites,
            onClick = onCheckFavoritesNow,
            modifier = Modifier
              .weight(1f)
              .testTag("check_favorites_weather_button"),
          ) {
            Text(
              text = if (isCheckingFavorites) "Проверка..." else "Проверить избранные",
              fontSize = 11.sp,
            )
          }
          if (alerts.isNotEmpty()) {
            OutlinedButton(
              onClick = onClearAlerts,
              modifier = Modifier.testTag("clear_alerts_button"),
            ) {
              Text("Очистить", fontSize = 11.sp)
            }
          }
        }

        if (alerts.isEmpty()) {
          Text(
            text = "Пока нет зафиксированных изменений погоды для избранных аэродромов.",
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 8.dp),
          )
        } else {
          alerts.forEach { alert ->
            val borderColor = when (alert.severity) {
              WeatherAlertSeverity.CRITICAL -> palette.tailwindNegative
              WeatherAlertSeverity.WARNING -> palette.metarAmber
              WeatherAlertSeverity.INFO -> palette.borderStrong
            }
            Column(
              verticalArrangement = Arrangement.spacedBy(3.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("alert_item_${alert.id}")
                .clip(RoundedCornerShape(4.dp))
                .background(palette.secondaryFieldBackground)
                .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                .clickable {
                  onOpenAlertStation(alert.icao)
                  onDismiss()
                }
                .padding(8.dp),
            ) {
              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = "${alert.icao} (${alert.airportName}) · ${alert.severity.label}",
                  color = borderColor,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "${alert.observationTimeUtc} (${alert.timestampLocal})",
                  color = palette.textMuted,
                  fontSize = 10.sp,
                )
              }
              Text(
                text = alert.summary,
                color = palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
              )
              Text(
                text = alert.rawMetar,
                color = palette.textSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
      }
    },
    confirmButton = {
      if (alerts.any { !it.isRead }) {
        TextButton(
          onClick = {
            onMarkAllRead()
            onDismiss()
          },
          modifier = Modifier.testTag("mark_alerts_read_button"),
        ) {
          Text("Прочитано")
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Закрыть")
      }
    },
  )
}

