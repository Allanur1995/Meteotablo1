package com.example.domain

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

object SolarCalculator {

  data class SunTimes(
    val sunriseLocal: String,
    val sunsetLocal: String,
  )

  fun calculateSunriseSunset(
    latitude: Double,
    longitude: Double,
    utcOffsetHours: Int,
    calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")),
  ): SunTimes {
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val dayOfYear = dayOfYear(year, month, day)
    val sunriseMinutes = calcEventMinutes(dayOfYear, latitude, longitude, utcOffsetHours, isSunrise = true)
    val sunsetMinutes = calcEventMinutes(dayOfYear, latitude, longitude, utcOffsetHours, isSunrise = false)

    return SunTimes(
      sunriseLocal = formatMinutes(sunriseMinutes),
      sunsetLocal = formatMinutes(sunsetMinutes),
    )
  }

  private fun dayOfYear(year: Int, month: Int, day: Int): Int {
    val n1 = floor(275.0 * month / 9.0).toInt()
    val n2 = floor((month + 9.0) / 12.0).toInt()
    val n3 = 1 + floor((year - 4.0 * floor(year / 4.0) + 2.0) / 3.0).toInt()
    return n1 - (n2 * n3) + day - 30
  }

  private fun calcEventMinutes(
    n: Int,
    lat: Double,
    lon: Double,
    utcOffsetHours: Int,
    isSunrise: Boolean,
  ): Int? {
    val zenith = 90.833
    val lngHour = lon / 15.0
    val t = if (isSunrise) n + ((6.0 - lngHour) / 24.0) else n + ((18.0 - lngHour) / 24.0)

    val m = (0.9856 * t) - 3.289
    var l = m + (1.916 * sin(deg2rad(m))) + (0.020 * sin(2 * deg2rad(m))) + 282.634
    l = normalizeDeg(l)

    var ra = rad2deg(kotlin.math.atan(0.91764 * tan(deg2rad(l))))
    ra = normalizeDeg(ra)
    val lQuadrant = floor(l / 90.0) * 90.0
    val raQuadrant = floor(ra / 90.0) * 90.0
    ra = (ra + (lQuadrant - raQuadrant)) / 15.0

    val sinDec = 0.39782 * sin(deg2rad(l))
    val cosDec = cos(asin(sinDec))

    val cosH = (cos(deg2rad(zenith)) - (sinDec * sin(deg2rad(lat)))) / (cosDec * cos(deg2rad(lat)))
    if (cosH > 1.0 || cosH < -1.0) return null

    var h = if (isSunrise) 360.0 - rad2deg(acos(cosH)) else rad2deg(acos(cosH))
    h /= 15.0

    val tLocal = h + ra - (0.06571 * t) - 6.622
    var ut = tLocal - lngHour
    ut = ((ut % 24.0) + 24.0) % 24.0
    val localHours = ((ut + utcOffsetHours) % 24.0 + 24.0) % 24.0
    return (localHours * 60.0).toInt()
  }

  private fun formatMinutes(totalMinutes: Int?): String {
    if (totalMinutes == null) return "--:--"
    val clamped = ((totalMinutes % 1440) + 1440) % 1440
    val hh = clamped / 60
    val mm = clamped % 60
    return "%02d:%02d".format(hh, mm)
  }

  private fun normalizeDeg(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
  private fun deg2rad(deg: Double): Double = deg * PI / 180.0
  private fun rad2deg(rad: Double): Double = rad * 180.0 / PI
}
