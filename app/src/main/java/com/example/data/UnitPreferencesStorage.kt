package com.example.data

import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit

enum class AutoRefreshInterval(
  val minutes: Int,
  val label: String,
) {
  ONE_MIN(1, "60 сек"),
  FIVE_MIN(5, "5 мин"),
  FIFTEEN_MIN(15, "15 мин"),
  THIRTY_MIN(30, "30 мин");

  val shortLabel: String
    get() = if (minutes == 1) "60с" else "${minutes}м"

  val descriptionLabel: String
    get() = if (minutes == 1) "60 сек" else "$minutes мин"

  companion object {
    fun fromMinutes(minutes: Int): AutoRefreshInterval =
      entries.firstOrNull { it.minutes == minutes } ?: ONE_MIN
  }
}

data class UnitPreferences(
  val windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  val pressureUnit: PressureUnit = PressureUnit.HPA,
  val autoRefreshEnabled: Boolean = true,
  val autoRefreshIntervalMinutes: Int = AutoRefreshInterval.ONE_MIN.minutes,
  val isNightMode: Boolean = false,
)

interface UnitPreferencesStorage {
  fun loadPreferences(): UnitPreferences
  fun savePreferences(preferences: UnitPreferences)
}

class InMemoryUnitPreferencesStorage : UnitPreferencesStorage {
  private var stored = UnitPreferences()

  override fun loadPreferences(): UnitPreferences = stored

  override fun savePreferences(preferences: UnitPreferences) {
    stored = preferences
  }
}
