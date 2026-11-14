package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AirportCatalog
import com.example.data.ServerConfig
import com.example.model.AirportStation
import com.example.model.LogEntry
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
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
  onSelectStation: (String) -> Unit,
  onApplyCustomMetar: (String, String) -> Unit,
  onSelectWindUnit: (WindSpeedUnit) -> Unit,
  onSelectPressureUnit: (PressureUnit) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onSaveServerConfig: (ServerConfig) -> Unit,
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  var selectedTab by remember { mutableIntStateOf(0) }
  var customIcao by remember { mutableStateOf(currentStation.icao) }
  var metarInput by remember { mutableStateOf(currentMetar) }
  var tafInput by remember { mutableStateOf(currentTaf) }
  var serverUrlInput by remember(serverConfig) { mutableStateOf(serverConfig.url) }
  var serverUserInput by remember(serverConfig) { mutableStateOf(serverConfig.username) }
  var serverPassInput by remember(serverConfig) { mutableStateOf(serverConfig.password) }
  var useCustomServer by remember(serverConfig) { mutableStateOf(serverConfig.useCustomServer) }
  var fallbackToNoaa by remember(serverConfig) { mutableStateOf(serverConfig.fallbackToNoaa) }
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
          .heightIn(max = 260.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Аэродромы") },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Ввод METAR") },
          )
          Tab(
            selected = selectedTab == 2,
            onClick = { selectedTab = 2 },
            text = { Text("Сервер") },
            modifier = Modifier.testTag("dialog_tab_server"),
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
                      modifier = Modifier.height(14.dp),
                    )
                    Text(
                      text = fav,
                      color = palette.textPrimary,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                    )
                  }
                  Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Удалить $fav",
                    tint = palette.textMuted,
                    modifier = Modifier
                      .clickable { onToggleFavorite(fav) }
                      .padding(start = 4.dp, end = 8.dp, top = 5.dp, bottom = 5.dp)
                      .height(14.dp),
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

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
        } else {
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
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Использовать этот сервер",
              color = palette.textPrimary,
              fontSize = 13.sp,
            )
            Switch(
              checked = useCustomServer,
              onCheckedChange = { useCustomServer = it },
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Резерв: aviationweather.gov",
              color = palette.textSecondary,
              fontSize = 12.sp,
            )
            Switch(
              checked = fallbackToNoaa,
              onCheckedChange = { fallbackToNoaa = it },
            )
          }
        }
      }
    },
    confirmButton = {
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
                useCustomServer = useCustomServer,
                fallbackToNoaa = fallbackToNoaa,
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
