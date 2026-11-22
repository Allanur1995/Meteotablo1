package com.example.model

import com.example.data.DataSourceMode

data class SavedWeatherPreset(
  val id: String,
  val name: String,
  val icao: String,
  val runwayPairIndex: Int,
  val runwayLabel: String,
  val windUnit: WindSpeedUnit = WindSpeedUnit.KT,
  val pressureUnit: PressureUnit = PressureUnit.HPA,
  val sourceMode: DataSourceMode? = null,
) {
  companion object {
    fun buildId(
      icao: String,
      runwayPairIndex: Int,
      windUnit: WindSpeedUnit,
      pressureUnit: PressureUnit,
    ): String {
      return "${icao.trim().uppercase()}_${runwayPairIndex}_${windUnit.name}_${pressureUnit.name}"
    }

    fun defaultLabel(
      icao: String,
      runwayLabel: String,
      windUnit: WindSpeedUnit,
      pressureUnit: PressureUnit,
    ): String {
      return "${icao.trim().uppercase()} · ВПП $runwayLabel (${windUnit.label}, ${pressureUnit.label})"
    }
  }
}
