package com.example.data

import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit

data class UnitPreferences(
  val windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  val pressureUnit: PressureUnit = PressureUnit.HPA,
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
