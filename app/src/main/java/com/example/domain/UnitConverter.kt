package com.example.domain

import com.example.model.PressureUnit
import com.example.model.UnitSystem
import com.example.model.WindSpeedUnit
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

object UnitConverter {

  private const val KT_TO_MPS = 0.514444
  private const val KT_TO_KPH = 1.852
  private const val HPA_TO_INHG = 0.029530
  private const val HPA_TO_MMHG = 0.750062
  private const val ISA_LAPSE_FACTOR = 2.25577e-5
  private const val ISA_EXPONENT = 5.25588
  private const val METERS_TO_FEET = 3.28084

  fun resolveUnitSystem(windUnit: WindSpeedUnit, pressureUnit: PressureUnit): UnitSystem? {
    return when {
      windUnit == WindSpeedUnit.MPS && pressureUnit == PressureUnit.HPA -> UnitSystem.METRIC
      windUnit == WindSpeedUnit.KT && pressureUnit == PressureUnit.INHG -> UnitSystem.IMPERIAL
      else -> null
    }
  }

  fun cycleWindUnit(current: WindSpeedUnit): WindSpeedUnit {
    return when (current) {
      WindSpeedUnit.KT -> WindSpeedUnit.MPS
      WindSpeedUnit.MPS -> WindSpeedUnit.KPH
      WindSpeedUnit.KPH -> WindSpeedUnit.KT
    }
  }

  fun cyclePressureUnit(current: PressureUnit): PressureUnit {
    return when (current) {
      PressureUnit.HPA -> PressureUnit.INHG
      PressureUnit.INHG -> PressureUnit.HPA
    }
  }

  fun convertWindFromKnots(speedKt: Double, targetUnit: WindSpeedUnit): Double {
    return when (targetUnit) {
      WindSpeedUnit.KT -> speedKt
      WindSpeedUnit.MPS -> speedKt * KT_TO_MPS
      WindSpeedUnit.KPH -> speedKt * KT_TO_KPH
    }
  }

  fun convertWindFromKnots(speedKt: Int, targetUnit: WindSpeedUnit): Double {
    return convertWindFromKnots(speedKt.toDouble(), targetUnit)
  }

  fun convertMpsToKnots(mps: Double): Double = mps / KT_TO_MPS

  fun convertKnotsToMps(kt: Double): Double = kt * KT_TO_MPS

  fun convertKphToKnots(kph: Double): Double = kph / KT_TO_KPH

  fun convertWind(value: Double, from: WindSpeedUnit, to: WindSpeedUnit): Double {
    if (from == to) return value
    val kt = when (from) {
      WindSpeedUnit.KT -> value
      WindSpeedUnit.MPS -> convertMpsToKnots(value)
      WindSpeedUnit.KPH -> convertKphToKnots(value)
    }
    return convertWindFromKnots(kt, to)
  }

  fun formatWindSpeedValue(speedKt: Int, targetUnit: WindSpeedUnit): Int {
    return convertWindFromKnots(speedKt, targetUnit).roundToInt()
  }

  fun convertHpaToInHg(hpa: Int): Double = hpa * HPA_TO_INHG

  fun convertHpaToInHg(hpa: Double): Double = hpa * HPA_TO_INHG

  fun convertInHgToHpa(inHg: Double): Double = inHg / HPA_TO_INHG

  fun convertPressure(value: Double, from: PressureUnit, to: PressureUnit): Double {
    if (from == to) return value
    return when (from) {
      PressureUnit.HPA -> convertHpaToInHg(value)
      PressureUnit.INHG -> convertInHgToHpa(value)
    }
  }

  fun convertHpaToMmHg(hpa: Int): Int = (hpa * HPA_TO_MMHG).roundToInt()

  fun convertHpaToMmHgDouble(hpa: Double): Double = hpa * HPA_TO_MMHG

  data class QnhQfeConversionResult(
    val qnhHpa: Double,
    val qfeHpa: Double,
    val qnhInHg: Double,
    val qfeInHg: Double,
    val qnhMmHg: Int,
    val qfeMmHg: Int,
    val pressureAltitudeFeet: Int,
    val elevationMeters: Int,
  ) {
    val baricOffsetHpa: Double get() = qnhHpa - qfeHpa
  }

  fun computeQfeFromQnhHpa(qnhHpa: Double, elevationMeters: Int): Double {
    val ratio = (1.0 - ISA_LAPSE_FACTOR * elevationMeters).coerceIn(0.2, 1.5)
    return qnhHpa * ratio.pow(ISA_EXPONENT)
  }

  fun computeQnhFromQfeHpa(qfeHpa: Double, elevationMeters: Int): Double {
    val ratio = (1.0 - ISA_LAPSE_FACTOR * elevationMeters).coerceIn(0.2, 1.5)
    return qfeHpa / ratio.pow(ISA_EXPONENT)
  }

  fun computePressureAltitudeFeet(qnhHpa: Double, elevationMeters: Int): Int {
    val elevationFt = elevationMeters * METERS_TO_FEET
    val qnhRatio = (qnhHpa / 1013.25).coerceIn(0.5, 1.3)
    val deltaFt = 145366.45 * (1.0 - qnhRatio.pow(0.190284))
    return (elevationFt + deltaFt).roundToInt()
  }

  fun convertQnhQfe(
    pressureValue: Double,
    unit: PressureUnit,
    elevationMeters: Int,
    fromQnhToQfe: Boolean = true,
  ): QnhQfeConversionResult {
    val safeElev = elevationMeters.coerceIn(-500, 6000)
    val inputHpa = if (unit == PressureUnit.HPA) pressureValue else convertInHgToHpa(pressureValue)
    val qnhHpa = if (fromQnhToQfe) inputHpa else computeQnhFromQfeHpa(inputHpa, safeElev)
    val qfeHpa = if (fromQnhToQfe) computeQfeFromQnhHpa(inputHpa, safeElev) else inputHpa
    return QnhQfeConversionResult(
      qnhHpa = qnhHpa,
      qfeHpa = qfeHpa,
      qnhInHg = convertHpaToInHg(qnhHpa),
      qfeInHg = convertHpaToInHg(qfeHpa),
      qnhMmHg = convertHpaToMmHgDouble(qnhHpa).roundToInt(),
      qfeMmHg = convertHpaToMmHgDouble(qfeHpa).roundToInt(),
      pressureAltitudeFeet = computePressureAltitudeFeet(qnhHpa, safeElev),
      elevationMeters = safeElev,
    )
  }

  fun formatPressurePrimary(
    hpa: Int?,
    inHg: Double?,
    unit: PressureUnit,
    locale: Locale = Locale("ru"),
  ): Pair<String, String> {
    return when (unit) {
      PressureUnit.HPA -> (hpa?.toString() ?: "----") to unit.label
      PressureUnit.INHG -> {
        val resolvedInHg = inHg ?: hpa?.let { convertHpaToInHg(it) }
        val text = resolvedInHg?.let { String.format(locale, "%.2f", it) } ?: "--,--"
        text to unit.label
      }
    }
  }

  fun formatPressureSecondary(
    hpa: Int?,
    mmHg: Int?,
    inHg: Double?,
    activeUnit: PressureUnit,
    locale: Locale = Locale("ru"),
  ): String {
    val mmText = (mmHg ?: hpa?.let { convertHpaToMmHg(it) })?.toString() ?: "---"
    return when (activeUnit) {
      PressureUnit.HPA -> {
        val inText = (inHg ?: hpa?.let { convertHpaToInHg(it) })?.let { String.format(locale, "%.2f", it) } ?: "--,--"
        "$mmText мм.  ·  $inText дм."
      }
      PressureUnit.INHG -> {
        val hpaText = hpa?.toString() ?: "----"
        "$hpaText гПа  ·  $mmText мм."
      }
    }
  }
}
