package com.example.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.WeatherExportPayload
import com.example.domain.WeatherReportFormatter
import com.example.export.AndroidWeatherReportExporter
import com.example.ui.theme.LocalMeteoPalette

@Composable
fun ExportWeatherDialog(
  payload: WeatherExportPayload,
  onExportLogged: (String) -> Unit = {},
  onDismiss: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  val summaryText = remember(payload) {
    WeatherReportFormatter.buildTextSummary(payload)
  }
  val baseFileName = remember(payload) {
    WeatherReportFormatter.suggestedFileBaseName(payload.station, payload.weather)
  }

  var statusMessage by remember { mutableStateOf<String?>(null) }
  var isErrorStatus by remember { mutableStateOf(false) }

  val pdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf"),
  ) { uri ->
    if (uri != null) {
      val result = AndroidWeatherReportExporter.exportPdfToUri(context, uri, payload)
      result.onSuccess {
        val msg = "PDF-сводка ${payload.station.icao} сохранена"
        statusMessage = msg
        isErrorStatus = false
        onExportLogged(msg)
      }.onFailure { err ->
        val msg = "Ошибка сохранения PDF: ${err.message ?: "неизвестная ошибка"}"
        statusMessage = msg
        isErrorStatus = true
        onExportLogged(msg)
      }
    }
  }

  val txtLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("text/plain"),
  ) { uri ->
    if (uri != null) {
      val result = AndroidWeatherReportExporter.exportTextToUri(context, uri, payload)
      result.onSuccess {
        val msg = "Текстовая сводка ${payload.station.icao} сохранена"
        statusMessage = msg
        isErrorStatus = false
        onExportLogged(msg)
      }.onFailure { err ->
        val msg = "Ошибка сохранения TXT: ${err.message ?: "неизвестная ошибка"}"
        statusMessage = msg
        isErrorStatus = true
        onExportLogged(msg)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Column {
        Text(
          text = "Экспорт сводки · ${payload.station.icao}",
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
        )
        Text(
          text = "Текущая погода, ВПП и тренды 24ч в PDF или TXT",
          color = palette.textSecondary,
          fontSize = 12.sp,
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .testTag("export_report_dialog"),
      ) {
        if (statusMessage != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .semantics(mergeDescendants = true) {}
              .testTag("export_status_message")
              .background(
                if (isErrorStatus) palette.tailwindNegative.copy(alpha = 0.14f)
                else palette.headwindPositive.copy(alpha = 0.14f),
                RoundedCornerShape(4.dp),
              )
              .border(
                1.dp,
                if (isErrorStatus) palette.tailwindNegative else palette.headwindPositive,
                RoundedCornerShape(4.dp),
              )
              .padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Text(
              text = statusMessage.orEmpty(),
              color = if (isErrorStatus) palette.tailwindNegative else palette.headwindPositive,
              fontSize = 11.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        // Primary Save Buttons (PDF & TXT)
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            onClick = { pdfLauncher.launch("$baseFileName.pdf") },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("export_save_pdf_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.PictureAsPdf,
              contentDescription = "Сохранить в PDF",
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("PDF", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
          }

          Button(
            onClick = { txtLauncher.launch("$baseFileName.txt") },
            colors = ButtonDefaults.buttonColors(
              containerColor = palette.buttonActiveBackground,
              contentColor = palette.textPrimary,
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("export_save_txt_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Description,
              contentDescription = "Сохранить в TXT",
              tint = palette.metarAmber,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("TXT", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
          }
        }

        // Quick Copy & Share Actions
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          OutlinedButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(summaryText))
              statusMessage = "Текстовая сводка ${payload.station.icao} скопирована в буфер"
              isErrorStatus = false
            },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("export_copy_summary_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.ContentCopy,
              contentDescription = "Копировать сводку",
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text("Копировать", fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = {
              runCatching {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "METEO ${payload.station.icao} (${payload.weather.observationTimeUtc})")
                  putExtra(Intent.EXTRA_TEXT, summaryText)
                }
                context.startActivity(
                  Intent.createChooser(shareIntent, "Поделиться сводкой ${payload.station.icao}"),
                )
              }.onFailure { err ->
                statusMessage = "Не удалось открыть меню отправки: ${err.message ?: ""}"
                isErrorStatus = true
              }
            },
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("export_share_summary_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Share,
              contentDescription = "Поделиться текстовой сводкой",
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text("Поделиться", fontSize = 12.sp)
          }
        }

        Text(
          text = "Предпросмотр отчёта:",
          color = palette.textSecondary,
          fontSize = 11.5.sp,
          fontWeight = FontWeight.SemiBold,
        )

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp, max = 140.dp)
            .background(palette.fieldBackground, RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        ) {
          Text(
            text = summaryText,
            color = palette.textPrimary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 15.sp,
            modifier = Modifier.testTag("export_summary_preview"),
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.testTag("export_close_button"),
      ) {
        Text("Закрыть")
      }
    },
  )
}
