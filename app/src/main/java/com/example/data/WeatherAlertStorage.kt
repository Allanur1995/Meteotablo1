package com.example.data

import com.example.model.FavoriteWeatherSnapshot
import com.example.model.WeatherAlertConfig
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherChangeAlert
import org.json.JSONArray
import org.json.JSONObject

interface WeatherAlertStorage {
  fun loadConfig(): WeatherAlertConfig
  fun saveConfig(config: WeatherAlertConfig)
  fun loadSnapshots(): Map<String, FavoriteWeatherSnapshot>
  fun saveSnapshots(snapshots: Map<String, FavoriteWeatherSnapshot>)
  fun loadAlerts(): List<WeatherChangeAlert>
  fun saveAlerts(alerts: List<WeatherChangeAlert>)
}

class InMemoryWeatherAlertStorage(
  initialConfig: WeatherAlertConfig = WeatherAlertConfig(),
  initialSnapshots: Map<String, FavoriteWeatherSnapshot> = emptyMap(),
  initialAlerts: List<WeatherChangeAlert> = emptyList(),
) : WeatherAlertStorage {
  private var config = initialConfig
  private var snapshots = initialSnapshots
  private var alerts = initialAlerts

  override fun loadConfig(): WeatherAlertConfig = config
  override fun saveConfig(config: WeatherAlertConfig) {
    this.config = config
  }

  override fun loadSnapshots(): Map<String, FavoriteWeatherSnapshot> = snapshots
  override fun saveSnapshots(snapshots: Map<String, FavoriteWeatherSnapshot>) {
    this.snapshots = snapshots
  }

  override fun loadAlerts(): List<WeatherChangeAlert> = alerts
  override fun saveAlerts(alerts: List<WeatherChangeAlert>) {
    this.alerts = alerts
  }
}

object WeatherAlertSerializer {

  fun configToJson(config: WeatherAlertConfig): String {
    return JSONObject().apply {
      put("enabled", config.enabled)
      put("windChangeThresholdKt", config.windChangeThresholdKt)
      put("visibilityThresholdMeters", config.visibilityThresholdMeters)
      put("ceilingThresholdMeters", config.ceilingThresholdMeters)
      put("checkIntervalMinutes", config.checkIntervalMinutes)
    }.toString()
  }

  fun configFromJson(raw: String?): WeatherAlertConfig {
    if (raw.isNullOrBlank()) return WeatherAlertConfig()
    return runCatching {
      val obj = JSONObject(raw)
      WeatherAlertConfig(
        enabled = obj.optBoolean("enabled", true),
        windChangeThresholdKt = obj.optInt("windChangeThresholdKt", 8).coerceIn(3, 30),
        visibilityThresholdMeters = obj.optInt("visibilityThresholdMeters", 3000).coerceIn(500, 9999),
        ceilingThresholdMeters = obj.optInt("ceilingThresholdMeters", 300).coerceIn(60, 1500),
        checkIntervalMinutes = obj.optInt("checkIntervalMinutes", 15).coerceIn(5, 120),
      )
    }.getOrDefault(WeatherAlertConfig())
  }

  fun snapshotsToJson(snapshots: Map<String, FavoriteWeatherSnapshot>): String {
    val arr = JSONArray()
    snapshots.values.forEach { s ->
      arr.put(
        JSONObject().apply {
          put("icao", s.icao)
          put("observationTimeUtc", s.observationTimeUtc)
          put("windSpeedKt", s.windSpeedKt)
          if (s.gustKt != null) put("gustKt", s.gustKt)
          if (s.windDirectionDeg != null) put("windDirectionDeg", s.windDirectionDeg)
          if (s.visibilityMeters != null) put("visibilityMeters", s.visibilityMeters)
          if (s.ceilingMeters != null) put("ceilingMeters", s.ceilingMeters)
          put("weatherPhenomena", s.weatherPhenomena)
          if (s.qnhHpa != null) put("qnhHpa", s.qnhHpa)
          put("rawMetar", s.rawMetar)
          put("updatedAtMillis", s.updatedAtMillis)
        },
      )
    }
    return arr.toString()
  }

  fun snapshotsFromJson(raw: String?): Map<String, FavoriteWeatherSnapshot> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
      val arr = JSONArray(raw)
      buildMap {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val icao = obj.optString("icao").trim().uppercase()
          if (icao.length != 4) continue
          put(
            icao,
            FavoriteWeatherSnapshot(
              icao = icao,
              observationTimeUtc = obj.optString("observationTimeUtc", "--:--Z"),
              windSpeedKt = obj.optInt("windSpeedKt", 0),
              gustKt = if (obj.has("gustKt") && !obj.isNull("gustKt")) obj.optInt("gustKt") else null,
              windDirectionDeg = if (obj.has("windDirectionDeg") && !obj.isNull("windDirectionDeg")) obj.optInt("windDirectionDeg") else null,
              visibilityMeters = if (obj.has("visibilityMeters") && !obj.isNull("visibilityMeters")) obj.optInt("visibilityMeters") else null,
              ceilingMeters = if (obj.has("ceilingMeters") && !obj.isNull("ceilingMeters")) obj.optInt("ceilingMeters") else null,
              weatherPhenomena = obj.optString("weatherPhenomena", ""),
              qnhHpa = if (obj.has("qnhHpa") && !obj.isNull("qnhHpa")) obj.optInt("qnhHpa") else null,
              rawMetar = obj.optString("rawMetar", ""),
              updatedAtMillis = obj.optLong("updatedAtMillis", 0L),
            ),
          )
        }
      }
    }.getOrDefault(emptyMap())
  }

  fun alertsToJson(alerts: List<WeatherChangeAlert>): String {
    val arr = JSONArray()
    alerts.take(40).forEach { a ->
      arr.put(
        JSONObject().apply {
          put("id", a.id)
          put("icao", a.icao)
          put("airportName", a.airportName)
          put("severity", a.severity.name)
          put("title", a.title)
          put("summary", a.summary)
          put("reasons", JSONArray(a.reasons))
          put("observationTimeUtc", a.observationTimeUtc)
          put("rawMetar", a.rawMetar)
          put("timestampLocal", a.timestampLocal)
          put("createdAtMillis", a.createdAtMillis)
          put("isRead", a.isRead)
        },
      )
    }
    return arr.toString()
  }

  fun alertsFromJson(raw: String?): List<WeatherChangeAlert> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val id = obj.optString("id").ifBlank { continue }
          val icao = obj.optString("icao").trim().uppercase()
          if (icao.length != 4) continue
          val reasonsArr = obj.optJSONArray("reasons")
          val reasons = buildList {
            if (reasonsArr != null) {
              for (r in 0 until reasonsArr.length()) {
                val txt = reasonsArr.optString(r)
                if (txt.isNotBlank()) add(txt)
              }
            }
          }
          val severity = WeatherAlertSeverity.entries.firstOrNull {
            it.name == obj.optString("severity")
          } ?: WeatherAlertSeverity.WARNING
          add(
            WeatherChangeAlert(
              id = id,
              icao = icao,
              airportName = obj.optString("airportName", icao),
              severity = severity,
              title = obj.optString("title", icao),
              summary = obj.optString("summary", ""),
              reasons = reasons,
              observationTimeUtc = obj.optString("observationTimeUtc", "--:--Z"),
              rawMetar = obj.optString("rawMetar", ""),
              timestampLocal = obj.optString("timestampLocal", "--:--"),
              createdAtMillis = obj.optLong("createdAtMillis", 0L),
              isRead = obj.optBoolean("isRead", false),
            ),
          )
        }
      }
    }.getOrDefault(emptyList())
  }
}
