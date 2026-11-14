package com.example.data

import com.example.model.AirportStation
import com.example.model.RunwayPair

object AirportCatalog {

  val presets: List<AirportStation> = listOf(
    AirportStation(
      icao = "UTAM",
      name = "Mary International",
      city = "Мары",
      latitude = 37.6197,
      longitude = 61.8961,
      elevationMeters = 193,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("35L", 350, "17R", 170),
        RunwayPair("35R", 350, "17L", 170),
      ),
    ),
    AirportStation(
      icao = "UTAA",
      name = "Ashgabat International",
      city = "Ашхабад",
      latitude = 37.9868,
      longitude = 58.3610,
      elevationMeters = 211,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("12L", 118, "30R", 298),
        RunwayPair("12R", 118, "30L", 298),
      ),
    ),
    AirportStation(
      icao = "UTAK",
      name = "Turkmenbashi International",
      city = "Туркменбаши",
      latitude = 40.0333,
      longitude = 53.0072,
      elevationMeters = 86,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("16L", 161, "34R", 341),
        RunwayPair("16R", 161, "34L", 341),
      ),
    ),
    AirportStation(
      icao = "UTAT",
      name = "Dashoguz Airport",
      city = "Дашогуз",
      latitude = 41.7647,
      longitude = 59.8331,
      elevationMeters = 83,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("04", 41, "22", 221),
      ),
    ),
    AirportStation(
      icao = "UTAV",
      name = "Turkmenabat International",
      city = "Туркменабад",
      latitude = 39.0833,
      longitude = 63.6133,
      elevationMeters = 191,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("13", 131, "31", 311),
      ),
    ),
    AirportStation(
      icao = "UTAN",
      name = "Balkanabat International",
      city = "Балканабат",
      latitude = 39.6783,
      longitude = 54.2078,
      elevationMeters = -8,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("16", 160, "34", 340),
      ),
    ),
    AirportStation(
      icao = "UTAE",
      name = "Kerki International",
      city = "Керки",
      latitude = 37.8222,
      longitude = 65.1353,
      elevationMeters = 236,
      utcOffsetHours = 5,
      timezoneLabel = "Ашхабад",
      runways = listOf(
        RunwayPair("16", 160, "34", 340),
      ),
    ),
    AirportStation(
      icao = "OMDB",
      name = "Dubai International",
      city = "Дубай",
      latitude = 25.2532,
      longitude = 55.3657,
      elevationMeters = 19,
      utcOffsetHours = 4,
      timezoneLabel = "Дубай",
      runways = listOf(
        RunwayPair("12L", 120, "30R", 300),
        RunwayPair("12R", 120, "30L", 300),
      ),
    ),
    AirportStation(
      icao = "UUDD",
      name = "Domodedovo",
      city = "Москва",
      latitude = 55.4088,
      longitude = 37.9063,
      elevationMeters = 179,
      utcOffsetHours = 3,
      timezoneLabel = "Москва",
      runways = listOf(
        RunwayPair("14L", 138, "32R", 318),
        RunwayPair("14R", 138, "32L", 318),
      ),
    ),
    AirportStation(
      icao = "LTFM",
      name = "Istanbul Airport",
      city = "Стамбул",
      latitude = 41.2753,
      longitude = 28.7519,
      elevationMeters = 99,
      utcOffsetHours = 3,
      timezoneLabel = "Стамбул",
      runways = listOf(
        RunwayPair("35L", 353, "17R", 173),
        RunwayPair("34L", 343, "16R", 163),
      ),
    ),
  )

  fun findOrDefault(icao: String): AirportStation {
    val clean = icao.trim().uppercase()
    return presets.firstOrNull { it.icao == clean }
      ?: AirportStation(
        icao = clean,
        name = "$clean Station",
        city = clean,
        latitude = 37.9868,
        longitude = 58.3610,
        elevationMeters = 100,
        utcOffsetHours = 0,
        timezoneLabel = "UTC",
        runways = listOf(RunwayPair("36", 360, "18", 180)),
      )
  }
}
