package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AirportCatalog
import com.example.ui.theme.LocalMeteoPalette

@Composable
fun AirportSearchBar(
  currentIcao: String,
  favoriteIcaos: List<String>,
  isLoading: Boolean,
  onSelectStation: (String) -> Unit,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current

  var query by remember { mutableStateOf("") }
  var isFocused by remember { mutableStateOf(false) }

  val normalizedLetters = remember(query) {
    query.trim().uppercase().filter { it.isLetterOrDigit() }.take(4)
  }
  val isValidIcao = normalizedLetters.length == 4 && normalizedLetters.all { it.isLetter() }
  val suggestions = remember(query, favoriteIcaos) {
    AirportCatalog.search(query = query, extraIcaos = favoriteIcaos)
  }
  val hasExactSuggestion = suggestions.any { it.icao.equals(normalizedLetters, ignoreCase = true) }

  fun submit(targetIcao: String? = null) {
    val candidate = (targetIcao ?: when {
      isValidIcao -> normalizedLetters
      suggestions.size == 1 && query.isNotBlank() -> suggestions.first().icao
      else -> null
    }) ?: return
    onSelectStation(candidate)
    query = ""
    isFocused = false
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("airport_icao_search_bar"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .background(palette.panelSurface)
        .border(
          width = 1.dp,
          color = if (isFocused) palette.borderStrong else palette.border,
        )
        .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
    ) {
      Icon(
        imageVector = Icons.Filled.Search,
        contentDescription = "Поиск аэропорта по коду ICAO",
        tint = if (isFocused || isValidIcao) palette.metarAmber else palette.textSecondary,
        modifier = Modifier.size(18.dp),
      )

      Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 40.dp)
          .background(palette.fieldBackground, RoundedCornerShape(3.dp))
          .border(0.8.dp, palette.border, RoundedCornerShape(3.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        if (query.isEmpty()) {
          Text(
            text = "Поиск по ICAO (напр. UTAA, OMDB, KJFK)… Сейчас: $currentIcao",
            color = palette.textMuted,
            fontSize = if (compactMode) 11.5.sp else 12.5.sp,
            maxLines = 1,
          )
        }
        BasicTextField(
          value = query,
          onValueChange = { raw ->
            query = raw.uppercase().take(24)
          },
          singleLine = true,
          textStyle = TextStyle(
            color = palette.textPrimary,
            fontSize = if (compactMode) 12.5.sp else 13.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          ),
          cursorBrush = SolidColor(palette.metarAmber),
          keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Search,
          ),
          keyboardActions = KeyboardActions(
            onSearch = { submit() },
          ),
          modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .testTag("airport_icao_search_input"),
        )
      }

      if (query.isNotEmpty()) {
        IconButton(
          onClick = { query = "" },
          modifier = Modifier
            .size(48.dp)
            .testTag("airport_icao_search_clear"),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Очистить поиск ICAO",
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      val canSubmit = isValidIcao || (suggestions.size == 1 && query.isNotBlank())
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("airport_icao_search_submit")
          .clip(RoundedCornerShape(3.dp))
          .background(if (canSubmit) palette.buttonActiveBackground else palette.buttonBackground)
          .border(
            1.dp,
            if (canSubmit) palette.borderStrong else palette.border,
            RoundedCornerShape(3.dp),
          )
          .clickable(enabled = canSubmit) { submit() }
          .padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = palette.metarAmber,
            modifier = Modifier.size(14.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.FlightTakeoff,
            contentDescription = "Загрузить погоду аэропорта",
            tint = if (canSubmit) palette.metarAmber else palette.textMuted,
            modifier = Modifier.size(15.dp),
          )
        }
        Text(
          text = if (isValidIcao) "METAR $normalizedLetters" else "Найти",
          color = if (canSubmit) palette.textPrimary else palette.textMuted,
          fontSize = 12.sp,
          fontWeight = if (canSubmit) FontWeight.Bold else FontWeight.Medium,
          maxLines = 1,
        )
      }
    }

    AnimatedVisibility(visible = isFocused || query.isNotBlank()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 8.dp, vertical = 6.dp)
          .testTag("airport_icao_suggestions_row"),
      ) {
        if (isValidIcao && !hasExactSuggestion) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("airport_search_suggestion_$normalizedLetters")
              .clip(RoundedCornerShape(4.dp))
              .background(palette.buttonActiveBackground)
              .border(1.dp, palette.metarAmber, RoundedCornerShape(4.dp))
              .clickable { submit(normalizedLetters) }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(
              text = "$normalizedLetters · Загрузить METAR",
              color = palette.metarAmber,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        suggestions.forEach { station ->
          val isCurrent = station.icao == currentIcao
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("airport_search_suggestion_${station.icao}")
              .clip(RoundedCornerShape(4.dp))
              .background(
                if (isCurrent) palette.buttonActiveBackground else palette.secondaryFieldBackground,
              )
              .border(
                1.dp,
                if (isCurrent) palette.borderStrong else palette.border,
                RoundedCornerShape(4.dp),
              )
              .clickable { submit(station.icao) }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(
              text = station.icao,
              color = palette.metarAmber,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = station.city,
              color = palette.textPrimary,
              fontSize = 11.5.sp,
            )
          }
        }

        if (suggestions.isEmpty() && !isValidIcao) {
          Text(
            text = "Введите 4 буквы ICAO (например: KJFK, EGLL, UTAA)",
            color = palette.textMuted,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
          )
        }
      }
    }
  }
}
