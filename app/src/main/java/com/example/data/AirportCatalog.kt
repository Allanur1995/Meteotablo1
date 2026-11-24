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

  private val extendedDatabase: List<AirportStation> = listOf(
    // Central Asia & Caucasus
    AirportStation("UTTT", "Tashkent International", "Ташкент", 41.2579, 69.2812, 432, 5, "Ташкент", listOf(RunwayPair("08L", 80, "26R", 260))),
    AirportStation("UTSS", "Samarkand International", "Самарканд", 39.7005, 66.9838, 678, 5, "Ташкент", listOf(RunwayPair("09", 91, "27", 271))),
    AirportStation("UTSB", "Bukhara International", "Бухара", 39.7750, 64.4833, 229, 5, "Ташкент", listOf(RunwayPair("01", 10, "19", 190))),
    AirportStation("UTNU", "Urgench International", "Ургенч", 41.5843, 60.6417, 98, 5, "Ташкент", listOf(RunwayPair("13", 132, "31", 312))),
    AirportStation("UAAA", "Almaty International", "Алматы", 43.3521, 77.0405, 681, 5, "Алматы", listOf(RunwayPair("05R", 50, "23L", 230))),
    AirportStation("UACC", "Nursultan Nazarbayev", "Астана", 51.0222, 71.4669, 355, 5, "Астана", listOf(RunwayPair("04", 41, "22", 221))),
    AirportStation("UCFM", "Manas International", "Бишкек", 43.0613, 74.4776, 637, 6, "Бишкек", listOf(RunwayPair("08", 80, "26", 260))),
    AirportStation("UTDD", "Dushanbe International", "Душанбе", 38.5433, 68.8250, 785, 5, "Душанбе", listOf(RunwayPair("09", 91, "27", 271))),
    AirportStation("UBBB", "Heydar Aliyev International", "Баку", 40.4675, 50.0467, 3, 4, "Баку", listOf(RunwayPair("16", 160, "34", 340))),
    AirportStation("UGTB", "Tbilisi International", "Тбилиси", 41.6692, 44.9547, 495, 4, "Тбилиси", listOf(RunwayPair("13R", 131, "31L", 311))),
    AirportStation("UDYZ", "Zvartnots International", "Ереван", 40.1473, 44.3959, 865, 4, "Ереван", listOf(RunwayPair("09", 90, "27", 270))),
    // Russia & CIS
    AirportStation("UUEE", "Sheremetyevo", "Москва (ШРМ)", 55.9726, 37.4146, 192, 3, "Москва", listOf(RunwayPair("06L", 64, "24R", 244))),
    AirportStation("UUWW", "Vnukovo", "Москва (ВНК)", 55.5915, 37.2615, 209, 3, "Москва", listOf(RunwayPair("01", 13, "19", 193))),
    AirportStation("ULLI", "Pulkovo", "Санкт-Петербург", 59.8003, 30.2625, 24, 3, "Москва", listOf(RunwayPair("10L", 100, "28R", 280))),
    AirportStation("URSS", "Sochi International", "Сочи", 43.4499, 39.9566, 27, 3, "Москва", listOf(RunwayPair("06", 61, "24", 241))),
    AirportStation("UWKD", "Kazan International", "Казань", 55.6062, 49.2787, 126, 3, "Москва", listOf(RunwayPair("11", 112, "29", 292))),
    AirportStation("USSS", "Koltsovo", "Екатеринбург", 56.7431, 60.8027, 233, 5, "Екатеринбург", listOf(RunwayPair("08L", 82, "26R", 262))),
    AirportStation("UNNT", "Tolmachevo", "Новосибирск", 55.0126, 82.6507, 111, 7, "Новосибирск", listOf(RunwayPair("07", 72, "25", 252))),
    AirportStation("UMMS", "Minsk National", "Минск", 53.8825, 28.0307, 204, 3, "Минск", listOf(RunwayPair("13R", 133, "31L", 313))),
    // Middle East
    AirportStation("OMAA", "Zayed International", "Абу-Даби", 24.4330, 54.6511, 27, 4, "ОАЭ", listOf(RunwayPair("13L", 132, "31R", 312))),
    AirportStation("OMSJ", "Sharjah International", "Шарджа", 25.3286, 55.5172, 34, 4, "ОАЭ", listOf(RunwayPair("12", 121, "30", 301))),
    AirportStation("OTHH", "Hamad International", "Доха", 25.2731, 51.6081, 4, 3, "Доха", listOf(RunwayPair("16L", 160, "34R", 340))),
    AirportStation("OERK", "King Khalid International", "Эр-Рияд", 24.9576, 46.6988, 625, 3, "Рияд", listOf(RunwayPair("15L", 150, "33R", 330))),
    AirportStation("OEJN", "King Abdulaziz International", "Джидда", 21.6796, 39.1565, 15, 3, "Джидда", listOf(RunwayPair("16L", 160, "34R", 340))),
    AirportStation("OKKK", "Kuwait International", "Кувейт", 29.2266, 47.9689, 63, 3, "Кувейт", listOf(RunwayPair("15L", 150, "33R", 330))),
    AirportStation("OOMS", "Muscat International", "Маскат", 23.5933, 58.2844, 15, 4, "Маскат", listOf(RunwayPair("08L", 80, "26R", 260))),
    AirportStation("OIIE", "Imam Khomeini International", "Тегеран", 35.4161, 51.1522, 1007, 4, "Тегеран", listOf(RunwayPair("11L", 112, "29R", 292))),
    // Turkey & Europe
    AirportStation("LTFJ", "Sabiha Gokcen", "Стамбул (SAW)", 40.8986, 29.3092, 95, 3, "Стамбул", listOf(RunwayPair("06", 60, "24", 240))),
    AirportStation("LTAC", "Esenboga International", "Анкара", 40.1281, 32.9951, 953, 3, "Анкара", listOf(RunwayPair("03L", 33, "21R", 213))),
    AirportStation("LTAI", "Antalya Airport", "Анталья", 36.8987, 30.8005, 54, 3, "Анталья", listOf(RunwayPair("18C", 183, "36C", 3))),
    AirportStation("EGLL", "London Heathrow", "Лондон", 51.4700, -0.4543, 25, 0, "UTC", listOf(RunwayPair("09L", 90, "27R", 270))),
    AirportStation("EGKK", "London Gatwick", "Лондон (LGW)", 51.1537, -0.1821, 62, 0, "UTC", listOf(RunwayPair("08R", 80, "26L", 260))),
    AirportStation("LFPG", "Paris Charles de Gaulle", "Париж", 49.0097, 2.5479, 119, 1, "Париж", listOf(RunwayPair("08L", 85, "26R", 265))),
    AirportStation("EDDF", "Frankfurt Main", "Франкфурт", 50.0379, 8.5622, 111, 1, "Берлин", listOf(RunwayPair("07C", 70, "25C", 250))),
    AirportStation("EDDM", "Munich Airport", "Мюнхен", 48.3537, 11.7750, 453, 1, "Берлин", listOf(RunwayPair("08L", 81, "26R", 261))),
    AirportStation("EHAM", "Amsterdam Schiphol", "Амстердам", 52.3105, 4.7683, -3, 1, "Амстердам", listOf(RunwayPair("09", 90, "27", 270))),
    AirportStation("LEMD", "Madrid Barajas", "Мадрид", 40.4983, -3.5676, 610, 1, "Мадрид", listOf(RunwayPair("14L", 142, "32R", 322))),
    AirportStation("LIRF", "Rome Fiumicino", "Рим", 41.8003, 12.2389, 5, 1, "Рим", listOf(RunwayPair("16L", 161, "34R", 341))),
    AirportStation("LSZH", "Zurich Airport", "Цюрих", 47.4582, 8.5555, 432, 1, "Цюрих", listOf(RunwayPair("14", 136, "32", 316))),
    AirportStation("LOWW", "Vienna International", "Вена", 48.1103, 16.5697, 183, 1, "Вена", listOf(RunwayPair("11", 113, "29", 293))),
    AirportStation("EPWA", "Warsaw Chopin", "Варшава", 52.1657, 20.9671, 110, 1, "Варшава", listOf(RunwayPair("11", 113, "29", 293))),
    AirportStation("LGAV", "Athens International", "Афины", 37.9364, 23.9445, 94, 2, "Афины", listOf(RunwayPair("03L", 34, "21R", 214))),
    // Asia & Pacific
    AirportStation("VIDP", "Indira Gandhi International", "Дели", 28.5562, 77.1000, 237, 5, "Дели", listOf(RunwayPair("11R", 111, "29L", 291))),
    AirportStation("VABB", "Chhatrapati Shivaji", "Мумбаи", 19.0896, 72.8656, 11, 5, "Дели", listOf(RunwayPair("09", 90, "27", 270))),
    AirportStation("VTBS", "Suvarnabhumi", "Бангкок", 13.6900, 100.7501, 2, 7, "Бангкок", listOf(RunwayPair("01L", 15, "19R", 195))),
    AirportStation("WSSS", "Singapore Changi", "Сингапур", 1.3644, 103.9915, 7, 8, "Сингапур", listOf(RunwayPair("02L", 23, "20R", 203))),
    AirportStation("VHHH", "Hong Kong International", "Гонконг", 22.3080, 113.9185, 9, 8, "Гонконг", listOf(RunwayPair("07L", 74, "25R", 254))),
    AirportStation("ZBAA", "Beijing Capital", "Пекин", 40.0799, 116.6031, 35, 8, "Пекин", listOf(RunwayPair("01", 10, "19", 190))),
    AirportStation("ZSPD", "Shanghai Pudong", "Шанхай", 31.1443, 121.8083, 4, 8, "Шанхай", listOf(RunwayPair("16L", 168, "34R", 348))),
    AirportStation("RKSI", "Incheon International", "Сеул", 37.4602, 126.4407, 7, 9, "Сеул", listOf(RunwayPair("15L", 152, "33R", 332))),
    AirportStation("RJTT", "Tokyo Haneda", "Токио", 35.5494, 139.7798, 11, 9, "Токио", listOf(RunwayPair("16L", 157, "34R", 337))),
    AirportStation("YSSY", "Sydney Kingsford Smith", "Сидней", -33.9399, 151.1753, 6, 10, "Сидней", listOf(RunwayPair("16R", 156, "34L", 336))),
    // North & South America
    AirportStation("KJFK", "John F. Kennedy International", "Нью-Йорк", 40.6413, -73.7781, 4, -5, "EST", listOf(RunwayPair("04L", 44, "22R", 224))),
    AirportStation("KLAX", "Los Angeles International", "Лос-Анджелес", 33.9416, -118.4085, 38, -8, "PST", listOf(RunwayPair("06L", 71, "24R", 251))),
    AirportStation("KORD", "Chicago O'Hare", "Чикаго", 41.9742, -87.9073, 207, -6, "CST", listOf(RunwayPair("09L", 90, "27R", 270))),
    AirportStation("KATL", "Hartsfield-Jackson Atlanta", "Атланта", 33.6407, -84.4277, 313, -5, "EST", listOf(RunwayPair("08L", 90, "26R", 270))),
    AirportStation("KDFW", "Dallas/Fort Worth", "Даллас", 32.8998, -97.0403, 185, -6, "CST", listOf(RunwayPair("17L", 175, "35R", 355))),
    AirportStation("CYYZ", "Toronto Pearson", "Торонто", 43.6777, -79.6248, 173, -5, "EST", listOf(RunwayPair("05", 47, "23", 227))),
  )

  val allStations: List<AirportStation> = (presets + extendedDatabase).distinctBy { it.icao }

  fun findKnown(icao: String): AirportStation? {
    val clean = icao.trim().uppercase()
    return allStations.firstOrNull { it.icao == clean }
  }

  fun findOrDefault(icao: String): AirportStation {
    val clean = icao.trim().uppercase()
    return findKnown(clean)
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

  fun search(
    query: String,
    extraIcaos: List<String> = emptyList(),
    limit: Int = 8,
  ): List<AirportStation> {
    val trimmed = query.trim()
    val extraStations = extraIcaos
      .map { it.trim().uppercase() }
      .filter { code -> code.length == 4 && allStations.none { it.icao == code } }
      .distinct()
      .map { findOrDefault(it) }
    val allKnown = allStations + extraStations

    if (trimmed.isEmpty()) {
      return allKnown.take(limit)
    }
    val upper = trimmed.uppercase()
    val exactAndPrefix = allKnown.filter {
      it.icao.startsWith(upper)
    }
    val substringMatches = allKnown.filter { station ->
      station !in exactAndPrefix && (
        station.icao.contains(upper) ||
          station.city.contains(trimmed, ignoreCase = true) ||
          station.name.contains(trimmed, ignoreCase = true)
        )
    }
    return (exactAndPrefix + substringMatches).take(limit)
  }
}
