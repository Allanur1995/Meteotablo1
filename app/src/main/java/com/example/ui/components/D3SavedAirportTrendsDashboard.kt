package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.AirportCatalog
import com.example.domain.D3TrendsChartDataBuilder
import com.example.domain.D3TrendsDashboardModel
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WeatherHistoryEntry
import com.example.model.WindSpeedUnit
import com.example.ui.theme.LocalMeteoPalette
import org.json.JSONObject
import kotlin.math.roundToInt

enum class D3TrendChartMode(
  val id: String,
  val label: String,
) {
  WIND_VIS("WIND_VIS", "Ветер + Видимость"),
  WIND_QNH("WIND_QNH", "Ветер + QNH"),
}

@Composable
fun D3SavedAirportTrendsDashboard(
  currentStation: AirportStation,
  currentWeather: DecodedWeather,
  favoriteIcaos: List<String>,
  selectedD3Icao: String,
  weatherHistoryByIcao: Map<String, List<WeatherHistoryEntry>>,
  mapWeatherByIcao: Map<String, DecodedWeather>,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isNightMode: Boolean,
  loadingHistoryIcao: String?,
  onSelectD3Airport: (String) -> Unit,
  onRefreshD3AirportHistory: (String) -> Unit,
  compactMode: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  var isExpanded by remember { mutableStateOf(true) }
  var chartMode by remember { mutableStateOf(D3TrendChartMode.WIND_VIS) }

  val airportTabs = remember(currentStation.icao, favoriteIcaos) {
    (favoriteIcaos + currentStation.icao)
      .map { it.trim().uppercase() }
      .filter { it.length == 4 }
      .distinct()
  }

  val activeIcao = remember(selectedD3Icao, airportTabs) {
    val clean = selectedD3Icao.trim().uppercase()
    if (clean in airportTabs) clean else airportTabs.firstOrNull() ?: currentStation.icao
  }

  val targetStation = remember(activeIcao, currentStation) {
    if (activeIcao.equals(currentStation.icao, ignoreCase = true)) {
      currentStation
    } else {
      AirportCatalog.findOrDefault(activeIcao)
    }
  }

  val liveFallback = remember(activeIcao, currentStation, currentWeather, mapWeatherByIcao) {
    when {
      activeIcao.equals(currentStation.icao, ignoreCase = true) -> currentWeather
      else -> mapWeatherByIcao[activeIcao]
    }
  }

  val historyEntries = weatherHistoryByIcao[activeIcao].orEmpty()
  val model = remember(targetStation, historyEntries, liveFallback, windUnit, pressureUnit) {
    D3TrendsChartDataBuilder.build(
      station = targetStation,
      historyEntries = historyEntries,
      liveFallbackWeather = liveFallback,
      windUnit = windUnit,
      pressureUnit = pressureUnit,
    )
  }
  val d3JsonPayload = remember(model, isNightMode, chartMode) {
    model.toD3JsonPayload(darkTheme = isNightMode, mode = chartMode.id)
  }
  val isLoadingTarget = loadingHistoryIcao?.equals(activeIcao, ignoreCase = true) == true

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxWidth()
      .background(palette.panelSurface)
      .border(1.dp, palette.border)
      .padding(if (compactMode) 6.dp else 10.dp)
      .testTag("d3_trends_dashboard"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 48.dp)
          .clickable { isExpanded = !isExpanded }
          .padding(horizontal = 4.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ShowChart,
          contentDescription = "D3.js график ветра и давления за 24 часа",
          tint = palette.metarAmber,
          modifier = Modifier.size(18.dp),
        )
        Column {
          Text(
            text = if (chartMode == D3TrendChartMode.WIND_VIS) {
              "D3.js · Ветер и видимость (24ч)"
            } else {
              "D3.js · Ветер и давление QNH (24ч)"
            },
            color = palette.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "${model.icao} (${model.city}) · ${model.points.size} точек",
            color = palette.textSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        IconButton(
          onClick = { onRefreshD3AirportHistory(activeIcao) },
          enabled = !isLoadingTarget,
          modifier = Modifier
            .size(48.dp)
            .testTag("d3_refresh_history_button"),
        ) {
          if (isLoadingTarget) {
            CircularProgressIndicator(
              strokeWidth = 2.dp,
              color = palette.metarAmber,
              modifier = Modifier.size(16.dp),
            )
          } else {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Обновить 24ч тренды для $activeIcao",
              tint = palette.metarAmber,
              modifier = Modifier.size(18.dp),
            )
          }
        }
        IconButton(
          onClick = { isExpanded = !isExpanded },
          modifier = Modifier
            .size(48.dp)
            .testTag("d3_expand_toggle_button"),
        ) {
          Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isExpanded) "Свернуть D3 панель" else "Развернуть D3 панель",
            tint = palette.textSecondary,
          )
        }
      }
    }

    AnimatedVisibility(visible = isExpanded) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Saved Airport Chips
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        ) {
          airportTabs.forEach { icao ->
            val selected = icao == activeIcao
            val isFav = icao in favoriteIcaos
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("d3_airport_chip_$icao")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) palette.metarAmber else palette.border,
                  shape = RoundedCornerShape(4.dp),
                )
                .clickable { onSelectD3Airport(icao) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
              Text(
                text = if (isFav) "★ $icao" else icao,
                color = if (selected) palette.metarAmber else palette.textPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              )
            }
          }
        }

        // Chart Mode Selector (Wind+Visibility / Wind+QNH)
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          D3TrendChartMode.entries.forEach { m ->
            val selected = m == chartMode
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("d3_mode_${m.id}")
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) palette.metarAmber else palette.border,
                  shape = RoundedCornerShape(4.dp),
                )
                .clickable { chartMode = m }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Text(
                text = m.label,
                color = if (selected) palette.metarAmber else palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              )
            }
          }
        }

        // KPI Summary Cards
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .weight(1f)
              .semantics(mergeDescendants = true) {}
              .testTag("d3_kpi_wind")
              .background(palette.fieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
              .padding(8.dp),
          ) {
            Text(
              text = "Ветер (${windUnit.label})",
              color = palette.textSecondary,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = model.formattedWindRange(),
              color = palette.metarAmber,
              fontSize = 12.5.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .semantics(mergeDescendants = true) {}
              .testTag("d3_kpi_visibility")
              .background(palette.fieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
              .padding(8.dp),
          ) {
            Text(
              text = "Видимость",
              color = palette.textSecondary,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = model.formattedVisibilityRange(),
              color = Color(0xFF10B981),
              fontSize = 12.5.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .semantics(mergeDescendants = true) {}
              .testTag("d3_kpi_pressure")
              .background(palette.fieldBackground, RoundedCornerShape(4.dp))
              .border(1.dp, palette.border, RoundedCornerShape(4.dp))
              .padding(8.dp),
          ) {
            Text(
              text = "QNH (${pressureUnit.label})",
              color = palette.textSecondary,
              fontSize = 10.5.sp,
              fontWeight = FontWeight.SemiBold,
            )
            Text(
              text = "${model.formattedPressureRange()} · Δ ${model.formattedPressureDelta()}",
              color = Color(0xFF38BDF8),
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        // D3.js Chart Container with WebView + Safe Compose Canvas Fallback
        D3ChartCanvasHost(
          model = model,
          chartMode = chartMode,
          d3JsonPayload = d3JsonPayload,
          compactMode = compactMode,
        )
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun D3ChartCanvasHost(
  model: D3TrendsDashboardModel,
  chartMode: D3TrendChartMode,
  d3JsonPayload: String,
  compactMode: Boolean,
) {
  val palette = LocalMeteoPalette.current
  var pageLoaded by remember { mutableStateOf(false) }
  var webViewInitFailed by remember { mutableStateOf(false) }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  val chartHeight = if (compactMode) 170.dp else 210.dp

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(chartHeight)
      .clip(RoundedCornerShape(4.dp))
      .background(palette.fieldBackground)
      .border(1.dp, palette.border, RoundedCornerShape(4.dp))
      .testTag("d3_webview_container"),
  ) {
    if (!webViewInitFailed) {
      AndroidView(
        factory = { ctx ->
          runCatching {
            WebView(ctx).apply {
              settings.javaScriptEnabled = true
              settings.allowFileAccess = true
              settings.domStorageEnabled = true
              setBackgroundColor(android.graphics.Color.TRANSPARENT)
              webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                  pageLoaded = true
                  val quoted = JSONObject.quote(d3JsonPayload)
                  view?.evaluateJavascript("window.renderTrendsDashboard($quoted)", null)
                }
              }
              loadUrl("file:///android_asset/d3_wind_pressure_dashboard.html")
              webViewRef = this
            }
          }.getOrElse {
            webViewInitFailed = true
            android.view.View(ctx)
          }
        },
        update = { view ->
          if (view is WebView && pageLoaded) {
            val quoted = JSONObject.quote(d3JsonPayload)
            runCatching {
              view.evaluateJavascript("window.renderTrendsDashboard($quoted)", null)
            }
          }
        },
        modifier = Modifier.fillMaxSize(),
      )
    }

    // Native Compose fallback shown if WebView isn't ready (e.g. headless Robolectric)
    if (webViewInitFailed || !pageLoaded) {
      NativeWindPressureFallbackChart(
        model = model,
        chartMode = chartMode,
        modifier = Modifier
          .fillMaxSize()
          .padding(10.dp)
          .testTag("d3_canvas_fallback"),
      )
    }
  }
}

@Composable
private fun NativeWindPressureFallbackChart(
  model: D3TrendsDashboardModel,
  chartMode: D3TrendChartMode,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val windColor = palette.metarAmber
  val qnhColor = Color(0xFF0284C7)
  val visColor = Color(0xFF10B981)

  if (model.points.isEmpty()) {
    Box(
      contentAlignment = Alignment.Center,
      modifier = modifier,
    ) {
      Text(
        text = "Нажмите обновить для загрузки 24ч трендов ${model.icao}",
        color = palette.textMuted,
        fontSize = 12.sp,
      )
    }
    return
  }

  Column(modifier = modifier) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "Ветер: ${model.formattedWindRange()}",
        color = windColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
      )
      if (chartMode == D3TrendChartMode.WIND_VIS) {
        Text(
          text = "Видимость: ${model.formattedVisibilityRange()}",
          color = visColor,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
        )
      } else {
        Text(
          text = "QNH: ${model.formattedPressureRange()}",
          color = qnhColor,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
        )
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      val pts = model.points
      val w = size.width
      val h = size.height
      if (w <= 4f || h <= 4f || pts.isEmpty()) return@Canvas

      val gridColor = palette.border.copy(alpha = 0.45f)
      for (step in 1..3) {
        val y = h * step / 4f
        drawLine(
          color = gridColor,
          start = Offset(0f, y),
          end = Offset(w, y),
          strokeWidth = 1f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
      }

      val maxWind = (pts.maxOf { maxOf(it.windDisplay, it.gustDisplay ?: it.windDisplay) } * 1.2)
        .coerceAtLeast(10.0)
      val qnhValues = pts.mapNotNull { it.pressureDisplay }
      val qMin = qnhValues.minOrNull()?.let { it - 2.0 } ?: 1000.0
      val qMax = qnhValues.maxOrNull()?.let { it + 2.0 } ?: 1025.0
      val qSpan = (qMax - qMin).coerceAtLeast(1.0)

      fun xAt(idx: Int): Float {
        return if (pts.size == 1) w / 2f else w * idx.toFloat() / (pts.size - 1).toFloat()
      }

      val windPath = Path()
      pts.forEachIndexed { idx, pt ->
        val x = xAt(idx)
        val y = (h * (1.0 - (pt.windDisplay / maxWind).coerceIn(0.0, 1.0))).toFloat()
        if (idx == 0) windPath.moveTo(x, y) else windPath.lineTo(x, y)
      }
      if (pts.size >= 2) {
        drawPath(
          path = windPath,
          color = windColor,
          style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
      }
      pts.forEachIndexed { idx, pt ->
        val x = xAt(idx)
        val y = (h * (1.0 - (pt.windDisplay / maxWind).coerceIn(0.0, 1.0))).toFloat()
        drawCircle(color = windColor, radius = 5f, center = Offset(x, y))
      }

      if (chartMode == D3TrendChartMode.WIND_VIS) {
        val visPath = Path()
        var startedVis = false
        var visCount = 0
        pts.forEachIndexed { idx, pt ->
          val visKm = pt.visibilityKm ?: return@forEachIndexed
          visCount++
          val x = xAt(idx)
          val y = (h * (1.0 - (visKm / 10.0).coerceIn(0.0, 1.0))).toFloat()
          if (!startedVis) {
            visPath.moveTo(x, y)
            startedVis = true
          } else {
            visPath.lineTo(x, y)
          }
          drawCircle(color = visColor, radius = 4.5f, center = Offset(x, y))
        }
        if (visCount >= 2) {
          drawPath(
            path = visPath,
            color = visColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }
      } else {
        val qnhPath = Path()
        var startedQnh = false
        pts.forEachIndexed { idx, pt ->
          val q = pt.pressureDisplay ?: return@forEachIndexed
          val x = xAt(idx)
          val y = (h * (1.0 - ((q - qMin) / qSpan).coerceIn(0.0, 1.0))).toFloat()
          if (!startedQnh) {
            qnhPath.moveTo(x, y)
            startedQnh = true
          } else {
            qnhPath.lineTo(x, y)
          }
          drawCircle(color = qnhColor, radius = 4.5f, center = Offset(x, y))
        }
        if (qnhValues.size >= 2) {
          drawPath(
            path = qnhPath,
            color = qnhColor,
            style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
          )
        }
      }
    }
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = model.points.firstOrNull()?.observationTimeUtc.orEmpty(),
        color = palette.textMuted,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
      )
      Text(
        text = model.points.lastOrNull()?.let {
          val extra = if (chartMode == D3TrendChartMode.WIND_VIS && it.visibilityMeters != null) {
            " · ${D3TrendsDashboardModel.formatVisibility(it.visibilityMeters)}"
          } else {
            ""
          }
          "Последний: ${it.windDisplay.roundToInt()} ${model.windUnit.label}$extra"
        }.orEmpty(),
        color = palette.textSecondary,
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
      )
    }
  }
}
