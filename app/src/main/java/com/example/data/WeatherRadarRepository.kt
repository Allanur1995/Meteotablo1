package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class RadarSourceMode(
  val id: String,
  val shortLabel: String,
  val fullLabel: String,
  val requiresKey: Boolean = false,
) {
  AUTO("auto", "Авто", "Авто (RainViewer → LibreWXR → NASA GIBS, без ключа)"),
  RAINVIEWER("rainviewer", "RainViewer", "RainViewer (до Z7, без ключа)"),
  LIBREWXR("librewxr", "LibreWXR", "LibreWXR (Глобальный радар + прогноз, без ключа)"),
  NASA_GIBS("nasa_gibs", "NASA GIBS", "NASA GIBS IMERG (Осадки со спутника, без ключа)"),
  OPENWEATHER("owm", "OWM (API)", "OpenWeatherMap (Осадки, нужен бесплатный API-ключ)", requiresKey = true);

  companion object {
    fun fromId(id: String?): RadarSourceMode =
      entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO
  }
}

data class RadarTileRequest(
  val url: String,
  val cacheKey: String,
  val fetchZ: Int,
  val fetchX: Int,
  val fetchY: Int,
  val subCol: Int,
  val subRow: Int,
  val subGridSize: Int,
)

data class RadarFrame(
  val timestampEpochSec: Long,
  val path: String,
  val timeUtcLabel: String,
  val isNowcast: Boolean,
  val maxTileZoom: Int = 7,
  val sourceMode: RadarSourceMode = RadarSourceMode.RAINVIEWER,
  val colorScheme: Int = 2,
  val tileOptions: String = "1_1",
  val customTileTemplate: String? = null,
) {
  fun tileUrl(host: String, z: Int, x: Int, y: Int, owmApiKey: String = ""): String {
    return resolveTileRequest(host, z, x, y, owmApiKey).url
  }

  fun resolveTileRequest(
    host: String,
    z: Int,
    x: Int,
    y: Int,
    owmApiKey: String = "",
  ): RadarTileRequest {
    val safeMaxZ = maxTileZoom.coerceIn(1, 16)
    val fetchZ = z.coerceAtMost(safeMaxZ)
    val shift = (z - fetchZ).coerceAtLeast(0)
    val gridSize = 1 shl shift
    val nFetch = 1 shl fetchZ
    val fetchX = (((x shr shift) % nFetch) + nFetch) % nFetch
    val fetchY = (y shr shift).coerceIn(0, nFetch - 1)
    val subCol = if (shift == 0) 0 else x and (gridSize - 1)
    val subRow = if (shift == 0) 0 else y and (gridSize - 1)

    val url = when {
      sourceMode == RadarSourceMode.OPENWEATHER -> {
        val cleanKey = owmApiKey.trim()
        "https://tile.openweathermap.org/map/precipitation_new/$fetchZ/$fetchX/$fetchY.png?appid=$cleanKey"
      }
      !customTileTemplate.isNullOrBlank() -> {
        customTileTemplate
          .replace("{z}", fetchZ.toString())
          .replace("{x}", fetchX.toString())
          .replace("{y}", fetchY.toString())
      }
      else -> {
        val cleanHost = host.trimEnd('/')
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        "$cleanHost$cleanPath/256/$fetchZ/$fetchX/$fetchY/$colorScheme/$tileOptions.png"
      }
    }

    val key = "radar_${sourceMode.id}_${timestampEpochSec}_${fetchZ}_${fetchX}_${fetchY}"
    return RadarTileRequest(
      url = url,
      cacheKey = key,
      fetchZ = fetchZ,
      fetchX = fetchX,
      fetchY = fetchY,
      subCol = subCol,
      subRow = subRow,
      subGridSize = gridSize,
    )
  }

  fun cacheKey(z: Int, x: Int, y: Int): String {
    val safeMaxZ = maxTileZoom.coerceIn(1, 16)
    val fetchZ = z.coerceAtMost(safeMaxZ)
    val shift = (z - fetchZ).coerceAtLeast(0)
    val nFetch = 1 shl fetchZ
    val fetchX = (((x shr shift) % nFetch) + nFetch) % nFetch
    val fetchY = (y shr shift).coerceIn(0, nFetch - 1)
    return "radar_${sourceMode.id}_${timestampEpochSec}_${fetchZ}_${fetchX}_${fetchY}"
  }
}

data class RadarTimelineSnapshot(
  val host: String,
  val frames: List<RadarFrame>,
  val generatedAtMillis: Long,
  val sourceLabel: String = "RainViewer Weather Radar",
  val activeSourceMode: RadarSourceMode = RadarSourceMode.RAINVIEWER,
  val fallbackNotice: String? = null,
)

class WeatherRadarRepository(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build(),
) {

  suspend fun fetchLatestTimeline(
    preferredSource: RadarSourceMode = RadarSourceMode.AUTO,
    owmApiKey: String = "",
  ): Result<RadarTimelineSnapshot> = withContext(Dispatchers.IO) {
    runCatching {
      when (preferredSource) {
        RadarSourceMode.NASA_GIBS -> buildNasaGibsSnapshot()
        RadarSourceMode.OPENWEATHER -> {
          val cleanKey = owmApiKey.trim()
          if (cleanKey.isBlank()) {
            error("Укажите API-ключ OpenWeatherMap в окне «API» или выберите бесплатный источник (Авто / LibreWXR / NASA GIBS)")
          }
          buildOpenWeatherSnapshot(cleanKey)
        }
        RadarSourceMode.LIBREWXR -> fetchLibreWxrSnapshot()
        RadarSourceMode.RAINVIEWER -> fetchRainViewerSnapshot()
        RadarSourceMode.AUTO -> fetchWithAutomaticFallback()
      }
    }
  }

  private fun fetchWithAutomaticFallback(): RadarTimelineSnapshot {
    val rvAttempt = runCatching { fetchRainViewerSnapshot() }
    val rvSnap = rvAttempt.getOrNull()
    if (rvSnap != null && rvSnap.frames.isNotEmpty()) {
      return rvSnap
    }

    val lwAttempt = runCatching { fetchLibreWxrSnapshot() }
    val lwSnap = lwAttempt.getOrNull()
    if (lwSnap != null && lwSnap.frames.isNotEmpty()) {
      val reason = rvAttempt.exceptionOrNull()?.message?.take(60)
      return lwSnap.copy(
        fallbackNotice = if (reason != null) {
          "RainViewer недоступен ($reason) → переключено на LibreWXR (без ключа)"
        } else {
          "Автопереключение на LibreWXR (глобальные осадки, без ключа)"
        },
      )
    }

    val reason = lwAttempt.exceptionOrNull()?.message ?: rvAttempt.exceptionOrNull()?.message
    return buildNasaGibsSnapshot(
      fallbackNotice = if (!reason.isNullOrBlank()) {
        "Живой радар ($reason) → показаны глобальные осадки NASA GIBS (без ключа)"
      } else {
        "Показаны глобальные спутниковые осадки NASA GIBS (без ключа)"
      },
    )
  }

  private fun fetchRainViewerSnapshot(): RadarTimelineSnapshot {
    val body = executeJsonGet(RAINVIEWER_MAPS_URL, "RainViewer")
    if (body.contains("api key", ignoreCase = true) || body.contains("required", ignoreCase = true)) {
      error("RainViewer запросил API-ключ")
    }
    val snap = parseRainViewerJson(body, sourceMode = RadarSourceMode.RAINVIEWER)
    if (snap.frames.isEmpty()) {
      error("RainViewer не вернул активных кадров")
    }
    return snap
  }

  private fun fetchLibreWxrSnapshot(): RadarTimelineSnapshot {
    val body = executeJsonGet(LIBREWXR_MAPS_URL, "LibreWXR")
    val snap = parseRainViewerJson(
      jsonBody = body,
      sourceMode = RadarSourceMode.LIBREWXR,
      defaultHost = LIBREWXR_HOST,
      sourceLabel = "LibreWXR Global Radar (без ключа)",
    )
    if (snap.frames.isEmpty()) {
      error("LibreWXR не вернул кадров")
    }
    return snap
  }

  private fun executeJsonGet(url: String, label: String): String {
    val req = Request.Builder()
      .url(url)
      .header("User-Agent", "Meteotablo/1.0")
      .get()
      .build()
    client.newCall(req).execute().use { resp ->
      val body = resp.body?.string().orEmpty()
      if (!resp.isSuccessful) {
        val hint = if (resp.code == 401 || resp.code == 403 || body.contains("api", ignoreCase = true)) {
          "$label: HTTP ${resp.code} (ограничение API)"
        } else {
          "$label: HTTP ${resp.code}"
        }
        error(hint)
      }
      if (body.isBlank()) {
        error("$label: пустой ответ")
      }
      return body
    }
  }

  companion object {
    const val RAINVIEWER_MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"
    const val LIBREWXR_MAPS_URL = "https://api.librewxr.net/public/weather-maps.json"
    const val LIBREWXR_HOST = "https://api.librewxr.net"
    const val NASA_GIBS_IMERG_TEMPLATE =
      "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/IMERG_Precipitation_Rate/default/default/GoogleMapsCompatible_Level6/{z}/{y}/{x}.png"

    fun buildNasaGibsSnapshot(
      nowMillis: Long = System.currentTimeMillis(),
      fallbackNotice: String? = null,
    ): RadarTimelineSnapshot {
      val utcFmt = SimpleDateFormat("HH:mm'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
      val epochSec = nowMillis / 1000L
      return RadarTimelineSnapshot(
        host = "https://gibs.earthdata.nasa.gov",
        frames = listOf(
          RadarFrame(
            timestampEpochSec = epochSec,
            path = "/nasa_gibs/imerg",
            timeUtcLabel = "${utcFmt.format(Date(nowMillis))} (IMERG)",
            isNowcast = false,
            maxTileZoom = 6,
            sourceMode = RadarSourceMode.NASA_GIBS,
            customTileTemplate = NASA_GIBS_IMERG_TEMPLATE,
          ),
        ),
        generatedAtMillis = nowMillis,
        sourceLabel = "NASA GIBS · IMERG Precipitation (без ключа)",
        activeSourceMode = RadarSourceMode.NASA_GIBS,
        fallbackNotice = fallbackNotice,
      )
    }

    fun buildOpenWeatherSnapshot(
      owmApiKey: String,
      nowMillis: Long = System.currentTimeMillis(),
    ): RadarTimelineSnapshot {
      val utcFmt = SimpleDateFormat("HH:mm'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
      val epochSec = nowMillis / 1000L
      return RadarTimelineSnapshot(
        host = "https://tile.openweathermap.org",
        frames = listOf(
          RadarFrame(
            timestampEpochSec = epochSec,
            path = "/map/precipitation_new",
            timeUtcLabel = "${utcFmt.format(Date(nowMillis))} (OWM)",
            isNowcast = false,
            maxTileZoom = 10,
            sourceMode = RadarSourceMode.OPENWEATHER,
          ),
        ),
        generatedAtMillis = nowMillis,
        sourceLabel = "OpenWeatherMap Precipitation (API-ключ)",
        activeSourceMode = RadarSourceMode.OPENWEATHER,
      )
    }

    fun parseRainViewerJson(
      jsonBody: String,
      nowMillis: Long = System.currentTimeMillis(),
      sourceMode: RadarSourceMode = RadarSourceMode.RAINVIEWER,
      defaultHost: String = "https://tilecache.rainviewer.com",
      sourceLabel: String = if (sourceMode == RadarSourceMode.LIBREWXR) {
        "LibreWXR Global Radar (без ключа)"
      } else {
        "RainViewer Weather Radar (до Z7, без ключа)"
      },
    ): RadarTimelineSnapshot {
      val root = JSONObject(jsonBody)
      val host = root.optString("host", defaultHost)
        .trim()
        .ifBlank { defaultHost }
      val radarObj = root.optJSONObject("radar") ?: JSONObject()
      val pastArr = radarObj.optJSONArray("past")
      val nowcastArr = radarObj.optJSONArray("nowcast")

      val maxZoom = if (sourceMode == RadarSourceMode.RAINVIEWER) 7 else 10
      val utcFmt = SimpleDateFormat("HH:mm'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
      }
      val frames = mutableListOf<RadarFrame>()

      if (pastArr != null) {
        val startIdx = (pastArr.length() - 10).coerceAtLeast(0)
        for (i in startIdx until pastArr.length()) {
          val obj = pastArr.optJSONObject(i) ?: continue
          val ts = obj.optLong("time", 0L)
          val path = obj.optString("path", "").trim()
          if (ts > 0L && path.isNotBlank()) {
            frames.add(
              RadarFrame(
                timestampEpochSec = ts,
                path = path,
                timeUtcLabel = utcFmt.format(Date(ts * 1000L)),
                isNowcast = false,
                maxTileZoom = maxZoom,
                sourceMode = sourceMode,
              ),
            )
          }
        }
      }

      // RainViewer free tier discontinued nowcast on Jan 2026; keep nowcast for LibreWXR
      if (nowcastArr != null && sourceMode != RadarSourceMode.RAINVIEWER) {
        for (i in 0 until minOf(nowcastArr.length(), 4)) {
          val obj = nowcastArr.optJSONObject(i) ?: continue
          val ts = obj.optLong("time", 0L)
          val path = obj.optString("path", "").trim()
          if (ts > 0L && path.isNotBlank()) {
            frames.add(
              RadarFrame(
                timestampEpochSec = ts,
                path = path,
                timeUtcLabel = "${utcFmt.format(Date(ts * 1000L))} (прогн.)",
                isNowcast = true,
                maxTileZoom = maxZoom,
                sourceMode = sourceMode,
              ),
            )
          }
        }
      }

      return RadarTimelineSnapshot(
        host = host,
        frames = frames,
        generatedAtMillis = nowMillis,
        sourceLabel = sourceLabel,
        activeSourceMode = sourceMode,
      )
    }
  }
}
