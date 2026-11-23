package com.example.data

import com.example.domain.WeatherHistoryAnalyzer
import com.example.model.WeatherHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

interface WeatherHistoryStorage {
  fun loadAll(): Map<String, List<WeatherHistoryEntry>>
  fun saveAll(historyByIcao: Map<String, List<WeatherHistoryEntry>>)
}

class InMemoryWeatherHistoryStorage(
  initial: Map<String, List<WeatherHistoryEntry>> = emptyMap(),
) : WeatherHistoryStorage {
  private var stored: Map<String, List<WeatherHistoryEntry>> = initial

  override fun loadAll(): Map<String, List<WeatherHistoryEntry>> = stored

  override fun saveAll(historyByIcao: Map<String, List<WeatherHistoryEntry>>) {
    stored = historyByIcao
  }
}

object WeatherHistorySerializer {

  fun toJson(historyByIcao: Map<String, List<WeatherHistoryEntry>>): String {
    val root = JSONObject()
    val cutoff = System.currentTimeMillis() - WeatherHistoryAnalyzer.WINDOW_24H_MILLIS
    historyByIcao.forEach { (icao, entries) ->
      val cleanIcao = icao.trim().uppercase()
      val recent = entries
        .filter { it.observedAtMillis >= cutoff }
        .takeLast(WeatherHistoryAnalyzer.MAX_ENTRIES_PER_STATION)
      if (cleanIcao.length != 4 || recent.isEmpty()) return@forEach
      val arr = JSONArray()
      recent.forEach { e ->
        arr.put(
          JSONObject().apply {
            put("icao", cleanIcao)
            put("observationTimeUtc", e.observationTimeUtc)
            put("observedAtMillis", e.observedAtMillis)
            put("windSpeedKt", e.windSpeedKt)
            if (e.gustKt != null) put("gustKt", e.gustKt)
            if (e.windDirectionDeg != null) put("windDirectionDeg", e.windDirectionDeg)
            if (e.visibilityMeters != null) put("visibilityMeters", e.visibilityMeters)
            put("isCavok", e.isCavok)
            if (e.ceilingFeet != null) put("ceilingFeet", e.ceilingFeet)
            if (e.temperatureC != null) put("temperatureC", e.temperatureC)
            if (e.dewPointC != null) put("dewPointC", e.dewPointC)
            if (e.qnhHpa != null) put("qnhHpa", e.qnhHpa)
            if (e.qnhInHg != null) put("qnhInHg", e.qnhInHg)
            put("flightCategoryCode", e.flightCategoryCode)
            put("rawMetar", e.rawMetar)
            if (e.rawTaf.isNotBlank()) put("rawTaf", e.rawTaf)
            if (e.sourceLabel.isNotBlank()) put("sourceLabel", e.sourceLabel)
          },
        )
      }
      root.put(cleanIcao, arr)
    }
    return root.toString()
  }

  fun fromJson(raw: String?): Map<String, List<WeatherHistoryEntry>> {
    if (raw.isNullOrBlank()) return emptyMap()
    val cutoff = System.currentTimeMillis() - WeatherHistoryAnalyzer.WINDOW_24H_MILLIS
    return runCatching {
      val root = JSONObject(raw)
      buildMap {
        val keys = root.keys()
        while (keys.hasNext()) {
          val key = keys.next().trim().uppercase()
          if (key.length != 4) continue
          val arr = root.optJSONArray(key) ?: continue
          val entries = buildList {
            for (i in 0 until arr.length()) {
              val obj = arr.optJSONObject(i) ?: continue
              val obsAt = obj.optLong("observedAtMillis", 0L)
              if (obsAt < cutoff) continue
              val rawMetar = obj.optString("rawMetar", "")
              if (rawMetar.isBlank()) continue
              add(
                WeatherHistoryEntry(
                  icao = key,
                  observationTimeUtc = obj.optString("observationTimeUtc", "--:--Z"),
                  observedAtMillis = obsAt,
                  windSpeedKt = obj.optInt("windSpeedKt", 0).coerceAtLeast(0),
                  gustKt = if (obj.has("gustKt") && !obj.isNull("gustKt")) obj.optInt("gustKt") else null,
                  windDirectionDeg = if (obj.has("windDirectionDeg") && !obj.isNull("windDirectionDeg")) obj.optInt("windDirectionDeg") else null,
                  visibilityMeters = if (obj.has("visibilityMeters") && !obj.isNull("visibilityMeters")) obj.optInt("visibilityMeters") else null,
                  isCavok = obj.optBoolean("isCavok", false),
                  ceilingFeet = if (obj.has("ceilingFeet") && !obj.isNull("ceilingFeet")) obj.optInt("ceilingFeet") else null,
                  temperatureC = if (obj.has("temperatureC") && !obj.isNull("temperatureC")) obj.optInt("temperatureC") else null,
                  dewPointC = if (obj.has("dewPointC") && !obj.isNull("dewPointC")) obj.optInt("dewPointC") else null,
                  qnhHpa = if (obj.has("qnhHpa") && !obj.isNull("qnhHpa")) obj.optInt("qnhHpa") else null,
                  qnhInHg = if (obj.has("qnhInHg") && !obj.isNull("qnhInHg")) obj.optDouble("qnhInHg") else null,
                  flightCategoryCode = obj.optString("flightCategoryCode", "---"),
                  rawMetar = rawMetar,
                  rawTaf = obj.optString("rawTaf", ""),
                  sourceLabel = obj.optString("sourceLabel", ""),
                ),
              )
            }
          }.sortedBy { it.observedAtMillis }.takeLast(WeatherHistoryAnalyzer.MAX_ENTRIES_PER_STATION)
          if (entries.isNotEmpty()) {
            put(key, entries)
          }
        }
      }
    }.getOrDefault(emptyMap())
  }
}
