package com.example.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RadarSourceMode
import com.example.data.RadarTimelineSnapshot
import com.example.data.WeatherRadarRepository
import com.example.domain.AirportMapProjection
import com.example.domain.MapLayerStyle
import com.example.domain.MapRegionPreset
import com.example.model.AirportStation
import com.example.ui.components.AndroidMapTileLoader
import com.example.ui.components.rememberMapTileSnapshot
import com.example.ui.components.rememberRadarTileSnapshot
import com.example.ui.theme.LocalMeteoPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun WeatherRadarScreen(
  stations: List<AirportStation>,
  activeStation: AirportStation,
  isNightMode: Boolean,
  isFullscreen: Boolean,
  onSelectStationOnBoard: (String) -> Unit,
  onBack: () -> Unit,
  initialTimeline: RadarTimelineSnapshot? = null,
  autoLoadTimeline: Boolean = initialTimeline == null,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val radarRepo = remember { WeatherRadarRepository() }
  val prefs = remember(context) {
    context.getSharedPreferences("meteotablo_radar", Context.MODE_PRIVATE)
  }

  var sourceMode by remember {
    mutableStateOf(RadarSourceMode.fromId(prefs.getString("radar_source", RadarSourceMode.AUTO.id)))
  }
  var owmApiKey by remember {
    mutableStateOf(prefs.getString("owm_api_key", "").orEmpty())
  }
  var showApiSettingsDialog by remember { mutableStateOf(false) }

  var timeline by remember(initialTimeline) { mutableStateOf(initialTimeline) }
  var isLoadingRadar by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }
  var frameIndex by remember(initialTimeline) {
    mutableIntStateOf((initialTimeline?.frames?.lastIndex ?: 0).coerceAtLeast(0))
  }
  var isPlaying by remember { mutableStateOf(false) }
  var layerStyle by remember(isNightMode) {
    mutableStateOf(if (isNightMode) MapLayerStyle.CARTO_DARK else MapLayerStyle.CARTO_VOYAGER)
  }
  var viewportPx by remember { mutableStateOf(IntSize.Zero) }
  var camera by remember(activeStation.icao) {
    mutableStateOf(
      AirportMapProjection.cameraForRegion(
        preset = if (activeStation.icao.startsWith("UT")) MapRegionPreset.TURKMENISTAN else MapRegionPreset.REGIONAL,
        stations = listOf(activeStation) + stations,
        widthPx = 900f,
        heightPx = 560f,
      ).copy(centerLat = activeStation.latitude, centerLon = activeStation.longitude, zoom = 5.5),
    )
  }

  fun loadTimeline(targetSource: RadarSourceMode = sourceMode, targetKey: String = owmApiKey) {
    scope.launch {
      isLoadingRadar = true
      errorMsg = null
      radarRepo.fetchLatestTimeline(
        preferredSource = targetSource,
        owmApiKey = targetKey,
      ).onSuccess { snap ->
        timeline = snap
        frameIndex = (snap.frames.lastIndex).coerceAtLeast(0)
        isLoadingRadar = false
      }.onFailure { err ->
        isLoadingRadar = false
        errorMsg = err.message ?: "Не удалось загрузить кадры радара"
      }
    }
  }

  LaunchedEffect(autoLoadTimeline) {
    if (autoLoadTimeline && timeline == null) {
      loadTimeline()
    }
  }

  val frames = timeline?.frames.orEmpty()
  val safeIdx = if (frames.isEmpty()) 0 else frameIndex.coerceIn(0, frames.lastIndex)
  val activeFrame = frames.getOrNull(safeIdx)

  LaunchedEffect(isPlaying, frames.size) {
    if (!isPlaying || frames.size <= 1) return@LaunchedEffect
    while (isPlaying) {
      delay(650L)
      frameIndex = (frameIndex + 1) % frames.size
    }
  }

  val safeModifier = if (isFullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxSize()
      .testTag("weather_radar_screen")
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  ) {
    // Top Header
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
            .testTag("radar_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад к табло",
            tint = palette.textPrimary,
          )
        }
        Icon(
          imageVector = Icons.Filled.CloudQueue,
          contentDescription = null,
          tint = palette.metarAmber,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
          Text(
            text = "Метеорадар · ${activeStation.icao} (${activeStation.city})",
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
          )
          val activeLabel = timeline?.sourceLabel ?: "Без ключа: RainViewer / LibreWXR / NASA GIBS"
          Text(
            text = activeFrame?.let {
              "$activeLabel · ${it.timeUtcLabel} (${safeIdx + 1}/${frames.size})"
            } ?: activeLabel,
            color = if (activeFrame?.isNowcast == true) palette.metarAmber else palette.textSecondary,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
          )
        }
      }

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Radar source / API button
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("radar_source_api_button")
            .clip(RoundedCornerShape(4.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable { showApiSettingsDialog = true }
            .padding(horizontal = 10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Key, contentDescription = "Источник радара и API", tint = palette.metarAmber, modifier = Modifier.size(15.dp))
            Text(sourceMode.shortLabel, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.buttonBackground)
            .border(1.dp, palette.border, RoundedCornerShape(4.dp))
            .clickable {
              val next = when (layerStyle) {
                MapLayerStyle.OSM -> MapLayerStyle.CARTO_DARK
                MapLayerStyle.CARTO_DARK -> MapLayerStyle.CARTO_VOYAGER
                MapLayerStyle.CARTO_VOYAGER -> MapLayerStyle.ESRI_SATELLITE
                MapLayerStyle.ESRI_SATELLITE -> MapLayerStyle.OSM
              }
              layerStyle = next
            }
            .padding(horizontal = 10.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Layers, contentDescription = "Слой карты", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
            Text(layerStyle.label, color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        IconButton(
          onClick = { loadTimeline() },
          modifier = Modifier
            .size(48.dp)
            .testTag("radar_refresh_button"),
        ) {
          if (isLoadingRadar) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = "Обновить кадры метеорадара",
              tint = palette.metarAmber,
            )
          }
        }
      }
    }

    val notice = timeline?.fallbackNotice
    if (errorMsg != null) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.tailwindNegative)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Text(
          text = errorMsg.orEmpty(),
          color = palette.tailwindNegative,
          fontSize = 11.5.sp,
          modifier = Modifier.weight(1f),
        )
        Text(
          text = "Источник",
          color = palette.metarAmber,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clickable { showApiSettingsDialog = true }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
          text = "Повтор",
          color = palette.metarAmber,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          modifier = Modifier
            .clickable { loadTimeline() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        )
      }
    } else if (!notice.isNullOrBlank()) {
      Text(
        text = notice,
        color = palette.metarAmber,
        fontSize = 11.sp,
        modifier = Modifier
          .fillMaxWidth()
          .background(palette.panelSurface)
          .border(1.dp, palette.border)
          .padding(horizontal = 10.dp, vertical = 4.dp),
      )
    }

    // Interactive Map + Radar Overlay Canvas
    BoxWithConstraints(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .clip(RoundedCornerShape(4.dp))
        .clipToBounds()
        .background(palette.panelSurface)
        .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
    ) {
      val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
      val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
      viewportPx = IntSize(widthPx.roundToInt(), heightPx.roundToInt())

      val tiles = remember(camera, viewportPx) {
        AirportMapProjection.visibleTiles(
          camera = camera,
          widthPx = widthPx,
          heightPx = heightPx,
        )
      }
      val baseRevision = rememberMapTileSnapshot(context = context, tiles = tiles, style = layerStyle)
      val radarRevision = rememberRadarTileSnapshot(
        tiles = tiles,
        host = timeline?.host ?: "https://tilecache.rainviewer.com",
        frame = activeFrame,
        owmApiKey = owmApiKey,
      )

      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .pointerInput(widthPx, heightPx) {
            detectTransformGestures { _, pan, zoom, _ ->
              val nextZoom = (camera.zoom + kotlin.math.ln(zoom.toDouble().coerceAtLeast(0.1)) / kotlin.math.ln(2.0)).coerceIn(2.0, 10.0)
              camera = AirportMapProjection.panCamera(
                camera = camera.copy(zoom = nextZoom),
                dxPx = pan.x,
                dyPx = pan.y,
              )
            }
          },
      ) {
        val touchRev = baseRevision + radarRevision
        if (touchRev >= 0) {
          drawRect(color = Color(0xFF0E1726))
        }
        val activeHost = timeline?.host ?: "https://tilecache.rainviewer.com"
        tiles.forEach { tile ->
          val baseBmp = AndroidMapTileLoader.getCached(tile.cacheKey(layerStyle))
          if (baseBmp != null) {
            drawImage(
              image = baseBmp,
              dstOffset = IntOffset(tile.drawX.roundToInt(), tile.drawY.roundToInt()),
              dstSize = IntSize(tile.tileSizePx.roundToInt() + 1, tile.tileSizePx.roundToInt() + 1),
            )
          }
          if (activeFrame != null) {
            val req = activeFrame.resolveTileRequest(activeHost, tile.z, tile.x, tile.y, owmApiKey)
            val radarBmp = AndroidMapTileLoader.getCached(req.cacheKey)
            if (radarBmp != null) {
              val srcW = (radarBmp.width / req.subGridSize).coerceAtLeast(1)
              val srcH = (radarBmp.height / req.subGridSize).coerceAtLeast(1)
              val srcX = (req.subCol * srcW).coerceIn(0, (radarBmp.width - srcW).coerceAtLeast(0))
              val srcY = (req.subRow * srcH).coerceIn(0, (radarBmp.height - srcH).coerceAtLeast(0))
              drawImage(
                image = radarBmp,
                srcOffset = IntOffset(srcX, srcY),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(tile.drawX.roundToInt(), tile.drawY.roundToInt()),
                dstSize = IntSize(tile.tileSizePx.roundToInt() + 1, tile.tileSizePx.roundToInt() + 1),
                alpha = 0.72f,
              )
            }
          }
        }

        // Airport Pins & Range Rings around Active Station
        val centerPt = AirportMapProjection.projectMercator(
          lat = activeStation.latitude,
          lon = activeStation.longitude,
          camera = camera,
          widthPx = size.width,
          heightPx = size.height,
        )
        drawCircle(
          color = Color(0xFFFF981A).copy(alpha = 0.35f),
          radius = 60f,
          center = Offset(centerPt.x, centerPt.y),
          style = Stroke(width = 1.5f),
        )
        stations.forEach { st ->
          val pt = AirportMapProjection.projectMercator(
            lat = st.latitude,
            lon = st.longitude,
            camera = camera,
            widthPx = size.width,
            heightPx = size.height,
          )
          val isCur = st.icao == activeStation.icao
          drawCircle(
            color = if (isCur) Color(0xFFFF981A) else Color(0xFF38BDF8),
            radius = if (isCur) 8f else 5.5f,
            center = Offset(pt.x, pt.y),
          )
          drawCircle(
            color = Color.Black,
            radius = if (isCur) 9f else 6.5f,
            center = Offset(pt.x, pt.y),
            style = Stroke(width = 1.5f),
          )
        }
      }

      // Quick station chips overlay + zoom controls
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(8.dp)
          .horizontalScroll(rememberScrollState()),
      ) {
        stations.take(8).forEach { st ->
          val isCur = st.icao == activeStation.icao
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(if (isCur) palette.buttonActiveBackground else palette.panelSurface.copy(alpha = 0.9f))
              .border(1.dp, if (isCur) palette.metarAmber else palette.border, RoundedCornerShape(4.dp))
              .clickable {
                onSelectStationOnBoard(st.icao)
                camera = camera.copy(centerLat = st.latitude, centerLon = st.longitude)
              }
              .padding(horizontal = 8.dp, vertical = 5.dp),
          ) {
            Text(
              text = st.icao,
              color = if (isCur) palette.metarAmber else palette.textPrimary,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(8.dp),
      ) {
        IconButton(
          onClick = { camera = camera.copy(zoom = (camera.zoom + 0.8).coerceAtMost(10.0)) },
          modifier = Modifier
            .size(48.dp)
            .background(palette.panelSurface.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "Приблизить", tint = palette.textPrimary)
        }
        IconButton(
          onClick = { camera = camera.copy(zoom = (camera.zoom - 0.8).coerceAtLeast(2.0)) },
          modifier = Modifier
            .size(48.dp)
            .background(palette.panelSurface.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
        ) {
          Icon(Icons.Filled.Remove, contentDescription = "Отдалить", tint = palette.textPrimary)
        }
        IconButton(
          onClick = {
            camera = camera.copy(centerLat = activeStation.latitude, centerLon = activeStation.longitude, zoom = 5.5)
          },
          modifier = Modifier
            .size(48.dp)
            .background(palette.panelSurface.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .border(1.dp, palette.border, RoundedCornerShape(4.dp)),
        ) {
          Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Центр на аэродром", tint = palette.metarAmber)
        }
      }
    }

    // Bottom Playback & UTC Frame Timeline Bar
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.panelSurface)
        .border(1.dp, palette.border)
        .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
      IconButton(
        onClick = { if (frames.size > 1) isPlaying = !isPlaying },
        modifier = Modifier
          .size(48.dp)
          .testTag("radar_play_pause_button"),
      ) {
        Icon(
          imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
          contentDescription = if (isPlaying) "Пауза анимации радара" else "Запустить анимацию радара",
          tint = palette.metarAmber,
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Slider(
          value = safeIdx.toFloat(),
          onValueChange = { v ->
            isPlaying = false
            frameIndex = v.roundToInt().coerceIn(0, (frames.size - 1).coerceAtLeast(0))
          },
          valueRange = 0f..(frames.size - 1).coerceAtLeast(1).toFloat(),
          enabled = frames.size > 1,
          modifier = Modifier.testTag("radar_timeline_slider"),
        )
        Row(
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = frames.firstOrNull()?.timeUtcLabel ?: "--:--Z",
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
          )
          Text(
            text = activeFrame?.timeUtcLabel ?: "Ожидание кадров…",
            color = palette.metarAmber,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = frames.lastOrNull()?.timeUtcLabel ?: "--:--Z",
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
          )
        }
      }
    }
  }

  if (showApiSettingsDialog) {
    var draftMode by remember(showApiSettingsDialog) { mutableStateOf(sourceMode) }
    var draftKey by remember(showApiSettingsDialog) { mutableStateOf(owmApiKey) }

    AlertDialog(
      onDismissRequest = { showApiSettingsDialog = false },
      containerColor = palette.panelSurface,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textPrimary,
      title = {
        Text(
          text = "Источник метеорадара и API",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        ) {
          Text(
            text = "Режимы «Авто», «RainViewer (до Z7)», «LibreWXR» и «NASA GIBS» работают бесплатно БЕЗ API-ключа. Если RainViewer сообщает об API/лимите, выберите «Авто» или «LibreWXR / NASA GIBS».",
            color = palette.textSecondary,
            fontSize = 11.5.sp,
          )

          RadarSourceMode.entries.forEach { mode ->
            val selected = draftMode == mode
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.buttonActiveBackground else palette.secondaryFieldBackground)
                .border(1.dp, if (selected) palette.metarAmber else palette.border, RoundedCornerShape(4.dp))
                .clickable { draftMode = mode }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Text(
                text = mode.fullLabel,
                color = if (selected) palette.metarAmber else palette.textPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
              )
            }
          }

          if (draftMode == RadarSourceMode.OPENWEATHER) {
            Text(
              text = "Бесплатный ключ OpenWeatherMap можно получить после регистрации на openweathermap.org/api (раздел «My API keys»).",
              color = palette.textSecondary,
              fontSize = 11.sp,
            )
            OutlinedTextField(
              value = draftKey,
              onValueChange = { draftKey = it.trim() },
              label = { Text("OpenWeatherMap API Key (необязательно)", fontSize = 11.sp) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            sourceMode = draftMode
            owmApiKey = draftKey.trim()
            prefs.edit()
              .putString("radar_source", sourceMode.id)
              .putString("owm_api_key", owmApiKey)
              .apply()
            showApiSettingsDialog = false
            loadTimeline(sourceMode, owmApiKey)
          },
        ) {
          Text("Применить")
        }
      },
      dismissButton = {
        TextButton(onClick = { showApiSettingsDialog = false }) {
          Text("Закрыть")
        }
      },
    )
  }
}
