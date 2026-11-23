package com.example

import com.example.data.AirportCatalog
import com.example.domain.AviationWeatherParser
import com.example.domain.TafParser
import com.example.model.TafChangeType
import com.example.model.WindSpeedUnit
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
    assertEquals(23000, decoded.ceilingFeet)
    assertEquals(23000, decoded.clouds.first().baseFeet)
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

  @Test
  fun `taf parser decodes base tempo becmg and fm periods with cloud bases in feet`() {
    val rawTaf = "TAF UTAA 231100Z 2312/2412 09006MPS 9999 SCT040 " +
      "TEMPO 2318/2402 2500 +TSRA BKN008CB " +
      "BECMG 2404/2406 08005MPS CAVOK " +
      "FM240800 12010KT 6000 FEW030"

    val decoded = TafParser.parse(rawTaf, windUnit = WindSpeedUnit.KT)
    assertEquals("UTAA", decoded.stationIcao)
    assertEquals("11:00Z (23)", decoded.issuedUtc)
    assertEquals("23 12:00Z – 24 12:00Z", decoded.validityWindow)
    assertFalse(decoded.isCancelled)
    assertFalse(decoded.isNil)
    assertEquals(4, decoded.periods.size)

    val base = decoded.periods[0]
    assertEquals(TafChangeType.BASE, base.type)
    assertEquals("SCT 4000 ft", base.cloudsSummary)
    assertEquals("090° 12 kt", base.windSummary)

    val tempo = decoded.periods[1]
    assertEquals(TafChangeType.TEMPO, tempo.type)
    assertTrue(tempo.isHazardous)
    assertEquals("2500 м", tempo.visibilitySummary)
    assertEquals("BKN 800 ft CB", tempo.cloudsSummary)
    assertTrue(tempo.phenomenaSummary.contains("+TSRA"))

    val becmg = decoded.periods[2]
    assertEquals(TafChangeType.BECMG, becmg.type)
    assertTrue(becmg.visibilitySummary.contains("CAVOK"))

    val fm = decoded.periods[3]
    assertEquals(TafChangeType.FM, fm.type)
    assertEquals("с 24 08:00Z", fm.validityLabel)
    assertEquals("FEW 3000 ft", fm.cloudsSummary)

    val cancelled = TafParser.parse("TAF UTAA 231100Z 2312/2412 CNL")
    assertTrue(cancelled.isCancelled)
    assertTrue(cancelled.periods.isEmpty())
  }
}
