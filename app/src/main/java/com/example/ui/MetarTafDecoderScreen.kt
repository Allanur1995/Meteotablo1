package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DecodedCodeItem
import com.example.domain.DecodedMessageResult
import com.example.domain.DecodedTokenSeverity
import com.example.domain.MetarTafDecoder
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.ui.theme.LocalMeteoPalette

private enum class DecoderViewTab(val label: String) {
  ALL("METAR + TAF"),
  METAR("METAR"),
  TAF("TAF"),
}

@Composable
fun MetarTafDecoderScreen(
  station: AirportStation,
  weather: DecodedWeather,
  isFullscreen: Boolean,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val clipboardManager = LocalClipboardManager.current

  var activeTab by remember { mutableStateOf(DecoderViewTab.ALL) }
  var rawMetarText by remember(station.icao, weather.rawMetar) { mutableStateOf(weather.rawMetar) }
  var rawTafText by remember(station.icao, weather.rawTaf) { mutableStateOf(weather.rawTaf) }
  var copiedFeedback by remember { mutableStateOf(false) }

  val decodedMetar = remember(rawMetarText, station) {
    MetarTafDecoder.decodeMetar(rawMetarText, station)
  }
  val decodedTaf = remember(rawTafText, station) {
    MetarTafDecoder.decodeTaf(rawTafText, station)
  }

  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("metar_taf_decoder_screen")
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
      // Top Navigation Bar
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
              .testTag("decoder_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Назад",
              tint = palette.textPrimary,
            )
          }
          Column {
            Text(
              text = "Декодер METAR / TAF · ${station.icao}",
              color = palette.textPrimary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Полная открытая расшифровка каждого авиационного кода простым языком",
              color = palette.textSecondary,
              fontSize = 11.sp,
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
              .testTag("decoder_copy_all_button")
              .clip(RoundedCornerShape(4.dp))
              .background(palette.buttonBackground)
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
              .clickable {
                val report = MetarTafDecoder.formatPlainTextReport(rawMetarText, rawTafText, station)
                clipboardManager.setText(AnnotatedString(report))
                copiedFeedback = true
              }
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = "Копировать расшифровку",
              tint = if (copiedFeedback) palette.headwindPositive else palette.metarAmber,
              modifier = Modifier.size(15.dp),
            )
            Text(
              text = if (copiedFeedback) "Скопировано" else "Копировать",
              color = if (copiedFeedback) palette.headwindPositive else palette.textPrimary,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
      ) {
        // Scope Tabs + Quick Source Controls
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .background(palette.panelSurface, RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .padding(10.dp),
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            DecoderViewTab.entries.forEach { tab ->
              val selected = tab == activeTab
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("decoder_tab_${tab.name}")
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                  .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else palette.border,
                    shape = RoundedCornerShape(4.dp),
                  )
                  .clickable { activeTab = tab }
                  .padding(horizontal = 8.dp, vertical = 8.dp),
              ) {
                Text(
                  text = tab.label,
                  color = if (selected) palette.metarAmber else palette.textPrimary,
                  fontSize = 12.sp,
                  fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                  textAlign = TextAlign.Center,
                )
              }
            }
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = {
                rawMetarText = weather.rawMetar
                rawTafText = weather.rawTaf
                copiedFeedback = false
              },
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .testTag("decoder_load_station_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Сводка ${station.icao}", fontSize = 11.5.sp)
            }

            OutlinedButton(
              onClick = {
                rawMetarText = "METAR UTAA 241430Z 29018G28KT 240V330 1200 R12L/0800U +TSRA FEW008 BKN015CB 19/18 Q1009 R88/290145 WS R12L BECMG 2415/2416 4000 BR NSW NOSIG"
                rawTafText = "TAF AMD UTAA 241400Z 2415/2515 28016G25KT 3000 -TSRA SCT012CB BKN025 TX26/2510Z TN15/2501Z TEMPO 2416/2420 0800 +TSGRRA BKN008CB PROB30 BECMG 2504/2506 CAVOK"
                copiedFeedback = false
              },
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 42.dp)
                .testTag("decoder_load_sample_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.Science,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Пример со СМУ", fontSize = 11.5.sp)
            }
          }

          if (activeTab == DecoderViewTab.ALL || activeTab == DecoderViewTab.METAR) {
            OutlinedTextField(
              value = rawMetarText,
              onValueChange = {
                rawMetarText = it
                copiedFeedback = false
              },
              label = { Text("Введите или вставьте код METAR для расшифровки", fontSize = 11.sp) },
              minLines = 2,
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("decoder_metar_input"),
            )
          }

          if (activeTab == DecoderViewTab.ALL || activeTab == DecoderViewTab.TAF) {
            OutlinedTextField(
              value = rawTafText,
              onValueChange = {
                rawTafText = it
                copiedFeedback = false
              },
              label = { Text("Введите или вставьте код TAF для расшифровки", fontSize = 11.sp) },
              minLines = 2,
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("decoder_taf_input"),
            )
          }
        }

        if (activeTab == DecoderViewTab.ALL || activeTab == DecoderViewTab.METAR) {
          DecodedSectionBlock(
            title = "ОТКРЫТАЯ РАСШИФРОВКА METAR",
            result = decodedMetar,
            summaryTestTag = "decoder_metar_plain_summary",
            tokenTagPrefix = "decoder_metar_token",
          )
        }

        if (activeTab == DecoderViewTab.ALL || activeTab == DecoderViewTab.TAF) {
          DecodedSectionBlock(
            title = "ОТКРЫТАЯ РАСШИФРОВКА TAF",
            result = decodedTaf,
            summaryTestTag = "decoder_taf_plain_summary",
            tokenTagPrefix = "decoder_taf_token",
          )
        }
      }
    }
  }
}

@Composable
private fun DecodedSectionBlock(
  title: String,
  result: DecodedMessageResult,
  summaryTestTag: String,
  tokenTagPrefix: String,
) {
  val palette = LocalMeteoPalette.current

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .background(palette.panelSurface, RoundedCornerShape(4.dp))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(4.dp))
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
          imageVector = Icons.Filled.Description,
          contentDescription = null,
          tint = palette.metarAmber,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = title,
          color = palette.textPrimary,
          fontSize = 12.5.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 0.4.sp,
        )
      }
      Text(
        text = "${result.items.size} групп",
        color = palette.textMuted,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
      )
    }

    // Plain-Language Open Summary Box
    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier
        .fillMaxWidth()
        .semantics(mergeDescendants = true) {}
        .testTag(summaryTestTag)
        .background(palette.fieldBackground, RoundedCornerShape(4.dp))
        .border(1.2.dp, palette.metarAmber.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
        .padding(10.dp),
    ) {
      Text(
        text = "ТЕКСТОВАЯ РАСШИФРОВКА (ОТКРЫТЫЙ ВИД):",
        color = palette.metarAmber,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
      )
      Text(
        text = result.plainSummaryParagraph,
        color = palette.textPrimary,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
      )
    }

    if (result.items.isNotEmpty()) {
      HorizontalDivider(thickness = 1.dp, color = palette.border)

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        result.items.forEachIndexed { index, item ->
          DecodedCodeRow(
            item = item,
            testTag = "${tokenTagPrefix}_$index",
          )
        }
      }
    }
  }
}

@Composable
private fun DecodedCodeRow(
  item: DecodedCodeItem,
  testTag: String,
) {
  val palette = LocalMeteoPalette.current
  val accentColor = when (item.severity) {
    DecodedTokenSeverity.HAZARD -> palette.tailwindNegative
    DecodedTokenSeverity.CAUTION -> palette.metarAmber
    DecodedTokenSeverity.NORMAL -> palette.headwindPositive
  }
  val bg = when (item.severity) {
    DecodedTokenSeverity.HAZARD -> palette.tailwindNegative.copy(alpha = 0.08f)
    DecodedTokenSeverity.CAUTION -> palette.metarAmber.copy(alpha = 0.06f)
    DecodedTokenSeverity.NORMAL -> palette.secondaryFieldBackground.copy(alpha = 0.65f)
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(IntrinsicSize.Min)
      .semantics(mergeDescendants = true) {}
      .testTag(testTag)
      .clip(RoundedCornerShape(4.dp))
      .background(bg)
      .border(0.9.dp, if (item.severity == DecodedTokenSeverity.NORMAL) palette.border else accentColor, RoundedCornerShape(4.dp)),
  ) {
    Box(
      modifier = Modifier
        .width(3.5.dp)
        .fillMaxHeight()
        .background(accentColor),
    )
    Column(
      verticalArrangement = Arrangement.spacedBy(3.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier = Modifier
            .background(palette.fieldBackground, RoundedCornerShape(3.dp))
            .border(1.dp, palette.borderStrong, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
          Text(
            text = item.rawCode,
            color = palette.metarAmber,
            fontSize = 12.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold,
          )
        }

        Text(
          text = item.category.uppercase(),
          color = if (item.severity == DecodedTokenSeverity.NORMAL) palette.textSecondary else accentColor,
          fontSize = 10.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 0.4.sp,
          modifier = Modifier.weight(1f),
        )
      }

      Text(
        text = item.shortValue,
        color = palette.textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
      )

      Text(
        text = item.fullExplanation,
        color = palette.textSecondary,
        fontSize = 12.sp,
        lineHeight = 16.5.sp,
      )
    }
  }
}
