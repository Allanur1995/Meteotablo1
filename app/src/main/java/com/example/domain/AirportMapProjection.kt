package com.example.domain

import com.example.model.AirportStation
import com.example.model.DecodedWeather
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

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

data class GeoPolygon(
  val name: String,
  val points: List<Pair<Double, Double>>, // lat, lon
)

object AirportMapProjection {

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
   * Simplified geographic coastline polygons for regional aviation orientation:
   * Caspian Sea, Persian Gulf, Black Sea, Aral Sea, and Eastern Mediterranean / Red Sea.
   */
  val waterPolygons: List<GeoPolygon> = listOf(
    GeoPolygon(
      name = "Caspian Sea",
      points = listOf(
        46.9 to 48.0,
        47.1 to 51.8,
        45.2 to 53.3,
        43.8 to 51.4,
        41.5 to 52.5,
        40.8 to 53.9,
        39.9 to 52.8,
        38.7 to 53.5,
        37.2 to 53.9,
        36.6 to 52.5,
        37.4 to 49.5,
        39.2 to 49.2,
        40.4 to 50.4,
        41.9 to 48.6,
        43.9 to 47.6,
        45.6 to 47.4,
      ),
    ),
    GeoPolygon(
      name = "Persian Gulf",
      points = listOf(
        30.3 to 48.3,
        29.8 to 50.2,
        28.4 to 51.2,
        26.8 to 53.8,
        26.6 to 56.4,
        25.7 to 56.3,
        25.2 to 55.2,
        24.3 to 52.8,
        25.2 to 51.5,
        26.4 to 50.1,
        28.8 to 48.4,
      ),
    ),
    GeoPolygon(
      name = "Black Sea",
      points = listOf(
        46.5 to 30.8,
        46.1 to 33.4,
        44.5 to 33.7,
        45.1 to 36.6,
        43.5 to 39.8,
        41.6 to 41.6,
        41.0 to 38.0,
        42.0 to 35.1,
        41.2 to 29.1,
        42.5 to 27.6,
        44.2 to 28.7,
      ),
    ),
    GeoPolygon(
      name = "Aral Sea",
      points = listOf(
        46.6 to 59.5,
        46.2 to 61.5,
        44.5 to 61.0,
        43.9 to 58.6,
        45.4 to 58.3,
      ),
    ),
    GeoPolygon(
      name = "Eastern Mediterranean",
      points = listOf(
        36.8 to 26.0,
        36.9 to 30.6,
        36.4 to 35.9,
        34.0 to 35.6,
        31.2 to 34.2,
        31.2 to 27.5,
        33.0 to 25.0,
      ),
    ),
    GeoPolygon(
      name = "Red Sea",
      points = listOf(
        29.9 to 32.5,
        27.8 to 34.5,
        24.2 to 37.8,
        20.0 to 40.3,
        19.0 to 37.5,
        23.5 to 35.5,
        27.5 to 33.7,
      ),
    ),
  )
}
