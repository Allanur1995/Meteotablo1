package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AirportCatalog
import com.example.domain.UnitConverter
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyAirportsBar(
  currentIcao: String,
  myAirports: List<String>,
  weatherByIcao: Map<String, DecodedWeather>,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  onSelectStation: (String) -> Unit,
  onToggleCurrentAirport: () -> Unit,
  onAddAirport: (String) -> Unit,
  onRemoveAirport: (String) -> Unit,
  isNightMode: Boolean = false,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  var showAddDialog by remember { mutableStateOf(false) }
  var showCoordinatePlot by remember { mutableStateOf(false) }
  var autocompleteQuery by remember { mutableStateOf("") }
  var isSearchFocused by remember { mutableStateOf(false) }
  val isCurrentSaved = currentIcao.uppercase() in myAirports

  val cleanQueryLetters = remember(autocompleteQuery) {
    autocompleteQuery.trim().uppercase().filter { it.isLetterOrDigit() }.take(4)
  }
  val isValidCustomIcao = cleanQueryLetters.length == 4 && cleanQueryLetters.all { it.isLetter() }
  val autocompleteMatches = remember(autocompleteQuery, myAirports) {
    AirportCatalog.search(query = autocompleteQuery, extraIcaos = myAirports, limit = 8)
  }
  val hasExactMatch = autocompleteMatches.any {
    it.icao.equals(cleanQueryLetters, ignoreCase = true)
  }

  fun commitAutocompleteAdd(icao: String) {
    val clean = icao.trim().uppercase()
    if (clean.length != 4 || !clean.all { it.isLetter() }) return
    onAddAirport(clean)
    autocompleteQuery = ""
    isSearchFocused = false
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("my_airports_bar")
      .background(palette.panelSurface)
      .border(1.dp, palette.border)
      .padding(horizontal = 10.dp, vertical = if (compactMode) 5.dp else 7.dp),
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
          imageVector = Icons.Filled.Bookmarks,
          contentDescription = "Мои аэропорты",
          tint = palette.metarAmber,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Мои аэропорты",
          color = palette.textPrimary,
          fontSize = if (compactMode) 11.5.sp else 12.5.sp,
          fontWeight = FontWeight.Bold,
        )
        Box(
          modifier = Modifier
            .background(palette.secondaryFieldBackground, RoundedCornerShape(3.dp))
            .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
          Text(
            text = "${myAirports.size}",
            color = palette.metarAmber,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = if (compactMode) 36.dp else 40.dp)
            .testTag("my_airports_toggle_current")
            .clip(RoundedCornerShape(4.dp))
            .background(if (isCurrentSaved) palette.buttonActiveBackground else palette.secondaryFieldBackground)
            .border(1.dp, if (isCurrentSaved) palette.borderStrong else palette.border, RoundedCornerShape(4.dp))
            .clickable(onClick = onToggleCurrentAirport)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = if (isCurrentSaved) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (isCurrentSaved) "Убрать $currentIcao из Моих аэропортов" else "Сохранить $currentIcao в Мои аэропорты",
            tint = if (isCurrentSaved) palette.metarAmber else palette.textSecondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = if (isCurrentSaved) "$currentIcao в моих" else "+ $currentIcao",
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = if (compactMode) 36.dp else 40.dp)
            .testTag("my_airports_add_button")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
            .clickable { showAddDialog = true }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Добавить код ICAO в Мои аэропорты",
            tint = palette.metarAmber,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "ICAO",
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = if (compactMode) 36.dp else 40.dp)
            .testTag("my_airports_plot_toggle")
            .clip(RoundedCornerShape(4.dp))
            .background(if (showCoordinatePlot) palette.buttonActiveBackground else palette.buttonBackground)
            .border(
              1.dp,
              if (showCoordinatePlot) palette.metarAmber else palette.borderStrong,
              RoundedCornerShape(4.dp),
            )
            .clickable { showCoordinatePlot = !showCoordinatePlot }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Map,
            contentDescription = "Координатная карта Моих аэропортов",
            tint = if (showCoordinatePlot) palette.metarAmber else palette.textSecondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Карта",
            color = if (showCoordinatePlot) palette.metarAmber else palette.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    // Inline Search Input with Local ICAO Database Autocomplete for 'My Airports'
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = if (compactMode) 38.dp else 42.dp)
          .background(palette.fieldBackground, RoundedCornerShape(4.dp))
          .border(
            width = 1.dp,
            color = if (isSearchFocused || autocompleteQuery.isNotBlank()) palette.borderStrong else palette.border,
            shape = RoundedCornerShape(4.dp),
          )
          .padding(horizontal = 8.dp, vertical = 4.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.Search,
          contentDescription = "Поиск ICAO для добавления в Мои аэропорты",
          tint = if (isSearchFocused) palette.metarAmber else palette.textMuted,
          modifier = Modifier.size(15.dp),
        )
        Box(
          contentAlignment = Alignment.CenterStart,
          modifier = Modifier.weight(1f),
        ) {
          if (autocompleteQuery.isEmpty()) {
            Text(
              text = "Найти и добавить в «Мои аэропорты» (ICAO, город, напр. EGLL, Ташкент)…",
              color = palette.textMuted,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          BasicTextField(
            value = autocompleteQuery,
            onValueChange = { raw -> autocompleteQuery = raw.take(28) },
            singleLine = true,
            textStyle = TextStyle(
              color = palette.textPrimary,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.SemiBold,
            ),
            cursorBrush = SolidColor(palette.metarAmber),
            keyboardOptions = KeyboardOptions(
              capitalization = KeyboardCapitalization.Characters,
              imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
              onDone = {
                when {
                  autocompleteMatches.size == 1 -> commitAutocompleteAdd(autocompleteMatches.first().icao)
                  isValidCustomIcao -> commitAutocompleteAdd(cleanQueryLetters)
                }
              },
            ),
            modifier = Modifier
              .fillMaxWidth()
              .onFocusChanged { isSearchFocused = it.isFocused }
              .testTag("my_airports_search_input"),
          )
        }
        if (autocompleteQuery.isNotEmpty()) {
          IconButton(
            onClick = { autocompleteQuery = "" },
            modifier = Modifier
              .size(28.dp)
              .testTag("my_airports_search_clear"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Очистить поиск",
              tint = palette.textSecondary,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      AnimatedVisibility(visible = autocompleteQuery.isNotBlank() || isSearchFocused) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
            .verticalScroll(rememberScrollState())
            .background(palette.secondaryFieldBackground, RoundedCornerShape(4.dp))
            .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
            .testTag("my_airports_autocomplete_list"),
        ) {
          if (isValidCustomIcao && !hasExactMatch) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .testTag("my_airports_autocomplete_custom_$cleanQueryLetters")
                .clickable { commitAutocompleteAdd(cleanQueryLetters) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Text(
                text = "$cleanQueryLetters · Добавить код в «Мои аэропорты»",
                color = palette.metarAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
              )
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Добавить $cleanQueryLetters",
                tint = palette.metarAmber,
                modifier = Modifier.size(16.dp),
              )
            }
            if (autocompleteMatches.isNotEmpty()) {
              HorizontalDivider(color = palette.border)
            }
          }

          autocompleteMatches.forEachIndexed { idx, station ->
            val alreadySaved = station.icao in myAirports
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .testTag("my_airports_autocomplete_item_${station.icao}")
                .clickable { commitAutocompleteAdd(station.icao) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Text(
                    text = station.icao,
                    color = palette.metarAmber,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                  )
                  Text(
                    text = station.city,
                    color = palette.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = station.name,
                  color = palette.textSecondary,
                  fontSize = 10.5.sp,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                  .background(
                    if (alreadySaved) palette.buttonActiveBackground else palette.buttonBackground,
                    RoundedCornerShape(3.dp),
                  )
                  .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
                  .padding(horizontal = 8.dp, vertical = 4.dp),
              ) {
                Icon(
                  imageVector = if (alreadySaved) Icons.Filled.Check else Icons.Filled.Add,
                  contentDescription = if (alreadySaved) "Уже в списке" else "Добавить ${station.icao}",
                  tint = if (alreadySaved) palette.headwindPositive else palette.metarAmber,
                  modifier = Modifier.size(13.dp),
                )
                Text(
                  text = if (alreadySaved) "Открыть" else "Добавить",
                  color = palette.textPrimary,
                  fontSize = 10.5.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            if (idx < autocompleteMatches.lastIndex) {
              HorizontalDivider(color = palette.border)
            }
          }

          if (autocompleteMatches.isEmpty() && !isValidCustomIcao) {
            Text(
              text = "Совпадений в локальной базе не найдено. Введите 4 буквы кода ICAO (напр. KJFK).",
              color = palette.textMuted,
              fontSize = 11.sp,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
      }
    }

    if (myAirports.isEmpty()) {
      Text(
        text = "Список «Мои аэропорты» пуст. Используйте поиск выше или кнопку «+ ICAO», чтобы сохранить станции для быстрого доступа.",
        color = palette.textMuted,
        fontSize = 11.5.sp,
        modifier = Modifier
          .testTag("my_airports_empty_tip")
          .padding(vertical = 2.dp),
      )
    } else {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
      ) {
        myAirports.forEach { icao ->
          val cleanIcao = icao.trim().uppercase()
          val isActive = cleanIcao.equals(currentIcao, ignoreCase = true)
          val catalogInfo = remember(cleanIcao) { AirportCatalog.findKnown(cleanIcao) }
          val cached = weatherByIcao[cleanIcao]
          val miniSummary = remember(cached, windUnit, pressureUnit) {
            cached?.let { wx ->
              val windStr = if (wx.wind.isCalm) {
                "0"
              } else {
                UnitConverter.formatWindSpeedValue(wx.wind.speedKt, windUnit)
              }
              val (qnhVal, _) = UnitConverter.formatPressurePrimary(wx.qnhHpa, wx.qnhInHg, pressureUnit)
              "$windStr${windUnit.label} · $qnhVal"
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isActive) palette.buttonActiveBackground else palette.secondaryFieldBackground)
              .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) palette.metarAmber else palette.border,
                shape = RoundedCornerShape(4.dp),
              ),
          ) {
            Column(
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("my_airport_chip_$cleanIcao")
                .clickable { onSelectStation(cleanIcao) }
                .padding(start = 9.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
              ) {
                Text(
                  text = cleanIcao,
                  color = if (isActive) palette.metarAmber else palette.textPrimary,
                  fontSize = 12.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.ExtraBold,
                )
                if (catalogInfo != null && catalogInfo.city.isNotBlank()) {
                  Text(
                    text = catalogInfo.city,
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
              if (miniSummary != null) {
                Text(
                  text = miniSummary,
                  color = palette.textMuted,
                  fontSize = 10.sp,
                  fontFamily = FontFamily.Monospace,
                  maxLines = 1,
                )
              }
            }

            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(width = 34.dp, height = 40.dp)
                .testTag("my_airport_remove_$cleanIcao")
                .clickable { onRemoveAirport(cleanIcao) },
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Удалить $cleanIcao из Моих аэропортов",
                tint = palette.textMuted,
                modifier = Modifier.size(14.dp),
              )
            }
          }
        }
      }
    }

    AnimatedVisibility(visible = showCoordinatePlot) {
      MyAirportsCoordinatePlot(
        myAirports = myAirports,
        currentIcao = currentIcao,
        weatherByIcao = weatherByIcao,
        windUnit = windUnit,
        pressureUnit = pressureUnit,
        isNightMode = isNightMode,
        onSelectStation = onSelectStation,
      )
    }
  }

  if (showAddDialog) {
    var icaoInput by remember { mutableStateOf("") }
    val cleanInput = icaoInput.trim().uppercase().filter { it.isLetterOrDigit() }.take(4)
    val canAdd = cleanInput.length == 4 && cleanInput.all { it.isLetter() }
    val dialogSuggestions = remember(icaoInput, myAirports) {
      val matches = AirportCatalog.search(query = icaoInput, extraIcaos = emptyList(), limit = 10)
      if (icaoInput.isBlank()) matches.filter { it.icao !in myAirports }.take(8) else matches
    }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = {
        Text(
          text = "Добавить в «Мои аэропорты»",
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .testTag("add_my_airport_dialog"),
        ) {
          OutlinedTextField(
            value = icaoInput,
            onValueChange = { raw ->
              icaoInput = raw.take(24)
            },
            label = { Text("Поиск ICAO, города или названия (напр. KJFK, Лондон)") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("my_airports_icao_input"),
          )

          if (dialogSuggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = if (icaoInput.isBlank()) "Популярные аэропорты:" else "Автоподбор из базы ICAO:",
              color = palette.textSecondary,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              dialogSuggestions.forEach { preset ->
                Box(
                  modifier = Modifier
                    .testTag("my_airports_quick_add_${preset.icao}")
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.secondaryFieldBackground)
                    .border(1.dp, palette.border, RoundedCornerShape(4.dp))
                    .clickable {
                      onAddAirport(preset.icao)
                      showAddDialog = false
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                  Text(
                    text = "${preset.icao} · ${preset.city}",
                    color = palette.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                  )
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          enabled = canAdd || dialogSuggestions.size == 1,
          onClick = {
            val target = if (canAdd) cleanInput else dialogSuggestions.first().icao
            onAddAirport(target)
            showAddDialog = false
          },
          modifier = Modifier.testTag("my_airports_confirm_add"),
        ) {
          Text("Сохранить")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Отмена")
        }
      },
    )
  }
}
