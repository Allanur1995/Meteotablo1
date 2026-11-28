package com.example.model

import com.example.domain.UnitConverter
import kotlin.math.roundToInt

enum class SigmetHazardType(
  val code: String,
  val label: String,
  val isSevere: Boolean,
) {
  TS("TS", "Гроза / Конвекция", true),
  TURB("TURB", "Турбулентность", true),
  ICE("ICE", "Обледенение", true),
  VA("VA", "Вулканический пепел", true),
  MTW("MTW", "Горная волна", false),
  OTHER("SIGMET", "Опасное явление", false);

  companion object {
    fun fromRaw(rawHazard: String?): SigmetHazardType {
      val up = rawHazard.orEmpty().trim().uppercase()
      return when {
        up.contains("TS") || up.contains("CONV") || up.contains("TC") -> TS
        up.contains("TURB") -> TURB
        up.contains("ICE") || up.contains("ICING") -> ICE
        up.contains("VA") || up.contains("ASH") -> VA
        up.contains("MTW") -> MTW
        else -> OTHER
      }
    }
  }
}

data class SigmetAlert(
  val id: String,
  val icaoId: String,
  val firName: String,
  val qualifier: String,
  val hazard: SigmetHazardType,
  val rawText: String,
  val validFromUtc: String,
  val validToUtc: String,
  val minAltitudeFt: Int?,
  val maxAltitudeFt: Int?,
  val distanceKm: Int?,
  val isAirmet: Boolean = false,
) {
  fun altitudeRangeLabel(pressureUnit: PressureUnit): String {
    val lo = minAltitudeFt
    val hi = maxAltitudeFt
    if (lo == null && hi == null) return "SFC / без огранич."
    return if (pressureUnit == PressureUnit.HPA) {
      val loM = lo?.let { "${UnitConverter.feetToMeters(it)} м" } ?: "SFC"
      val hiM = hi?.let { "${UnitConverter.feetToMeters(it)} м" } ?: "UNL"
      "$loM – $hiM"
    } else {
      val loFt = lo?.let { if (it >= 10000) "FL${(it / 100.0).roundToInt()}" else "$it ft" } ?: "SFC"
      val hiFt = hi?.let { if (it >= 10000) "FL${(it / 100.0).roundToInt()}" else "$it ft" } ?: "UNL"
      "$loFt – $hiFt"
    }
  }
}
