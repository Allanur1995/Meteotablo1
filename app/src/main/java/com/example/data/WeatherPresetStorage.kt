package com.example.data

import com.example.model.PressureUnit
import com.example.model.SavedWeatherPreset
import com.example.model.WindSpeedUnit
import org.json.JSONArray
import org.json.JSONObject

interface WeatherPresetStorage {
  fun loadPresets(): List<SavedWeatherPreset>
  fun savePresets(presets: List<SavedWeatherPreset>)
}

class InMemoryWeatherPresetStorage(
  initial: List<SavedWeatherPreset> = emptyList(),
) : WeatherPresetStorage {
  private var stored: List<SavedWeatherPreset> = initial

  override fun loadPresets(): List<SavedWeatherPreset> = stored

  override fun savePresets(presets: List<SavedWeatherPreset>) {
    stored = presets
  }
}

object WeatherPresetSerializer {

  fun toJson(presets: List<SavedWeatherPreset>): String {
    val arr = JSONArray()
    presets.forEach { preset ->
      val obj = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("icao", preset.icao)
        put("runwayPairIndex", preset.runwayPairIndex)
        put("runwayLabel", preset.runwayLabel)
        put("windUnit", preset.windUnit.name)
        put("pressureUnit", preset.pressureUnit.name)
        if (preset.sourceMode != null) {
          put("sourceMode", preset.sourceMode.name)
        }
      }
      arr.put(obj)
    }
    return arr.toString()
  }

  fun fromJson(raw: String?): List<SavedWeatherPreset> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(raw)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val icao = obj.optString("icao", "").trim().uppercase()
          if (icao.length != 4) continue
          val runwayIndex = obj.optInt("runwayPairIndex", 0).coerceAtLeast(0)
          val runwayLabel = obj.optString("runwayLabel", "").trim().ifBlank { "ВПП #${runwayIndex + 1}" }
          val windUnit = WindSpeedUnit.entries.firstOrNull {
            it.name == obj.optString("windUnit", "")
          } ?: WindSpeedUnit.KT
          val pressureUnit = PressureUnit.entries.firstOrNull {
            it.name == obj.optString("pressureUnit", "")
          } ?: PressureUnit.HPA
          val sourceMode = obj.optString("sourceMode", "").takeIf { it.isNotBlank() }?.let { modeName ->
            DataSourceMode.entries.firstOrNull { it.name == modeName }
          }
          val id = obj.optString("id", "").trim().ifBlank {
            SavedWeatherPreset.buildId(icao, runwayIndex, windUnit, pressureUnit)
          }
          val name = obj.optString("name", "").trim().ifBlank {
            SavedWeatherPreset.defaultLabel(icao, runwayLabel, windUnit, pressureUnit)
          }
          add(
            SavedWeatherPreset(
              id = id,
              name = name,
              icao = icao,
              runwayPairIndex = runwayIndex,
              runwayLabel = runwayLabel,
              windUnit = windUnit,
              pressureUnit = pressureUnit,
              sourceMode = sourceMode,
            ),
          )
        }
      }.distinctBy { it.id }
    }.getOrDefault(emptyList())
  }
}
