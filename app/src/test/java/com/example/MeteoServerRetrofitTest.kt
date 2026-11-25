package com.example

import com.example.data.CustomMeteoServerClient
import com.example.data.ServerConfig
import com.example.network.BasicAuthInterceptor
import com.example.network.MeteoLoginRequest
import com.example.network.MeteoLoginResponse
import com.example.network.MeteoServerRetrofitFactory
import com.example.network.ServerStationWeatherDto
import com.example.network.ServerWeatherEnvelopeDto
import com.example.network.SessionCookieJar
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MeteoServerRetrofitTest {

  @Test
  fun `retrofit factory builds api with default server url and credentials`() {
    val api = MeteoServerRetrofitFactory.create(ServerConfig())
    assertNotNull(api)
  }

  @Test
  fun `basic auth interceptor injects meteo credentials header into retrofit calls`() = runTest {
    var capturedAuthHeader: String? = null
    val moshi = MeteoServerRetrofitFactory.createMoshi()

    val client = OkHttpClient.Builder()
      .addInterceptor(BasicAuthInterceptor("meteo", "meteo"))
      .addInterceptor { chain ->
        val req = chain.request()
        capturedAuthHeader = req.header("Authorization")
        val json = """
          {
            "station": {
              "icao": "UTAA",
              "rawMetar": "METAR UTAA 141700Z 11007KT 9999 SCT045 13/05 Q1022 NOSIG",
              "rawTaf": "TAF UTAA 141630Z 1418/1518 11010KT 8000 SCT040"
            }
          }
        """.trimIndent()
        Response.Builder()
          .request(req)
          .protocol(Protocol.HTTP_1_1)
          .code(200)
          .message("OK")
          .body(json.toResponseBody("application/json".toMediaType()))
          .build()
      }
      .build()

    val api = Retrofit.Builder()
      .baseUrl("http://217.174.229.206:8181/")
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(com.example.network.MeteoServerApi::class.java)

    val resp = api.getStationWeather("UTAA")
    assertTrue(resp.isSuccessful)
    assertEquals(Credentials.basic("meteo", "meteo"), capturedAuthHeader)

    val dto = resp.body()?.findForStation("UTAA")
    assertNotNull(dto)
    assertEquals("METAR UTAA 141700Z 11007KT 9999 SCT045 13/05 Q1022 NOSIG", dto!!.resolvedMetar())
    assertEquals("TAF UTAA 141630Z 1418/1518 11010KT 8000 SCT040", dto.resolvedTaf())
  }

  @Test
  fun `session cookie jar preserves cookies across requests to server host`() {
    val jar = SessionCookieJar()
    val url = "http://217.174.229.206:8181/login".toHttpUrl()
    val cookie = Cookie.Builder()
      .name("PHPSESSID")
      .value("sess123")
      .domain("217.174.229.206")
      .path("/")
      .build()

    jar.saveFromResponse(url, listOf(cookie))
    val loaded = jar.loadForRequest("http://217.174.229.206:8181/weather".toHttpUrl())
    assertEquals(1, loaded.size)
    assertEquals("PHPSESSID", loaded.first().name)
    assertEquals("sess123", loaded.first().value)
  }

  @Test
  fun `moshi models synthesize metar from structured station telemetry and serialize login dto`() {
    val moshi = MeteoServerRetrofitFactory.createMoshi()

    val loginJson = moshi.adapter(MeteoLoginRequest::class.java)
      .toJson(MeteoLoginRequest("meteo", "meteo"))
    assertTrue(loginJson.contains("\"username\":\"meteo\""))
    assertTrue(loginJson.contains("\"password\":\"meteo\""))

    val loginResp = moshi.adapter(MeteoLoginResponse::class.java)
      .fromJson("""{"success":true,"token":"tok_1"}""")
    assertEquals(true, loginResp?.success)
    assertEquals("tok_1", loginResp?.token)

    val envelopeJson = """
      {
        "stations": [
          {
            "icao": "UTAE",
            "windDir": 140,
            "windSpeed": 7,
            "visibility": 8000,
            "qnh": 1022,
            "temp": 12,
            "dewPoint": 5,
            "obsTime": "141700"
          }
        ]
      }
    """.trimIndent()
    val envelope = moshi.adapter(ServerWeatherEnvelopeDto::class.java).fromJson(envelopeJson)
    val dto: ServerStationWeatherDto? = envelope?.findForStation("UTAE")
    assertNotNull(dto)
    assertEquals("METAR UTAE 141700Z 14007KT 8000 12/05 Q1022 NOSIG", dto!!.resolvedMetar())

    val (extracted, _) = CustomMeteoServerClient.extractMetarAndTaf(envelopeJson, "UTAE", moshi)
    assertEquals("METAR UTAE 141700Z 14007KT 8000 12/05 Q1022 NOSIG", extracted)
  }

  @Test
  fun `aviation weather network manager fetches metar taf bbox nearest and 24h history via retrofit`() = runTest {
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/api/data/metar") { exchange ->
      val query = exchange.requestURI.rawQuery.orEmpty()
      val payload = when {
        query.contains("bbox=") -> """
          [
            {"icaoId":"UTAM","rawOb":"METAR UTAM 251200Z 09012KT CAVOK 34/08 Q1011","lat":37.62,"lon":61.90,"elev":220,"name":"Mary"},
            {"icaoId":"UTAA","rawOb":"METAR UTAA 251200Z 11008KT CAVOK 32/09 Q1012","lat":37.98,"lon":58.36,"elev":211,"name":"Ashgabat"}
          ]
        """.trimIndent()
        query.contains("hours=24") -> """
          [
            {"icaoId":"UTAM","rawOb":"METAR UTAM 251100Z 08010KT CAVOK 33/08 Q1012","obsTime":1764068400,"temp":33,"dewp":8,"wdir":80,"wspd":10,"altim":1012.0},
            {"icaoId":"UTAM","rawOb":"METAR UTAM 251200Z 09012KT CAVOK 34/08 Q1011","obsTime":1764072000,"temp":34,"dewp":8,"wdir":90,"wspd":12,"altim":1011.0}
          ]
        """.trimIndent()
        else -> """
          [
            {"icaoId":"UTAM","rawOb":"METAR UTAM 251200Z 09012KT CAVOK 34/08 Q1011","lat":37.62,"lon":61.90,"elev":220,"name":"Mary"}
          ]
        """.trimIndent()
      }
      val bytes = payload.toByteArray()
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.createContext("/api/data/taf") { exchange ->
      val payload = """[{"icaoId":"UTAM","rawTAF":"TAF UTAM 251100Z 2512/2612 09012KT 9999 FEW040"}]"""
      val bytes = payload.toByteArray()
      exchange.responseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    server.start()

    try {
      val manager = com.example.network.AviationWeatherNetworkManager(
        awcBaseUrl = "http://127.0.0.1:${server.address.port}/",
      )
      val latest = manager.fetchLatestWeather(
        icao = "UTAM",
        useAviationWeatherGov = true,
        useNoaaTgftp = false,
        useVatsimMetar = false,
      )
      assertNotNull(latest)
      assertEquals("METAR UTAM 251200Z 09012KT CAVOK 34/08 Q1011", latest!!.rawMetar)
      assertEquals("TAF UTAM 251100Z 2512/2612 09012KT 9999 FEW040", latest.rawTaf)

      val nearest = manager.fetchNearestByCoordinates(37.63, 61.89)
      assertNotNull(nearest)
      assertEquals("UTAM", nearest!!.icao)
      assertTrue(nearest.distanceKm < 5.0)
      assertEquals("TAF UTAM 251100Z 2512/2612 09012KT 9999 FEW040", nearest.rawTaf)

      val history = manager.fetch24hHistory("UTAM")
      assertEquals(2, history.metars.size)
      assertEquals(1, history.tafs.size)
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun `aviation performance calculator computes density altitude humidity and warnings`() {
    val utam = com.example.data.AirportCatalog.findKnown("UTAM")!!
    val hotMetar = com.example.domain.AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAM 251200Z 09012KT CAVOK 40/10 Q1005",
      rawTafInput = "TAF UTAM",
      station = utam,
      sourceLabel = "test",
    )
    val hotMetrics = com.example.domain.AviationPerformanceCalculator.calculate(utam, hotMetar)
    assertTrue(hotMetrics.highDensityAltitudeWarning)
    assertTrue((hotMetrics.densityAltitudeFeet ?: 0) > hotMetrics.elevationFeet + 2000)
    assertTrue((hotMetrics.isaDeviationC ?: 0) >= 20)
    assertTrue(hotMetrics.advisorySummary().contains("Выс. DA"))

    val foggyMetar = com.example.domain.AviationWeatherParser.parse(
      rawMetarInput = "METAR UTAM 250400Z 00000KT 0400 FG OVC002 01/00 Q1015",
      rawTafInput = "TAF UTAM",
      station = utam,
      sourceLabel = "test",
    )
    val icyFogMetrics = com.example.domain.AviationPerformanceCalculator.calculate(utam, foggyMetar)
    assertTrue(icyFogMetrics.fogRiskWarning)
    assertTrue(icyFogMetrics.icingRiskWarning)
    assertTrue((icyFogMetrics.relativeHumidityPercent ?: 0) >= 90)
  }
}
