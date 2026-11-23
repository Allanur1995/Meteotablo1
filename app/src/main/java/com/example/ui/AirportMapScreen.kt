package com.example.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.MapLayerStyle
import com.example.domain.MapRegionPreset
import com.example.domain.MapViewportCamera
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import com.example.ui.components.AirportMapSummaryCard
import com.example.ui.components.AndroidMapTileLoader
import com.example.ui.components.flightCategoryColor
import com.example.ui.components.rememberMapTileSnapshot
import com.example.ui.theme.LocalMeteoPalette
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AirportMapScreen(
  stations: List<AirportStation>,
  weatherByIcao: Map<String, DecodedWeather>,
  activeStationIcao: String,
  favoriteIcaos: List<String>,
  windUnit: WindSpeedUnit,
  pressureUnit: PressureUnit,
  isFullscreen: Boolean,
  isRefreshingMap: Boolean,
  loadingMapIcao: String?,
  isNightMode: Boolean,
  onBack: () -> Unit,
  onSelectStationOnBoard: (String) -> Unit,
  onRefreshAll: () -> Unit,
  onRefreshStation: (String) -> Unit,
  onToggleFavorite: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = onBack)

  val palette = LocalMeteoPalette.current
  var selectedIcao by remember(activeStationIcao) { mutableStateOf(activeStationIcao) }
  var regionPreset by remember {
    mutableStateOf(
      if (stations.any { it.icao == activeStationIcao && it.icao.startsWith("UT") }) {
        MapRegionPreset.TURKMENISTAN
      } else {
        MapRegionPreset.REGIONAL
      },
    )
  }
  var layerStyle by remember(isNightMode) {
    mutableStateOf(if (isNightMode) MapLayerStyle.CARTO_DARK else MapLayerStyle.OSM)
  }
  var camera by remember {
    mutableStateOf(
      AirportMapProjection.cameraForRegion(regionPreset, stations, 900f, 520f),
    )
  }
  var viewportPx by remember { mutableStateOf(IntSize.Zero) }

  val selectedStation = remember(stations, selectedIcao) {
    stations.firstOrNull { it.icao == selectedIcao } ?: stations.first()
  }
  val selectedWeather = weatherByIcao[selectedStation.icao]

  val safeModifier = if (isFullscreen) {
    Modifier
  } else {
    Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
  }

  fun focusRegion(preset: MapRegionPreset) {
    regionPreset = preset
    val w = if (viewportPx.width > 0) viewportPx.width.toFloat() else 900f
    val h = if (viewportPx.height > 0) viewportPx.height.toFloat() else 520f
    camera = AirportMapProjection.cameraForRegion(preset, stations, w, h)
  }

  fun selectPin(icao: String, centerIfNeeded: Boolean = false) {
    selectedIcao = icao
    val target = stations.firstOrNull { it.icao == icao }
    if (centerIfNeeded && target != null) {
      val pt = AirportMapProjection.projectMercator(
        target.latitude,
        target.longitude,
        camera,
        viewportPx.width.toFloat(),
        viewportPx.height.toFloat(),
      )
      val outOfView = viewportPx.width > 0 && (
        pt.x < 24f || pt.x > viewportPx.width - 24f ||
          pt.y < 24f || pt.y > viewportPx.height - 24f
        )
      if (outOfView) {
        camera = camera.copy(
          centerLat = target.latitude,
          centerLon = target.longitude,
          zoom = camera.zoom.coerceAtLeast(5.2),
        )
      }
    }
    if (weatherByIcao[icao] == null) {
      onRefreshStation(icao)
    }
  }

  Column(
    verticalArrangement = Arrangement.spacedBy(6.dp),
    modifier = modifier
      .fillMaxSize()
      .background(palette.bezelSurface)
      .then(safeModifier)
      .padding(horizontal = 8.dp, vertical = 6.dp)
      .testTag("airport_map_screen"),
  ) {
    // Top Header Bar (pinned)
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .background(palette.panelSurface)
        .border(1.dp, palette.border)
        .padding(horizontal = 6.dp, vertical = 4.dp)
        .testTag("map_top_bar"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f),
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier
            .size(48.dp)
            .testTag("map_back_button"),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад к табло",
            tint = palette.textPrimary,
          )
        }
        Icon(
          imageVector = Icons.Filled.Map,
          contentDescription = null,
          tint = palette.metarAmber,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
          Text(
            text = "Карта аэродромов",
            color = palette.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
          )
          val loadedCount = stations.count { weatherByIcao[it.icao] != null }
          Text(
            text = "${stations.size} аэродр. · погода: $loadedCount/${stations.size} · ${layerStyle.label}",
            color = palette.textSecondary,
            fontSize = 11.sp,
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("map_refresh_button")
          .clip(RoundedCornerShape(4.dp))
          .background(palette.buttonBackground)
          .border(1.dp, palette.border, RoundedCornerShape(4.dp))
          .clickable(enabled = !isRefreshingMap, onClick = onRefreshAll)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        if (isRefreshingMap) {
          CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = palette.metarAmber,
            modifier = Modifier.size(15.dp),
          )
        } else {
          Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Обновить погоду всех станций на карте",
            tint = palette.textPrimary,
            modifier = Modifier.size(16.dp),
          )
        }
        Text(
          text = "Обновить",
          color = palette.textPrimary,
          fontSize = 12.sp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }

    // Layer styles, region presets, and station quick-jump strip
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    ) {
      MapLayerStyle.entries.forEach { style ->
        val active = style == layerStyle
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("map_layer_${style.name}")
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) palette.buttonActiveBackground else palette.panelSurface)
            .border(
              1.dp,
              if (active) palette.metarAmber else palette.border,
              RoundedCornerShape(4.dp),
            )
            .clickable { layerStyle = style }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Icon(
            imageVector = Icons.Filled.Layers,
            contentDescription = style.label,
            tint = if (active) palette.metarAmber else palette.textSecondary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = style.label,
            color = if (active) palette.metarAmber else palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
          )
        }
      }

      MapRegionPreset.entries.forEach { preset ->
        val active = preset == regionPreset
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("map_region_${preset.name}")
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) palette.buttonActiveBackground else palette.panelSurface)
            .border(
              width = 1.dp,
              color = if (active) palette.borderStrong else palette.border,
              shape = RoundedCornerShape(4.dp),
            )
            .clickable { focusRegion(preset) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = preset.label,
            color = if (active) palette.metarAmber else palette.textPrimary,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
          )
        }
      }

      stations.forEach { st ->
        val isSel = st.icao == selectedStation.icao
        val hasWeather = weatherByIcao[st.icao] != null
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("map_station_chip_${st.icao}")
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSel) palette.buttonActiveBackground else palette.secondaryFieldBackground)
            .border(
              1.dp,
              if (isSel) palette.metarAmber else palette.border,
              RoundedCornerShape(4.dp),
            )
            .clickable { selectPin(st.icao, centerIfNeeded = true) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Text(
            text = st.icao,
            color = if (isSel || hasWeather) palette.metarAmber else palette.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = st.city,
            color = palette.textSecondary,
            fontSize = 11.sp,
          )
        }
      }
    }

    // Adaptive Real-Tile Map + Selected Weather Summary layout
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    ) {
      val isWide = maxWidth >= 740.dp
      if (isWide) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          AviationTileMapViewport(
            stations = stations,
            weatherByIcao = weatherByIcao,
            selectedIcao = selectedStation.icao,
            favoriteIcaos = favoriteIcaos,
            camera = camera,
            layerStyle = layerStyle,
            windUnit = windUnit,
            isNightMode = isNightMode,
            onViewportMeasured = { size ->
              if (viewportPx == IntSize.Zero && size.width > 0 && size.height > 0) {
                camera = AirportMapProjection.cameraForRegion(
                  regionPreset,
                  stations,
                  size.width.toFloat(),
                  size.height.toFloat(),
                )
              }
              viewportPx = size
            },
            onTransform = { zoomDelta, panDelta ->
              val nextZoom = (camera.zoom + ln(zoomDelta.toDouble()) / ln(2.0)).coerceIn(2.0, 13.0)
              val panned = AirportMapProjection.panCamera(
                camera.copy(zoom = nextZoom),
                panDelta.x,
                panDelta.y,
              )
              camera = panned
            },
            onZoomIn = { camera = camera.copy(zoom = (camera.zoom + 0.8).coerceIn(2.0, 13.0)) },
            onZoomOut = { camera = camera.copy(zoom = (camera.zoom - 0.8).coerceIn(2.0, 13.0)) },
            onResetView = { focusRegion(regionPreset) },
            onSelectPin = { icao -> selectPin(icao) },
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight(),
          )
          Box(
            modifier = Modifier
              .width(360.dp)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
          ) {
            AirportMapSummaryCard(
              station = selectedStation,
              weather = selectedWeather,
              windUnit = windUnit,
              pressureUnit = pressureUnit,
              isFavorite = selectedStation.icao in favoriteIcaos,
              isActiveStation = selectedStation.icao == activeStationIcao,
              isLoadingPin = loadingMapIcao == selectedStation.icao,
              onToggleFavorite = { onToggleFavorite(selectedStation.icao) },
              onRefreshPin = { onRefreshStation(selectedStation.icao) },
              onOpenOnBoard = { onSelectStationOnBoard(selectedStation.icao) },
            )
          }
        }
      } else {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          AviationTileMapViewport(
            stations = stations,
            weatherByIcao = weatherByIcao,
            selectedIcao = selectedStation.icao,
            favoriteIcaos = favoriteIcaos,
            camera = camera,
            layerStyle = layerStyle,
            windUnit = windUnit,
            isNightMode = isNightMode,
            onViewportMeasured = { size ->
              if (viewportPx == IntSize.Zero && size.width > 0 && size.height > 0) {
                camera = AirportMapProjection.cameraForRegion(
                  regionPreset,
                  stations,
                  size.width.toFloat(),
                  size.height.toFloat(),
                )
              }
              viewportPx = size
            },
            onTransform = { zoomDelta, panDelta ->
              val nextZoom = (camera.zoom + ln(zoomDelta.toDouble()) / ln(2.0)).coerceIn(2.0, 13.0)
              camera = AirportMapProjection.panCamera(
                camera.copy(zoom = nextZoom),
                panDelta.x,
                panDelta.y,
              )
            },
            onZoomIn = { camera = camera.copy(zoom = (camera.zoom + 0.8).coerceIn(2.0, 13.0)) },
            onZoomOut = { camera = camera.copy(zoom = (camera.zoom - 0.8).coerceIn(2.0, 13.0)) },
            onResetView = { focusRegion(regionPreset) },
            onSelectPin = { icao -> selectPin(icao) },
            modifier = Modifier
              .fillMaxWidth()
              .weight(0.54f),
          )
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .weight(0.46f)
              .verticalScroll(rememberScrollState()),
          ) {
            AirportMapSummaryCard(
              station = selectedStation,
              weather = selectedWeather,
              windUnit = windUnit,
              pressureUnit = pressureUnit,
              isFavorite = selectedStation.icao in favoriteIcaos,
              isActiveStation = selectedStation.icao == activeStationIcao,
              isLoadingPin = loadingMapIcao == selectedStation.icao,
              onToggleFavorite = { onToggleFavorite(selectedStation.icao) },
              onRefreshPin = { onRefreshStation(selectedStation.icao) },
              onOpenOnBoard = { onSelectStationOnBoard(selectedStation.icao) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun AviationTileMapViewport(
  stations: List<AirportStation>,
  weatherByIcao: Map<String, DecodedWeather>,
  selectedIcao: String,
  favoriteIcaos: List<String>,
  camera: MapViewportCamera,
  layerStyle: MapLayerStyle,
  windUnit: WindSpeedUnit,
  isNightMode: Boolean,
  onViewportMeasured: (IntSize) -> Unit,
  onTransform: (zoomDelta: Float, panDelta: Offset) -> Unit,
  onZoomIn: () -> Unit,
  onZoomOut: () -> Unit,
  onResetView: () -> Unit,
  onSelectPin: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalMeteoPalette.current
  val context = LocalContext.current
  val density = LocalDensity.current
  val landStart = if (isNightMode) Color(0xFF0A1421) else Color(0xFFEAEFF4)
  val landEnd = if (isNightMode) Color(0xFF101F33) else Color(0xFFDCE4EE)
  val waterFill = if (isNightMode) Color(0xFF0E2A47) else Color(0xFFB5D4EC)
  val waterStroke = if (isNightMode) Color(0xFF2B5A87) else Color(0xFF6B9EC9)
  val gridColor = if (isNightMode) Color(0xFF1E324B) else Color(0xFFB7C4D4)
  val corridorColor = palette.metarAmber.copy(alpha = 0.35f)

  BoxWithConstraints(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(Brush.verticalGradient(listOf(landStart, landEnd)))
      .border(1.2.dp, palette.borderStrong, RoundedCornerShape(6.dp))
      .clipToBounds()
      .pointerInput(Unit) {
        detectTransformGestures { _, pan, gestureZoom, _ ->
          onTransform(gestureZoom, pan)
        }
      }
      .testTag("map_canvas_container"),
  ) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()

    LaunchedEffect(constraints.maxWidth, constraints.maxHeight) {
      if (constraints.maxWidth > 0 && constraints.maxHeight > 0) {
        onViewportMeasured(IntSize(constraints.maxWidth, constraints.maxHeight))
      }
    }

    val visibleTiles = remember(camera, widthPx, heightPx) {
      AirportMapProjection.visibleTiles(camera, widthPx, heightPx)
    }
    val tileRevision = rememberMapTileSnapshot(context, visibleTiles, layerStyle)

    Canvas(modifier = Modifier.fillMaxSize()) {
      if (size.width <= 0f || size.height <= 0f) return@Canvas
      // Read tileRevision to recompose canvas as tiles arrive
      val _rev = tileRevision

      // 1. Offline/loading fallback graticule & water coastlines
      AirportMapProjection.waterPolygons.forEach { polygon ->
        val path = Path()
        polygon.points.forEachIndexed { idx, (pLat, pLon) ->
          val pt = AirportMapProjection.projectMercator(pLat, pLon, camera, size.width, size.height)
          if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        path.close()
        drawPath(path = path, color = waterFill)
        drawPath(path = path, color = waterStroke, style = Stroke(width = 1.4f))
      }

      val stepDeg = if (camera.zoom >= 5.0) 2.5 else 5.0
      var lon = -180.0
      while (lon <= 180.0) {
        val p1 = AirportMapProjection.projectMercator(-75.0, lon, camera, size.width, size.height)
        val p2 = AirportMapProjection.projectMercator(75.0, lon, camera, size.width, size.height)
        if (p1.x in -100f..(size.width + 100f)) {
          drawLine(gridColor, Offset(p1.x, 0f), Offset(p2.x, size.height), strokeWidth = 0.8f)
        }
        lon += stepDeg
      }
      var lat = -70.0
      while (lat <= 70.0) {
        val p1 = AirportMapProjection.projectMercator(lat, -180.0, camera, size.width, size.height)
        if (p1.y in -100f..(size.height + 100f)) {
          drawLine(gridColor, Offset(0f, p1.y), Offset(size.width, p1.y), strokeWidth = 0.8f)
        }
        lat += stepDeg
      }

      // 2. Real Slippy Map Tiles drawn in Web Mercator coordinates
      visibleTiles.forEach { tile ->
        val bmp = AndroidMapTileLoader.getCached(tile.cacheKey(layerStyle))
        if (bmp != null) {
          val dstSize = IntSize(
            width = ceil(tile.tileSizePx).toInt() + 1,
            height = ceil(tile.tileSizePx).toInt() + 1,
          )
          drawImage(
            image = bmp,
            dstOffset = IntOffset(tile.drawX.roundToInt(), tile.drawY.roundToInt()),
            dstSize = dstSize,
          )
        }
      }

      // 3. Aviation overlays: range rings, airway vectors, and wind arrows
      val sel = stations.firstOrNull { it.icao == selectedIcao }
      if (sel != null) {
        val centerPt = AirportMapProjection.projectMercator(sel.latitude, sel.longitude, camera, size.width, size.height)
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        val center = Offset(centerPt.x, centerPt.y)
        drawCircle(
          color = palette.metarAmber.copy(alpha = 0.45f),
          radius = 60f,
          center = center,
          style = Stroke(width = 1.6f, pathEffect = dash),
        )
        drawCircle(
          color = palette.metarAmber.copy(alpha = 0.25f),
          radius = 120f,
          center = center,
          style = Stroke(width = 1.2f, pathEffect = dash),
        )
        stations.filter { it.icao != sel.icao }.forEach { other ->
          val otherPt = AirportMapProjection.projectMercator(other.latitude, other.longitude, camera, size.width, size.height)
          drawLine(
            color = corridorColor,
            start = center,
            end = Offset(otherPt.x, otherPt.y),
            strokeWidth = 1.2f,
            pathEffect = dash,
          )
        }
      }

      stations.forEach { st ->
        val wx = weatherByIcao[st.icao]
        val dir = wx?.wind?.directionDeg
        if (wx != null && !wx.wind.isCalm && dir != null) {
          val pt = AirportMapProjection.projectMercator(st.latitude, st.longitude, camera, size.width, size.height)
          val rad = Math.toRadians((dir - 90).toDouble())
          val len = 26f
          val endX = pt.x + (cos(rad) * len).toFloat()
          val endY = pt.y + (sin(rad) * len).toFloat()
          drawLine(
            color = palette.windArrow,
            start = Offset(pt.x, pt.y),
            end = Offset(endX, endY),
            strokeWidth = 2.6f,
          )
        }
      }
    }

    // 4. Interactive Pinned Airport Markers
    val pinHalfW = with(density) { 36.dp.toPx() }
    val pinHalfH = with(density) { 24.dp.toPx() }
    stations.forEach { station ->
      val pt = AirportMapProjection.projectMercator(
        lat = station.latitude,
        lon = station.longitude,
        camera = camera,
        widthPx = widthPx,
        heightPx = heightPx,
      )
      val marginPx = with(density) { 64.dp.toPx() }
      if (pt.x in -marginPx..(widthPx + marginPx) && pt.y in -marginPx..(heightPx + marginPx)) {
        val weather = weatherByIcao[station.icao]
        val category = AirportMapProjection.evaluateFlightCategory(weather)
        val dotColor = flightCategoryColor(category)
        val isSelected = station.icao == selectedIcao
        val isFav = station.icao in favoriteIcaos

        val shortWeather = if (weather != null && weather.observationTimeUtc != "--:--Z") {
          val temp = weather.temperatureC?.let { "${it}°" } ?: ""
          val wind = if (weather.wind.isCalm) {
            "Шт"
          } else {
            "${UnitConverter.formatWindSpeedValue(weather.wind.speedKt, windUnit)}${windUnit.label}"
          }
          "$temp $wind".trim()
        } else {
          null
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .offset {
              IntOffset(
                x = (pt.x - pinHalfW).roundToInt(),
                y = (pt.y - pinHalfH).roundToInt(),
              )
            }
            .sizeIn(minWidth = 72.dp, minHeight = 48.dp)
            .testTag("map_pin_${station.icao}")
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSelectPin(station.icao) }
            .padding(2.dp),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .size(if (isSelected) 22.dp else 16.dp)
              .clip(CircleShape)
              .background(if (isSelected) palette.metarAmber.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.25f))
              .border(
                width = if (isSelected) 2.2.dp else 1.2.dp,
                color = if (isSelected) palette.metarAmber else Color.White,
                shape = CircleShape,
              ),
          ) {
            Box(
              modifier = Modifier
                .size(if (isSelected) 12.dp else 9.dp)
                .clip(CircleShape)
                .background(dotColor),
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(
                if (isSelected) palette.buttonActiveBackground else palette.panelSurface.copy(alpha = 0.94f),
              )
              .border(
                width = if (isSelected) 1.4.dp else 0.8.dp,
                color = if (isSelected) palette.metarAmber else palette.borderStrong,
                shape = RoundedCornerShape(4.dp),
              )
              .padding(horizontal = 5.dp, vertical = 2.dp),
          ) {
            if (isFav) {
              Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = "Избранный аэродром",
                tint = palette.metarAmber,
                modifier = Modifier.size(10.dp),
              )
            }
            Text(
              text = station.icao,
              color = if (isSelected) palette.metarAmber else palette.textPrimary,
              fontSize = 10.5.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.ExtraBold,
            )
            if (shortWeather != null) {
              Text(
                text = shortWeather,
                color = palette.textSecondary,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
              )
            }
          }
        }
      }
    }

    // Top-left Flight Category Legend
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(palette.panelSurface.copy(alpha = 0.92f))
        .border(0.8.dp, palette.border, RoundedCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
      LegendDot("VFR", flightCategoryColor(FlightCategory.VFR))
      LegendDot("MVFR", flightCategoryColor(FlightCategory.MVFR))
      LegendDot("IFR", flightCategoryColor(FlightCategory.IFR))
    }

    // Map attribution pill at bottom-left
    Text(
      text = when (layerStyle) {
        MapLayerStyle.OSM -> "© OpenStreetMap"
        MapLayerStyle.CARTO_VOYAGER, MapLayerStyle.CARTO_DARK -> "© OpenStreetMap · CARTO"
        MapLayerStyle.ESRI_SATELLITE -> "© Esri World Imagery"
      },
      color = palette.textSecondary,
      fontSize = 9.5.sp,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(8.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(palette.panelSurface.copy(alpha = 0.85f))
        .padding(horizontal = 6.dp, vertical = 2.dp),
    )

    // Bottom-right Map Zoom & Reset Controls (48dp touch targets)
    Column(
      verticalArrangement = Arrangement.spacedBy(4.dp),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(8.dp),
    ) {
      MapControlButton(
        icon = Icons.Filled.Add,
        contentDescription = "Увеличить карту",
        testTag = "map_zoom_in_button",
        onClick = onZoomIn,
      )
      MapControlButton(
        icon = Icons.Filled.Remove,
        contentDescription = "Уменьшить карту",
        testTag = "map_zoom_out_button",
        onClick = onZoomOut,
      )
      MapControlButton(
        icon = Icons.Filled.CenterFocusStrong,
        contentDescription = "Сбросить масштаб карты",
        testTag = "map_reset_view_button",
        onClick = onResetView,
      )
    }
  }
}

@Composable
private fun LegendDot(
  label: String,
  color: Color,
) {
  val palette = LocalMeteoPalette.current
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color),
    )
    Text(
      text = label,
      color = palette.textPrimary,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
    )
  }
}

@Composable
private fun MapControlButton(
  icon: ImageVector,
  contentDescription: String,
  testTag: String,
  onClick: () -> Unit,
) {
  val palette = LocalMeteoPalette.current
  IconButton(
    onClick = onClick,
    modifier = Modifier
      .size(48.dp)
      .testTag(testTag)
      .clip(RoundedCornerShape(6.dp))
      .background(palette.panelSurface.copy(alpha = 0.94f))
      .border(1.dp, palette.borderStrong, RoundedCornerShape(6.dp)),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = palette.textPrimary,
      modifier = Modifier.size(20.dp),
    )
  }
}
