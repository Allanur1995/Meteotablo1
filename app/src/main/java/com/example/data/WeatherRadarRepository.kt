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

data class RadarFrame(
  val timestampEpochSec: Long,
  val path: String,
  val timeUtcLabel: String,
  val isNowcast: Boolean,
) {
  fun tileUrl(host: String, z: Int, x: Int, y: Int, colorScheme: Int = 2): String {
    val cleanHost = host.trimEnd('/')
    val cleanPath = if (path.startsWith("/")) path else "/$path"
    return "$cleanHost$cleanPath/256/$z/$x/$y/$colorScheme/1_1.png"
  }

  fun cacheKey(z: Int, x: Int, y: Int): String = "radar_${timestampEpochSec}_${z}_${x}_${y}"
}

data class RadarTimelineSnapshot(
  val host: String,
  val frames: List<RadarFrame>,
  val generatedAtMillis: Long,
  val sourceLabel: String = "RainViewer Weather Radar",
)

class WeatherRadarRepository(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build(),
) {

  suspend fun fetchLatestTimeline(): Result<RadarTimelineSnapshot> = withContext(Dispatchers.IO) {
    runCatching {
      val req = Request.Builder()
        .url(RAINVIEWER_MAPS_URL)
        .header("User-Agent", "Meteotablo/1.0")
        .get()
        .build()
      client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) {
          error("Radar API HTTP ${resp.code}")
        }
        val body = resp.body?.string().orEmpty()
        if (body.isBlank()) {
          error("Пустой ответ радарной службы")
        }
        parseRainViewerJson(body)
      }
    }
  }

  companion object {
    const val RAINVIEWER_MAPS_URL = "https://api.rainviewer.com/public/weather-maps.json"

    fun parseRainViewerJson(
      jsonBody: String,
      nowMillis: Long = System.currentTimeMillis(),
    ): RadarTimelineSnapshot {
      val root = JSONObject(jsonBody)
      val host = root.optString("host", "https://tilecache.rainviewer.com")
        .trim()
        .ifBlank { "https://tilecache.rainviewer.com" }
      val radarObj = root.optJSONObject("radar") ?: JSONObject()
      val pastArr = radarObj.optJSONArray("past")
      val nowcastArr = radarObj.optJSONArray("nowcast")

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
              ),
            )
          }
        }
      }

      if (nowcastArr != null) {
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
              ),
            )
          }
        }
      }

      return RadarTimelineSnapshot(
        host = host,
        frames = frames,
        generatedAtMillis = nowMillis,
      )
    }
  }
}
