package com.example.domain

import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import java.util.Locale
import kotlin.math.roundToInt

object UnitConverter {

  private const val KT_TO_MPS = 0.514444
  private const val KT_TO_KPH = 1.852
  private const val HPA_TO_INHG = 0.029530
  private const val HPA_TO_MMHG = 0.750062

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

  fun formatWindSpeedValue(speedKt: Int, targetUnit: WindSpeedUnit): Int {
    return convertWindFromKnots(speedKt, targetUnit).roundToInt()
  }

  fun convertHpaToInHg(hpa: Int): Double = hpa * HPA_TO_INHG

  fun convertHpaToMmHg(hpa: Int): Int = (hpa * HPA_TO_MMHG).roundToInt()

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
