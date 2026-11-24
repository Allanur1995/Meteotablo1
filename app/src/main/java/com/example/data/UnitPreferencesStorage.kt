package com.example.data

import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit

enum class AutoRefreshInterval(
  val minutes: Int,
  val label: String,
) {
  FIVE_MIN(5, "5 мин"),
  FIFTEEN_MIN(15, "15 мин"),
  THIRTY_MIN(30, "30 мин");

  companion object {
    fun fromMinutes(minutes: Int): AutoRefreshInterval =
      entries.firstOrNull { it.minutes == minutes } ?: FIFTEEN_MIN
  }
}

data class UnitPreferences(
  val windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  val pressureUnit: PressureUnit = PressureUnit.HPA,
  val autoRefreshEnabled: Boolean = true,
  val autoRefreshIntervalMinutes: Int = AutoRefreshInterval.FIFTEEN_MIN.minutes,
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
