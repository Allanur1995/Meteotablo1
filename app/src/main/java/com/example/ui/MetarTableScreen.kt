package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import com.example.ui.components.WindRoseCard
import com.example.ui.theme.LocalMeteoPalette
import java.util.Locale
import kotlin.math.roundToInt

private data class MetarTableRowData(
  val icon: ImageVector,
  val label: String,
  val subLabel: String,
  val primaryValue: String,
  val secondaryDetails: String,
  val rawToken: String,
  val testTag: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetarTableScreen(
  station: AirportStation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  favoriteIcaos: List<String>,
  isCurrentFavorite: Boolean,
  isFullscreen: Boolean,
  onBack: () -> Unit,
  onEditRawMetar: () -> Unit,
  onToggleWindUnit: () -> Unit,
  onTogglePressureUnit: () -> Unit,
  onToggleFavorite: () -> Unit,
  onSelectFavoriteStation: (String) -> Unit,
  onAddFavorite: (String) -> Unit,
  onRemoveFavorite: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val clipboardManager = LocalClipboardManager.current
  var copiedFeedback by remember { mutableStateOf(false) }
  var showAddFavoriteDialog by remember { mutableStateOf(false) }

  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  val tokens = remember(weather.rawMetar) {
    weather.rawMetar.split(" ").filter { it.isNotBlank() }
  }

  val tableRows = remember(weather, station, windUnit, pressureUnit) {
    buildMetarTableRows(station, weather, windUnit, pressureUnit)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 980.dp)
        .align(Alignment.TopCenter)
        .verticalScroll(rememberScrollState()),
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
              .testTag("metar_table_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад к табло",
              tint = palette.textPrimary,
            )
          }

          Column {
            Text(
              text = "Таблица METAR · ${station.icao}",
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${station.name} (${station.city}, ${station.elevationMeters} м)",
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "METAR ${weather.observationTimeUtc}",
            color = palette.metarAmber,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp),
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .testTag("metar_table_unit_toggle")
              .clip(RoundedCornerShape(3.dp))
              .background(palette.buttonBackground)
              .border(1.dp, palette.border, RoundedCornerShape(3.dp))
              .clickable(onClick = onToggleWindUnit)
              .padding(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Text(
              text = windUnit.label,
              color = palette.textPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .testTag("metar_table_pressure_toggle")
              .clip(RoundedCornerShape(3.dp))
              .background(palette.buttonBackground)
              .border(1.dp, palette.border, RoundedCornerShape(3.dp))
              .clickable(onClick = onTogglePressureUnit)
              .padding(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Text(
              text = pressureUnit.label,
              color = palette.textPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }

          IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
              .size(48.dp)
              .testTag("metar_table_favorite_toggle"),
          ) {
            Icon(
              imageVector = if (isCurrentFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
              contentDescription = if (isCurrentFavorite) "Убрать из избранного" else "Сохранить в избранное",
              tint = if (isCurrentFavorite) palette.metarAmber else palette.textSecondary,
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .testTag("metar_table_edit_button")
              .clip(RoundedCornerShape(3.dp))
              .background(palette.buttonBackground)
              .border(1.dp, palette.border, RoundedCornerShape(3.dp))
              .clickable(onClick = onEditRawMetar)
              .padding(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Изменить METAR",
              tint = palette.textPrimary,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Ввод",
              color = palette.textPrimary,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
            )
          }
        }
      }

      // Favorite Airports Quick-Access Bar
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("favorite_airports_bar")
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Star,
          contentDescription = "Избранные аэропорты",
          tint = palette.metarAmber,
          modifier = Modifier.size(15.dp),
        )
        Text(
          text = "Избранные:",
          color = palette.textSecondary,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
        )

        if (favoriteIcaos.isEmpty()) {
          Text(
            text = "нет сохранённых ICAO",
            color = palette.textMuted,
            fontSize = 11.5.sp,
          )
        } else {
          favoriteIcaos.forEach { icao ->
            val isActive = icao == station.icao
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(if (isActive) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(
                  1.dp,
                  if (isActive) palette.borderStrong else palette.border,
                  RoundedCornerShape(3.dp),
                ),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .testTag("favorite_chip_$icao")
                  .clickable { onSelectFavoriteStation(icao) }
                  .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
              ) {
                Text(
                  text = icao,
                  color = palette.textPrimary,
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                )
              }
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .testTag("remove_favorite_$icao")
                  .clickable { onRemoveFavorite(icao) }
                  .padding(start = 4.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Удалить $icao из избранного",
                  tint = palette.textMuted,
                  modifier = Modifier.size(14.dp),
                )
              }
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(3.dp),
          modifier = Modifier
            .testTag("add_favorite_chip")
            .clip(RoundedCornerShape(3.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.border, RoundedCornerShape(3.dp))
            .clickable { showAddFavoriteDialog = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Добавить ICAO в избранное",
            tint = palette.metarAmber,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "ICAO",
            color = palette.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      // Raw METAR Banner & Token Strip
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(10.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "ИСХОДНАЯ СВОДКА METAR (RAW)",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
          )
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .testTag("copy_raw_metar_button")
              .clip(RoundedCornerShape(3.dp))
              .background(palette.secondaryFieldBackground)
              .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
              .clickable {
                clipboardManager.setText(AnnotatedString(weather.rawMetar))
                copiedFeedback = true
              }
              .padding(horizontal = 8.dp, vertical = 4.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = "Копировать METAR",
              tint = palette.textSecondary,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = if (copiedFeedback) "Скопировано" else "Копировать",
              color = if (copiedFeedback) palette.headwindPositive else palette.textPrimary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
            )
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("raw_metar_full_text")
            .background(palette.fieldBackground)
            .border(1.dp, palette.borderStrong)
            .padding(10.dp),
        ) {
          Text(
            text = weather.rawMetar,
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
          )
        }

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          tokens.forEach { token ->
            val category = classifyMetarToken(token)
            val isKeyMetric = category == "Ветер" || category == "Видимость" || category == "QNH"
            Column(
              modifier = Modifier
                .background(
                  if (isKeyMetric) palette.buttonActiveBackground else palette.secondaryFieldBackground,
                  RoundedCornerShape(3.dp),
                )
                .border(
                  0.8.dp,
                  if (isKeyMetric) palette.borderStrong else palette.border,
                  RoundedCornerShape(3.dp),
                )
                .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
              Text(
                text = token,
                color = palette.textPrimary,
                fontSize = 11.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = category,
                color = if (isKeyMetric) palette.metarAmber else palette.textMuted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Medium,
              )
            }
          }
        }
      }

      // Graphical Wind Rose Card (Canvas)
      WindRoseCard(
        wind = weather.wind,
        windUnit = windUnit,
        onToggleUnit = onToggleWindUnit,
      )

      // Structured Readable METAR Table
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("metar_structured_table")
          .background(palette.panelSurface)
          .border(1.dp, palette.border),
      ) {
        // Table Column Header
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.secondaryFieldBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
          Text(
            text = "ПАРАМЕТР",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1.1f),
          )
          Text(
            text = "ЗНАЧЕНИЕ / РАСШИФРОВКА",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1.5f),
          )
          Text(
            text = "КОД",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.65f),
          )
        }

        HorizontalDivider(thickness = 1.dp, color = palette.borderStrong)

        tableRows.forEachIndexed { index, row ->
          MetarTableRowItem(
            row = row,
            isAlternate = index % 2 == 1,
          )
          if (index < tableRows.lastIndex) {
            HorizontalDivider(thickness = 0.8.dp, color = palette.border)
          }
        }
      }

      // Footer Status
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.statusBarBg)
          .border(1.dp, palette.border)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text(
          text = "Источник: ${weather.sourceLabel} · наблюдение ${weather.localObsTime} (${station.timezoneLabel}) · получено ${weather.receivedTimeLocal}",
          color = palette.textSecondary,
          fontSize = 11.sp,
        )
      }
    }
  }

  if (showAddFavoriteDialog) {
    var newIcao by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showAddFavoriteDialog = false },
      title = { Text("Добавить аэропорт (ICAO)") },
      text = {
        OutlinedTextField(
          value = newIcao,
          onValueChange = { filtered ->
            val clean = filtered.uppercase().filter { it.isLetter() }.take(4)
            newIcao = clean
          },
          label = { Text("Код ICAO (4 буквы, напр. UTAV)") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("add_favorite_icao_input"),
        )
      },
      confirmButton = {
        Button(
          enabled = newIcao.length == 4,
          onClick = {
            onAddFavorite(newIcao)
            showAddFavoriteDialog = false
          },
          modifier = Modifier.testTag("confirm_add_favorite_button"),
        ) {
          Text("Сохранить")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddFavoriteDialog = false }) {
          Text("Отмена")
        }
      },
    )
  }
}

@Composable
private fun MetarTableRowItem(
  row: MetarTableRowData,
  isAlternate: Boolean,
) {
  val palette = LocalMeteoPalette.current

  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .testTag(row.testTag)
      .background(if (isAlternate) palette.secondaryFieldBackground.copy(alpha = 0.45f) else palette.fieldBackground)
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    // Column 1: Clear Icon + Parameter Label
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .weight(1.1f)
        .padding(end = 8.dp),
    ) {
      Icon(
        imageVector = row.icon,
        contentDescription = row.label,
        tint = palette.metarAmber,
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

    // Column 2: Decoded Value + Details
    Column(
      modifier = Modifier
        .weight(1.5f)
        .padding(end = 8.dp),
    ) {
      Text(
        text = row.primaryValue,
        color = palette.textPrimary,
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
    }

    // Column 3: Raw METAR Token Badge
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

private fun buildMetarTableRows(
  station: AirportStation,
  weather: DecodedWeather,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
): List<MetarTableRowData> {
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

  val visPrimary = when {
    weather.isCavok -> "≥ 10 км (CAVOK)"
    weather.visibilityMeters != null && weather.visibilityMeters >= 9999 -> "≥ 10 км (9999 м)"
    weather.visibilityMeters != null -> "${weather.visibilityMeters} м"
    else -> "Нет данных"
  }
  val rvrDetail = buildString {
    append("Явления: ${weather.weatherPhenomena}")
    if (weather.rvrPrimaryMeters != null) {
      append(" · RVR ${weather.rvrPrimaryMeters} м")
    }
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
      append("\nQFE (${station.elevationMeters} м): $qfeVal $qfeUnit / ${weather.qfeMmHg} мм")
    }
  }

  val tempPrimary = if (weather.temperatureC != null && weather.dewPointC != null) {
    "${weather.temperatureC} °C / ${weather.dewPointC} °C"
  } else {
    "-- / --"
  }
  val spreadDetail = if (weather.temperatureC != null && weather.dewPointC != null) {
    "Дефицит точки росы: ${weather.temperatureC - weather.dewPointC} °C"
  } else {
    ""
  }

  val ceilingPrimary = weather.ceilingMeters?.let { "ВНГО $it м" } ?: if (weather.isCavok) "CAVOK" else "Без значимой облачности"
  val cloudsDetail = if (weather.clouds.isNotEmpty()) {
    weather.clouds.joinToString(", ") { "${it.cover} ${it.baseMeters} м (${it.baseFeet} ft)" }
  } else {
    "Слои облачности не зафиксированы"
  }

  return listOf(
    MetarTableRowData(
      icon = Icons.Filled.Air,
      label = "Ветер",
      subLabel = "Wind",
      primaryValue = "$windDirText, $windSpeedPrimary",
      secondaryDetails = "$allWindConversions$gustPart$varPart",
      rawToken = windToken,
      testTag = "metar_row_wind",
    ),
    MetarTableRowData(
      icon = Icons.Filled.Visibility,
      label = "Видимость",
      subLabel = "Visibility (MOR)",
      primaryValue = visPrimary,
      secondaryDetails = rvrDetail,
      rawToken = visToken,
      testTag = "metar_row_visibility",
    ),
    MetarTableRowData(
      icon = Icons.Filled.Compress,
      label = "Давление QNH",
      subLabel = "Altimeter / QNH",
      primaryValue = qnhPrimary,
      secondaryDetails = qnhSecondary,
      rawToken = qnhToken,
      testTag = "metar_row_qnh",
    ),
    MetarTableRowData(
      icon = Icons.Filled.Cloud,
      label = "Облачность",
      subLabel = "Clouds / ВНГО",
      primaryValue = ceilingPrimary,
      secondaryDetails = cloudsDetail,
      rawToken = cloudTokens,
      testTag = "metar_row_clouds",
    ),
    MetarTableRowData(
      icon = Icons.Filled.Thermostat,
      label = "Температура / DP",
      subLabel = "Temp / Dew Point",
      primaryValue = tempPrimary,
      secondaryDetails = spreadDetail,
      rawToken = tempToken,
      testTag = "metar_row_temp",
    ),
    MetarTableRowData(
      icon = Icons.Filled.WaterDrop,
      label = "Состояние ВПП",
      subLabel = "Runway / КСц",
      primaryValue = weather.frictionCoeff?.let { String.format(Locale.US, "КСц %.2f", it) } ?: "Нет данных",
      secondaryDetails = "Особые условия: ${weather.specialConditions}",
      rawToken = rwyStateToken,
      testTag = "metar_row_runway",
    ),
    MetarTableRowData(
      icon = Icons.Filled.Schedule,
      label = "Время наблюдения",
      subLabel = "Observation Time",
      primaryValue = "${weather.observationTimeUtc} (${weather.localObsTime} ${station.timezoneLabel})",
      secondaryDetails = "Восход ${weather.sunriseLocal} · Заход ${weather.sunsetLocal}",
      rawToken = obsToken,
      testTag = "metar_row_time",
    ),
  )
}

private fun classifyMetarToken(token: String): String {
  return when {
    token == "METAR" || token == "SPECI" -> "Тип"
    Regex("^[A-Z]{4}$").matches(token) && token !in setOf("CAVOK", "NOSIG", "TEMPO", "BECMG") -> "ICAO"
    Regex("^\\d{6}Z$").matches(token) -> "Время"
    Regex("^(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?(KT|MPS)$").matches(token) -> "Ветер"
    Regex("^\\d{3}V\\d{3}$").matches(token) -> "Сектор"
    token == "CAVOK" || Regex("^(\\d{4}|\\d+SM)$").matches(token) -> "Видимость"
    Regex("^(FEW|SCT|BKN|OVC|VV)\\d{3}.*$").matches(token) -> "Облака"
    Regex("^M?\\d{2}/M?\\d{2}$").matches(token) -> "T / DP"
    Regex("^[QA]\\d{4}$").matches(token) -> "QNH"
    Regex("^R\\d{2}[LRC]?/.*$").matches(token) -> "ВПП"
    token in setOf("NOSIG", "TEMPO", "BECMG") -> "Тренд"
    else -> "Код"
  }
}
