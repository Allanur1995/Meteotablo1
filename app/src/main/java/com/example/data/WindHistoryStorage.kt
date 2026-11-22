package com.example.data

import com.example.model.WindHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

interface WindHistoryStorage {
  fun loadAll(): Map<String, List<WindHistoryEntry>>
  fun saveAll(historyByIcao: Map<String, List<WindHistoryEntry>>)
}

class InMemoryWindHistoryStorage(
  initial: Map<String, List<WindHistoryEntry>> = emptyMap(),
) : WindHistoryStorage {
  private var stored: Map<String, List<WindHistoryEntry>> = initial

  override fun loadAll(): Map<String, List<WindHistoryEntry>> = stored

  override fun saveAll(historyByIcao: Map<String, List<WindHistoryEntry>>) {
    stored = historyByIcao
  }
}

object WindHistorySerializer {

  fun toJson(historyByIcao: Map<String, List<WindHistoryEntry>>): String {
    val root = JSONObject()
    historyByIcao.forEach { (icao, entries) ->
      val cleanIcao = icao.trim().uppercase()
      if (cleanIcao.length != 4 || entries.isEmpty()) return@forEach
      val arr = JSONArray()
      entries.takeLast(12).forEach { e ->
        arr.put(
          JSONObject().apply {
            put("observationTimeUtc", e.observationTimeUtc)
            put("speedKt", e.speedKt)
            if (e.gustKt != null) put("gustKt", e.gustKt)
            if (e.directionDeg != null) put("directionDeg", e.directionDeg)
            if (e.rawMetar.isNotBlank()) put("rawMetar", e.rawMetar)
            put("recordedAtMillis", e.recordedAtMillis)
          },
        )
      }
      root.put(cleanIcao, arr)
    }
    return root.toString()
  }

  fun fromJson(raw: String?): Map<String, List<WindHistoryEntry>> {
    if (raw.isNullOrBlank()) return emptyMap()
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
              add(
                WindHistoryEntry(
                  observationTimeUtc = obj.optString("observationTimeUtc", "--:--Z"),
                  speedKt = obj.optInt("speedKt", 0).coerceAtLeast(0),
                  gustKt = if (obj.has("gustKt") && !obj.isNull("gustKt")) obj.optInt("gustKt") else null,
                  directionDeg = if (obj.has("directionDeg") && !obj.isNull("directionDeg")) obj.optInt("directionDeg") else null,
                  rawMetar = obj.optString("rawMetar", ""),
                  recordedAtMillis = obj.optLong("recordedAtMillis", 0L),
                ),
              )
            }
          }.takeLast(12)
          if (entries.isNotEmpty()) {
            put(key, entries)
          }
        }
      }
    }.getOrDefault(emptyMap())
  }
}
