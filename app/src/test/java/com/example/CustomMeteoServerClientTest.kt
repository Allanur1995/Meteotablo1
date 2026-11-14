package com.example

import com.example.data.CustomMeteoServerClient
import com.example.data.ServerConfig
import com.example.data.SharedPreferencesServerConfigStorage
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMeteoServerClientTest {

  @Test
  fun `default server config points to user IP with meteo credentials`() {
    val cfg = ServerConfig()
    assertEquals("http://217.174.229.206:8181/", cfg.url)
    assertEquals("meteo", cfg.username)
    assertEquals("meteo", cfg.password)
    assertTrue(cfg.useCustomServer)
  }

  @Test
  fun `normalizeServerUrl adds http scheme and trailing slash`() {
    assertEquals(
      "http://217.174.229.206:8181/",
      SharedPreferencesServerConfigStorage.normalizeServerUrl("217.174.229.206:8181"),
    )
    assertEquals(
      "http://217.174.229.206:8181/",
      SharedPreferencesServerConfigStorage.normalizeServerUrl("http://217.174.229.206:8181"),
    )
  }

  @Test
  fun `parseHtmlForm extracts action hidden token login and password fields`() {
    val html = """
      <html><body>
        <form action="/auth/login" method="post">
          <input type="hidden" name="csrf" value="abc123" />
          <input type="text" name="login" />
          <input type="password" name="parol" />
          <input type="submit" name="btn" value="Вход" />
        </form>
      </body></html>
    """.trimIndent()

    assertTrue(CustomMeteoServerClient.isLoginFormHtml(html))
    val parsed = CustomMeteoServerClient.parseHtmlForm(
      html = html,
      baseUrl = "http://217.174.229.206:8181/".toHttpUrl(),
      username = "meteo",
      password = "meteo",
    )
    assertNotNull(parsed)
    assertEquals("http://217.174.229.206:8181/auth/login", parsed!!.actionUrl)
    assertEquals("post", parsed.method)
    assertTrue(parsed.fields.contains("csrf" to "abc123"))
    assertTrue(parsed.fields.contains("login" to "meteo"))
    assertTrue(parsed.fields.contains("parol" to "meteo"))
  }

  @Test
  fun `extractMetarAndTaf extracts from html table and plaintext formats`() {
    val htmlPage = """
      <html><body>
        <div class="box">
          <pre>
            METAR UTAA 141700Z 11007KT 9999 SCT045 13/05 Q1022 NOSIG=
            TAF UTAA 141630Z 1418/1518 11010KT 8000 SCT040
            TEMPO 1500/1506 3000 BR BKN012=
          </pre>
        </div>
        <div class="box">
          UTAE 141700Z 14008KT 9999 FEW040 12/05 Q1023 NOSIG=
        </div>
      </body></html>
    """.trimIndent()

    assertFalse(CustomMeteoServerClient.isLoginFormHtml(htmlPage))

    val (utaaMetar, utaaTaf) = CustomMeteoServerClient.extractMetarAndTaf(htmlPage, "UTAA")
    assertEquals("METAR UTAA 141700Z 11007KT 9999 SCT045 13/05 Q1022 NOSIG", utaaMetar)
    assertEquals("TAF UTAA 141630Z 1418/1518 11010KT 8000 SCT040 TEMPO 1500/1506 3000 BR BKN012", utaaTaf)

    val (utaeMetar, _) = CustomMeteoServerClient.extractMetarAndTaf(htmlPage, "UTAE")
    assertEquals("METAR UTAE 141700Z 14008KT 9999 FEW040 12/05 Q1023 NOSIG", utaeMetar)
  }

  @Test
  fun `extractMetarAndTaf extracts from json payload`() {
    val json = """
      [{"icaoId":"UTAN","rawOb":"METAR UTAN 141700Z 29011KT 9999 SCT040 15/06 Q1020","rawTAF":"TAF UTAN 141630Z 1418/1518 30013KT CAVOK"}]
    """.trimIndent()

    val (metar, taf) = CustomMeteoServerClient.extractMetarAndTaf(json, "UTAN")
    assertEquals("METAR UTAN 141700Z 29011KT 9999 SCT040 15/06 Q1020", metar)
    assertEquals("TAF UTAN 141630Z 1418/1518 30013KT CAVOK", taf)
  }
}
