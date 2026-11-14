package com.example

import com.example.domain.UnitConverter
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitConverterTest {

  @Test
  fun `cycleWindUnit cycles through kt mps and kph`() {
    assertEquals(WindSpeedUnit.MPS, UnitConverter.cycleWindUnit(WindSpeedUnit.KT))
    assertEquals(WindSpeedUnit.KPH, UnitConverter.cycleWindUnit(WindSpeedUnit.MPS))
    assertEquals(WindSpeedUnit.KT, UnitConverter.cycleWindUnit(WindSpeedUnit.KPH))
  }

  @Test
  fun `cyclePressureUnit toggles between hPa and inHg`() {
    assertEquals(PressureUnit.INHG, UnitConverter.cyclePressureUnit(PressureUnit.HPA))
    assertEquals(PressureUnit.HPA, UnitConverter.cyclePressureUnit(PressureUnit.INHG))
  }

  @Test
  fun `wind speed conversions from knots to mps and kph are accurate`() {
    assertEquals(10, UnitConverter.formatWindSpeedValue(10, WindSpeedUnit.KT))
    assertEquals(5, UnitConverter.formatWindSpeedValue(10, WindSpeedUnit.MPS))
    assertEquals(19, UnitConverter.formatWindSpeedValue(10, WindSpeedUnit.KPH))
    assertEquals(7, UnitConverter.formatWindSpeedValue(4, WindSpeedUnit.KPH))
  }

  @Test
  fun `pressure primary formatting switches cleanly between hPa and inHg`() {
    val (hpaVal, hpaUnit) = UnitConverter.formatPressurePrimary(
      hpa = 1023,
      inHg = null,
      unit = PressureUnit.HPA,
      locale = Locale.US,
    )
    assertEquals("1023", hpaVal)
    assertEquals("гПа", hpaUnit)

    val (inHgVal, inHgUnit) = UnitConverter.formatPressurePrimary(
      hpa = 1023,
      inHg = null,
      unit = PressureUnit.INHG,
      locale = Locale.US,
    )
    assertEquals("30.21", inHgVal)
    assertEquals("дм", inHgUnit)
  }
}
