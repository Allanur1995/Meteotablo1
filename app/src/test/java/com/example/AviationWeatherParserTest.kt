package com.example

import com.example.data.AirportCatalog
import com.example.domain.AviationWeatherParser
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AviationWeatherParserTest {

  @Test
  fun `utam demo metar decodes to exact instrument values`() {
    val utam = AirportCatalog.findOrDefault("UTAM")
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
      set(2026, Calendar.NOVEMBER, 14, 17, 11, 0)
    }
    val metar = "METAR UTAM 141700Z 08004KT 6000 BKN230 11/07 Q1023 R88/CLRD70 NOSIG"
    val taf = "TAF UTAM 141630Z 1418/1518 09012KT 5000 SCT020 BKN100 NOSIG"

    val decoded = AviationWeatherParser.parse(
      rawMetarInput = metar,
      rawTafInput = taf,
      station = utam,
      sourceLabel = "test",
      receivedTimeLocal = "22:11",
      calendarForSun = cal,
    )

    assertEquals("17:00Z", decoded.observationTimeUtc)
    assertEquals("22:00", decoded.localObsTime)
    assertEquals(80, decoded.wind.directionDeg)
    assertEquals(2, decoded.wind.speedMps.roundToInt())
    assertEquals(6000, decoded.visibilityMeters)
    assertEquals(7010, decoded.ceilingMeters)
    assertEquals("BKN230", decoded.ceilingCode)
    assertEquals(11, decoded.temperatureC)
    assertEquals(7, decoded.dewPointC)
    assertEquals(1023, decoded.qnhHpa)
    assertEquals(767, decoded.qnhMmHg)
    assertEquals(1000, decoded.qfeHpa)
    assertEquals(750, decoded.qfeMmHg)
    assertNotNull(decoded.frictionCoeff)
    assertEquals(0.70, decoded.frictionCoeff!!, 0.001)

    val rwy35L = AviationWeatherParser.calculateRunwayComponents(
      designator = "35L",
      runwayHeadingDeg = 350,
      wind = decoded.wind,
      frictionCoeff = decoded.frictionCoeff,
      rvrMeters = null,
    )
    assertEquals(2.1, rwy35L.crosswindMps, 0.1)
    assertEquals(0.0, abs(rwy35L.headwindMps), 0.1)
  }
}
