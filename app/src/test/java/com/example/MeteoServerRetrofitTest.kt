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
}
