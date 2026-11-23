package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

enum class MapRegionPreset(
  val label: String,
  val minLat: Double,
  val maxLat: Double,
  val minLon: Double,
  val maxLon: Double,
) {
  TURKMENISTAN(
    label = "Туркменистан",
    minLat = 35.0,
    maxLat = 43.2,
    minLon = 51.5,
    maxLon = 66.5,
  ),
  REGIONAL(
    label = "Регион",
    minLat = 22.0,
    maxLat = 58.0,
    minLon = 25.0,
    maxLon = 68.0,
  ),
  ALL_STATIONS(
    label = "Все станции",
    minLat = 20.0,
    maxLat = 60.0,
    minLon = 20.0,
    maxLon = 70.0,
  ),
}

enum class MapLayerStyle(
  val id: String,
  val label: String,
) {
  OSM("osm", "Карта (OSM)"),
  CARTO_VOYAGER("voyager", "Детальная"),
  CARTO_DARK("dark", "Тёмная"),
  ESRI_SATELLITE("satellite", "Спутник");

  fun tileUrl(z: Int, x: Int, y: Int): String = when (this) {
    OSM -> "https://tile.openstreetmap.org/$z/$x/$y.png"
    CARTO_VOYAGER -> "https://basemaps.cartocdn.com/rastertiles/voyager/$z/$x/$y.png"
    CARTO_DARK -> "https://basemaps.cartocdn.com/dark_all/$z/$x/$y.png"
    ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
  }
}

enum class FlightCategory(
  val code: String,
  val description: String,
) {
  VFR("VFR", "Хорошие метеоусловия"),
  MVFR("MVFR", "Умеренные ограничения"),
  IFR("IFR", "Сложные метеоусловия"),
  UNKNOWN("Н/Д", "Ожидание METAR"),
}

data class MapBounds(
  val minLat: Double,
  val maxLat: Double,
  val minLon: Double,
  val maxLon: Double,
)

data class ProjectedPoint(
  val x: Float,
  val y: Float,
)

data class MapViewportCamera(
  val centerLat: Double,
  val centerLon: Double,
  val zoom: Double,
)

data class TilePlacement(
  val z: Int,
  val x: Int,
  val y: Int,
  val drawX: Float,
  val drawY: Float,
  val tileSizePx: Float,
) {
  fun cacheKey(style: MapLayerStyle): String = "${style.id}_${z}_${x}_${y}"
}

data class GeoPolygon(
  val name: String,
  val points: List<Pair<Double, Double>>, // lat, lon
)

object AirportMapProjection {

  private const val MIN_LAT_MERCATOR = -85.05112878
  private const val MAX_LAT_MERCATOR = 85.05112878

  fun evaluateFlightCategory(weather: DecodedWeather?): FlightCategory {
    if (weather == null || weather.observationTimeUtc == "--:--Z") {
      return FlightCategory.UNKNOWN
    }
    if (weather.isCavok) {
      val gustOrWind = weather.wind.gustKt ?: weather.wind.speedKt
      return if (gustOrWind >= 25) FlightCategory.MVFR else FlightCategory.VFR
    }
    val vis = weather.visibilityMeters
    val ceiling = weather.ceilingMeters
    val phenom = weather.weatherPhenomena.lowercase()
    val hasSeverePhenom = phenom.contains("гроз") ||
      phenom.contains("шквал") ||
      phenom.contains("лив") ||
      phenom.contains("туман") ||
      phenom.contains("снег") ||
      phenom.contains("буря")

    if ((vis != null && vis < 1500) || (ceiling != null && ceiling < 150) || hasSeverePhenom) {
      return FlightCategory.IFR
    }
    val gustOrWind = weather.wind.gustKt ?: weather.wind.speedKt
    if ((vis != null && vis < 5000) || (ceiling != null && ceiling < 450) || gustOrWind >= 22) {
      return FlightCategory.MVFR
    }
    return FlightCategory.VFR
  }

  fun computeBounds(
    preset: MapRegionPreset,
    stations: List<AirportStation>,
  ): MapBounds {
    if (preset != MapRegionPreset.ALL_STATIONS || stations.isEmpty()) {
      return MapBounds(preset.minLat, preset.maxLat, preset.minLon, preset.maxLon)
    }
    var minLat = stations.minOf { it.latitude }
    var maxLat = stations.maxOf { it.latitude }
    var minLon = stations.minOf { it.longitude }
    var maxLon = stations.maxOf { it.longitude }

    val latSpan = max(6.0, maxLat - minLat)
    val lonSpan = max(8.0, maxLon - minLon)
    minLat = (minLat - latSpan * 0.16).coerceAtLeast(-75.0)
    maxLat = (maxLat + latSpan * 0.16).coerceAtMost(75.0)
    minLon = (minLon - lonSpan * 0.16).coerceAtLeast(-180.0)
    maxLon = (maxLon + lonSpan * 0.16).coerceAtMost(180.0)
    return MapBounds(minLat, maxLat, minLon, maxLon)
  }

  fun lonToWorldX(lon: Double, zoomInt: Int): Double {
    val n = 2.0.pow(zoomInt)
    return ((lon + 180.0) / 360.0) * n
  }

  fun latToWorldY(lat: Double, zoomInt: Int): Double {
    val clamped = lat.coerceIn(MIN_LAT_MERCATOR, MAX_LAT_MERCATOR)
    val latRad = Math.toRadians(clamped)
    val n = 2.0.pow(zoomInt)
    return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n
  }

  fun worldXToLon(wx: Double, zoomInt: Int): Double {
    val n = 2.0.pow(zoomInt)
    var lon = (wx / n) * 360.0 - 180.0
    while (lon < -180.0) lon += 360.0
    while (lon > 180.0) lon -= 360.0
    return lon
  }

  fun worldYToLat(wy: Double, zoomInt: Int): Double {
    val n = 2.0.pow(zoomInt)
    val clampedWy = wy.coerceIn(0.0, n)
    val mercN = PI - (2.0 * PI * clampedWy) / n
    return Math.toDegrees(atan(sinh(mercN))).coerceIn(-82.0, 82.0)
  }

  fun cameraForRegion(
    preset: MapRegionPreset,
    stations: List<AirportStation>,
    widthPx: Float,
    heightPx: Float,
  ): MapViewportCamera {
    val bounds = computeBounds(preset, stations)
    val topY = latToWorldY(bounds.maxLat, 0)
    val botY = latToWorldY(bounds.minLat, 0)
    val midY = (topY + botY) / 2.0
    val centerLat = worldYToLat(midY, 0)
    val centerLon = (bounds.minLon + bounds.maxLon) / 2.0

    if (widthPx <= 1f || heightPx <= 1f) {
      val fallbackZoom = when (preset) {
        MapRegionPreset.TURKMENISTAN -> 5.4
        MapRegionPreset.REGIONAL -> 3.8
        MapRegionPreset.ALL_STATIONS -> 3.0
      }
      return MapViewportCamera(centerLat, centerLon, fallbackZoom)
    }

    val spanX = max(0.005, lonToWorldX(bounds.maxLon, 0) - lonToWorldX(bounds.minLon, 0))
    val spanY = max(0.005, botY - topY)
    val usableW = max(64.0, widthPx * 0.86)
    val usableH = max(64.0, heightPx * 0.82)

    val zoomX = ln(usableW / (256.0 * spanX)) / ln(2.0)
    val zoomY = ln(usableH / (256.0 * spanY)) / ln(2.0)
    val zoom = min(zoomX, zoomY).coerceIn(2.0, 12.0)
    return MapViewportCamera(centerLat, centerLon, zoom)
  }

  fun projectMercator(
    lat: Double,
    lon: Double,
    camera: MapViewportCamera,
    widthPx: Float,
    heightPx: Float,
  ): ProjectedPoint {
    if (widthPx <= 0f || heightPx <= 0f) return ProjectedPoint(0f, 0f)
    val clampedZoom = camera.zoom.coerceIn(1.5, 16.0)
    val tileZ = floor(clampedZoom).toInt().coerceIn(1, 16)
    val tileSizePx = (256.0 * 2.0.pow(clampedZoom - tileZ)).toFloat()

    val centerWx = lonToWorldX(camera.centerLon, tileZ)
    val centerWy = latToWorldY(camera.centerLat, tileZ)
    val targetWx = lonToWorldX(lon, tileZ)
    val targetWy = latToWorldY(lat, tileZ)

    val x = widthPx / 2f + ((targetWx - centerWx) * tileSizePx).toFloat()
    val y = heightPx / 2f + ((targetWy - centerWy) * tileSizePx).toFloat()
    return ProjectedPoint(x, y)
  }

  fun panCamera(
    camera: MapViewportCamera,
    dxPx: Float,
    dyPx: Float,
  ): MapViewportCamera {
    val clampedZoom = camera.zoom.coerceIn(1.5, 16.0)
    val tileZ = floor(clampedZoom).toInt().coerceIn(1, 16)
    val tileSizePx = 256.0 * 2.0.pow(clampedZoom - tileZ)

    val centerWx = lonToWorldX(camera.centerLon, tileZ) - dxPx / tileSizePx
    val centerWy = latToWorldY(camera.centerLat, tileZ) - dyPx / tileSizePx

    return camera.copy(
      centerLat = worldYToLat(centerWy, tileZ),
      centerLon = worldXToLon(centerWx, tileZ),
    )
  }

  fun visibleTiles(
    camera: MapViewportCamera,
    widthPx: Float,
    heightPx: Float,
  ): List<TilePlacement> {
    if (widthPx <= 0f || heightPx <= 0f) return emptyList()
    val clampedZoom = camera.zoom.coerceIn(1.5, 16.0)
    val tileZ = floor(clampedZoom).toInt().coerceIn(1, 16)
    val n = 1 shl tileZ
    val tileSizePx = (256.0 * 2.0.pow(clampedZoom - tileZ)).toFloat()

    val centerWx = lonToWorldX(camera.centerLon, tileZ)
    val centerWy = latToWorldY(camera.centerLat, tileZ)

    val halfTilesW = (widthPx / 2f) / tileSizePx
    val halfTilesH = (heightPx / 2f) / tileSizePx

    val minTx = floor(centerWx - halfTilesW).toInt()
    val maxTx = ceil(centerWx + halfTilesW).toInt() - 1
    val minTy = floor(centerWy - halfTilesH).toInt().coerceIn(0, n - 1)
    val maxTy = (ceil(centerWy + halfTilesH).toInt() - 1).coerceIn(0, n - 1)

    val out = mutableListOf<TilePlacement>()
    for (ty in minTy..maxTy) {
      for (tx in minTx..maxTx) {
        val wrappedX = ((tx % n) + n) % n
        val drawX = widthPx / 2f + ((tx - centerWx) * tileSizePx).toFloat()
        val drawY = heightPx / 2f + ((ty - centerWy) * tileSizePx).toFloat()
        out.add(
          TilePlacement(
            z = tileZ,
            x = wrappedX,
            y = ty,
            drawX = drawX,
            drawY = drawY,
            tileSizePx = tileSizePx,
          ),
        )
      }
    }
    return out
  }

  /**
   * Legacy equirectangular projection kept for simple overlays/tests.
   */
  fun project(
    lat: Double,
    lon: Double,
    bounds: MapBounds,
    widthPx: Float,
    heightPx: Float,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
  ): ProjectedPoint {
    if (widthPx <= 0f || heightPx <= 0f) return ProjectedPoint(0f, 0f)
    val midLatRad = Math.toRadians((bounds.minLat + bounds.maxLat) / 2.0)
    val lonScale = cos(midLatRad).coerceIn(0.25, 1.0)

    val rawWidthDeg = max(0.5, (bounds.maxLon - bounds.minLon) * lonScale)
    val rawHeightDeg = max(0.5, bounds.maxLat - bounds.minLat)

    val padX = widthPx * 0.06f
    val padY = heightPx * 0.08f
    val usableW = max(1f, widthPx - padX * 2f)
    val usableH = max(1f, heightPx - padY * 2f)

    val scale = min(usableW / rawWidthDeg.toFloat(), usableH / rawHeightDeg.toFloat())
    val contentW = rawWidthDeg.toFloat() * scale
    val contentH = rawHeightDeg.toFloat() * scale

    val originX = (widthPx - contentW) / 2f
    val originY = (heightPx - contentH) / 2f

    val normX = ((lon - bounds.minLon) * lonScale) / rawWidthDeg
    val normY = (bounds.maxLat - lat) / rawHeightDeg

    val baseX = originX + normX.toFloat() * contentW
    val baseY = originY + normY.toFloat() * contentH

    val cx = widthPx / 2f
    val cy = heightPx / 2f
    val finalX = cx + (baseX - cx) * zoom + panX
    val finalY = cy + (baseY - cy) * zoom + panY

    return ProjectedPoint(finalX, finalY)
  }

  /**
   * Simplified geographic coastline polygons rendered underneath real map tiles
   * while tiles load or when offline.
   */
  val waterPolygons: List<GeoPolygon> = listOf(
    GeoPolygon(
      name = "Caspian Sea",
      points = listOf(
        46.9 to 48.0, 47.1 to 51.8, 45.2 to 53.3, 43.8 to 51.4,
        41.5 to 52.5, 40.8 to 53.9, 39.9 to 52.8, 38.7 to 53.5,
        37.2 to 53.9, 36.6 to 52.5, 37.4 to 49.5, 39.2 to 49.2,
        40.4 to 50.4, 41.9 to 48.6, 43.9 to 47.6, 45.6 to 47.4,
      ),
    ),
    GeoPolygon(
      name = "Persian Gulf",
      points = listOf(
        30.3 to 48.3, 29.8 to 50.2, 28.4 to 51.2, 26.8 to 53.8,
        26.6 to 56.4, 25.7 to 56.3, 25.2 to 55.2, 24.3 to 52.8,
        25.2 to 51.5, 26.4 to 50.1, 28.8 to 48.4,
      ),
    ),
    GeoPolygon(
      name = "Black Sea",
      points = listOf(
        46.5 to 30.8, 46.1 to 33.4, 44.5 to 33.7, 45.1 to 36.6,
        43.5 to 39.8, 41.6 to 41.6, 41.0 to 38.0, 42.0 to 35.1,
        41.2 to 29.1, 42.5 to 27.6, 44.2 to 28.7,
      ),
    ),
    GeoPolygon(
      name = "Aral Sea",
      points = listOf(
        46.6 to 59.5, 46.2 to 61.5, 44.5 to 61.0, 43.9 to 58.6, 45.4 to 58.3,
      ),
    ),
    GeoPolygon(
      name = "Eastern Mediterranean",
      points = listOf(
        36.8 to 26.0, 36.9 to 30.6, 36.4 to 35.9, 34.0 to 35.6,
        31.2 to 34.2, 31.2 to 27.5, 33.0 to 25.0,
      ),
    ),
    GeoPolygon(
      name = "Red Sea",
      points = listOf(
        29.9 to 32.5, 27.8 to 34.5, 24.2 to 37.8, 20.0 to 40.3,
        19.0 to 37.5, 23.5 to 35.5, 27.5 to 33.7,
      ),
    ),
  )
}
