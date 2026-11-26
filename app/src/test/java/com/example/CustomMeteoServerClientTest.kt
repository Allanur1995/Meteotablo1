package com.example

import com.example.data.CustomMeteoServerClient
import com.example.data.ServerConfig
import com.example.data.SharedPreferencesServerConfigStorage
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMeteoServerClientTest {

  @Test
  fun `default server config points to user IP wdr CGI with meteo credentials`() {
    val cfg = ServerConfig()
    assertEquals("http://217.174.229.206:8181/cgi-bin/wdr", cfg.url)
    assertEquals("meteo", cfg.username)
    assertEquals("meteo", cfg.password)
    assertTrue(cfg.useCustomServer)
  }

  @Test
  fun `normalizeServerUrl adds http scheme preserves cgi path and appends slash only to bare host`() {
    assertEquals(
      "http://217.174.229.206:8181/",
      SharedPreferencesServerConfigStorage.normalizeServerUrl("217.174.229.206:8181"),
    )
    assertEquals(
      "http://217.174.229.206:8181/",
      SharedPreferencesServerConfigStorage.normalizeServerUrl("http://217.174.229.206:8181"),
    )
    assertEquals(
      "http://217.174.229.206:8181/cgi-bin/wlogin",
      SharedPreferencesServerConfigStorage.normalizeServerUrl("217.174.229.206:8181/cgi-bin/wlogin"),
    )
    assertEquals(
      "http://217.174.229.206:8181/cgi-bin/",
      ServerConfig.retrofitBaseUrl("217.174.229.206:8181/cgi-bin/wlogin"),
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

  @Test
  fun `extractAftnBulletinCandidates ranks newest SA and FT bulletins for collective stations`() {
    val wmaTableHtml = """
      <html><head><title>WWW-Interface</title></head><body>
      <div><a href="/cgi-bin/wma">WMA</a> | <a href="/cgi-bin/wda">WDA</a></div>
      <table>
        <tr><th colspan="2">Сообщения для пользователя "meteo"</th></tr>
        <tr>
          <td><input type="checkbox" name="m" value="1"><a href="/cgi-bin/wma?id=1">FTTR31UTAA_230500</a></td>
          <td><input type="checkbox" name="m" value="2"><a href="/cgi-bin/wma?id=2">FTTR31UTAA_231100</a></td>
        </tr>
        <tr>
          <td><input type="checkbox" name="m" value="3"><a href="/cgi-bin/wma?id=3">SATR31UTAA_230400</a></td>
          <td><input type="checkbox" name="m" value="4"><a href="/cgi-bin/wma?id=4">SATR31UTAA_231200</a></td>
        </tr>
        <tr>
          <td><input type="checkbox" name="m" value="5"><a href="/cgi-bin/wma?id=5">CAPA10AXMA_230600</a></td>
          <td><input type="checkbox" name="m" value="6"><a href="/cgi-bin/wma?id=6">BBPA90AXBA_230521</a></td>
        </tr>
      </table>
      </body></html>
    """.trimIndent()

    val base = "http://217.174.229.206:8181/cgi-bin/wlogin".toHttpUrl()
    val candidates = CustomMeteoServerClient.extractAftnBulletinCandidates(wmaTableHtml, "UTAM", base)

    val metarRanked = candidates.filter { it.kind == CustomMeteoServerClient.BulletinKind.METAR }
    val tafRanked = candidates.filter { it.kind == CustomMeteoServerClient.BulletinKind.TAF }

    assertEquals("SATR31UTAA_231200", metarRanked.first().label)
    assertEquals("http://217.174.229.206:8181/cgi-bin/wma?id=4", metarRanked.first().url)
    assertEquals("FTTR31UTAA_231100", tafRanked.first().label)
    assertEquals("http://217.174.229.206:8181/cgi-bin/wma?id=2", tafRanked.first().url)
    // Non-METAR/TAF AFTN bulletins (CAPA10 / BBPA90) must be ignored
    assertFalse(candidates.any { it.label.contains("CAPA10") || it.label.contains("BBPA90") })
  }

  @Test
  fun `post-login WWW-Interface inbox wrapped in delete form is not misclassified as login form`() {
    val inboxFormHtml = """
      <html><head><title>WWW-Interface</title></head><body>
        <div><a href="/cgi-bin/wma">WMA</a> | <a href="/cgi-bin/wda">WDA</a></div>
        <form action="/cgi-bin/wma" method="post">
          <input type="submit" name="del" value="Удалить" />
          <input type="button" value="Выделить все" />
          <input type="reset" value="Сброс" />
          <table>
            <tr><th colspan="2">Сообщения для пользователя "meteo"</th></tr>
            <tr>
              <td><input type="checkbox" name="msg" value="1" /><a href="/cgi-bin/wma?id=101">FTTR31UTAA_231100</a></td>
              <td><input type="checkbox" name="msg" value="2" /><a href="/cgi-bin/wma?id=102">SATR31UTAA_231200</a></td>
            </tr>
            <tr>
              <td><input type="checkbox" name="msg" value="3" /><a href="/cgi-bin/wma?id=100">SATR31UTAA_231000</a></td>
              <td><input type="checkbox" name="msg" value="4" /><a href="/cgi-bin/wma?id=99">FTTR31UTAA_230500</a></td>
            </tr>
          </table>
        </form>
      </body></html>
    """.trimIndent()

    assertFalse(CustomMeteoServerClient.isLoginFormHtml(inboxFormHtml))
    val base = "http://217.174.229.206:8181/cgi-bin/wlogin".toHttpUrl()
    val candidates = CustomMeteoServerClient.extractAftnBulletinCandidates(inboxFormHtml, "UTAA", base)
    val newestMetar = candidates.firstOrNull { it.kind == CustomMeteoServerClient.BulletinKind.METAR }
    val newestTaf = candidates.firstOrNull { it.kind == CustomMeteoServerClient.BulletinKind.TAF }
    assertNotNull(newestMetar)
    assertNotNull(newestTaf)
    assertEquals("SATR31UTAA_231200", newestMetar!!.label)
    assertEquals("FTTR31UTAA_231100", newestTaf!!.label)
  }

  @Test
  fun `fetchMetarAndTaf authenticates via wlogin CGI and fetches newest collective METAR and TAF`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/cgi-bin/wlogin") { ex ->
      if (ex.requestMethod.equals("POST", ignoreCase = true)) {
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("login=meteo"))
        assertTrue(body.contains("parol=meteo"))
        val html = """
          <html><head><title>WWW-Interface</title></head><body>
            <a href="/cgi-bin/wma">WMA</a>
            <table>
              <tr>
                <td><a href="/cgi-bin/wma?msg=old_taf">FTTR31UTAA_230500</a></td>
                <td><a href="/cgi-bin/wma?msg=new_taf">FTTR31UTAA_231100</a></td>
              </tr>
              <tr>
                <td><a href="/cgi-bin/wma?msg=old_sa">SATR31UTAA_230400</a></td>
                <td><a href="/cgi-bin/wma?msg=new_sa">SATR31UTAA_231200</a></td>
              </tr>
              <tr>
                <td><a href="/cgi-bin/wma?msg=radar">BBPA90AXBA_231220</a></td>
              </tr>
            </table>
          </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Set-Cookie", "UMAS_SID=ok123; Path=/")
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, html.size.toLong())
        ex.responseBody.use { it.write(html) }
      } else {
        val form = """
          <html><head><title>WWW-Interface</title></head><body>
            <form action="/cgi-bin/wlogin" method="post">
              <input type="text" name="login" />
              <input type="password" name="parol" />
              <input type="submit" name="ok" value="Login" />
            </form>
          </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, form.size.toLong())
        ex.responseBody.use { it.write(form) }
      }
    }

    server.createContext("/cgi-bin/wma") { ex ->
      val query = ex.requestURI.rawQuery.orEmpty()
      val content = when {
        query.contains("msg=new_sa") -> """
          <html><body><pre>
          ZCZC
          SATR31 UTAA 231200
          METAR UTAA 231200Z 09005MPS 9999 FEW040 16/02 Q1022 NOSIG=
          METAR UTAM 231200Z 08004KT 6000 BKN230 11/07 Q1023 NOSIG=
          NNNN
          </pre></body></html>
        """.trimIndent()
        query.contains("msg=old_sa") -> """
          <html><body><pre>
          METAR UTAM 230400Z 05002KT 4000 BR BKN010 06/05 Q1020=
          </pre></body></html>
        """.trimIndent()
        query.contains("msg=new_taf") -> """
          <html><body><pre>
          ZCZC
          FTTR31 UTAA 231100
          TAF
          UTAA 231100Z 2312/2412 09006MPS 9999 SCT040=
          UTAM 231100Z 2312/2412 08005MPS 6000 BKN030
               TEMPO 2318/2322 3000 BR=
          NNNN
          </pre></body></html>
        """.trimIndent()
        else -> "<html><body>empty</body></html>"
      }.toByteArray(Charsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
      ex.sendResponseHeaders(200, content.size.toLong())
      ex.responseBody.use { it.write(content) }
    }

    server.start()
    try {
      val port = server.address.port
      val client = CustomMeteoServerClient()
      val config = ServerConfig(
        url = "http://127.0.0.1:$port/cgi-bin/wlogin",
        username = "meteo",
        password = "meteo",
      )
      val outcome = runBlocking { client.fetchMetarAndTaf("UTAM", config) }
      assertEquals("METAR UTAM 231200Z 08004KT 6000 BKN230 11/07 Q1023 NOSIG", outcome.rawMetar)
      assertEquals("TAF UTAM 231100Z 2312/2412 08005MPS 6000 BKN030 TEMPO 2318/2322 3000 BR", outcome.rawTaf)

      val history24h = runBlocking { client.fetchHistory24h("UTAM", config, maxBulletins = 8) }
      assertTrue(history24h.observations.size >= 2)
      assertTrue(history24h.observations.any { it.rawMetar.contains("231200Z") })
      assertTrue(history24h.observations.any { it.rawMetar.contains("230400Z") })
      assertTrue(history24h.tafs.any { it.contains("231100Z") })
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun `fetchMetarAndTaf handles python UMAS cgi-bin wdr user-pass POST and SATR-FTTR links for all 7 turkmen airports`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/cgi-bin/wdr") { ex ->
      if (ex.requestMethod.equals("POST", ignoreCase = true)) {
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("user=meteo"))
        assertTrue(body.contains("pass=meteo"))
        val html = """
          <html><body>
            <h3>Сообщения WDR</h3>
            <a href="/cgi-bin/wdr?msg=satr_main">SATR31UTAA_250000</a><br/>
            <a href="/cgi-bin/wdr?msg=fttr_main">FTTR31UTAA_242300</a>
          </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Set-Cookie", "WDR_SESSION=tkm777; Path=/")
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, html.size.toLong())
        ex.responseBody.use { it.write(html) }
      } else {
        val q = ex.requestURI.rawQuery.orEmpty()
        val cookie = ex.requestHeaders.getFirst("Cookie").orEmpty()
        if (q.isNotBlank()) {
          assertTrue("Expected session cookie on GET $q", cookie.contains("WDR_SESSION=tkm777"))
        }
        val text = when {
          q.contains("msg=satr_main") -> """
            SATR31 UTAA 250000
            METAR UTAM 250000Z 09005MPS 9999 FEW040 16/04 Q1022 NOSIG=
            METAR UTAK 250000Z 31008MPS 9999 SCT030 14/06 Q1021=
            METAR UTAA 250000Z 11006MPS CAVOK 18/03 Q1023=
            METAR UTAN 250000Z 29007MPS 8000 SCT025 13/05 Q1020=
            METAR UTAT 250000Z 06004MPS 9999 FEW045 15/02 Q1024=
            METAR UTAV 250000Z 04006MPS 9999 SCT040 12/03 Q1025=
            METAR UTAE 250000Z 14005MPS 7000 FEW035 17/05 Q1021=
          """.trimIndent()
          q.contains("msg=fttr_main") -> """
            FTTR31 UTAA 242300
            TAF UTAM 242300Z 2500/2524 09006MPS 9999 SCT040=
            TAF UTAK 242300Z 2500/2524 31009MPS 8000 BKN025=
            TAF UTAA 242300Z 2500/2524 11007MPS CAVOK=
            TAF UTAN 242300Z 2500/2524 29008MPS 9999 SCT030=
            TAF UTAT 242300Z 2500/2524 06005MPS 9999 FEW040=
            TAF UTAV 242300Z 2500/2524 04006MPS CAVOK=
            TAF UTAE 242300Z 2500/2524 14006MPS 9999 SCT035=
          """.trimIndent()
          else -> ""
        }.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(200, text.size.toLong())
        ex.responseBody.use { it.write(text) }
      }
    }

    server.start()
    try {
      val port = server.address.port
      val client = CustomMeteoServerClient()
      val config = ServerConfig(
        url = "http://127.0.0.1:$port/cgi-bin/wdr",
        username = "meteo",
        password = "meteo",
      )
      val turkmenIcaos = listOf("UTAM", "UTAK", "UTAA", "UTAN", "UTAT", "UTAV", "UTAE")
      turkmenIcaos.forEach { icao ->
        val outcome = runBlocking { client.fetchMetarAndTaf(icao, config) }
        assertNotNull("Expected METAR for $icao", outcome.rawMetar)
        assertNotNull("Expected TAF for $icao", outcome.rawTaf)
        assertTrue("METAR for $icao should contain ICAO", outcome.rawMetar!!.contains("METAR $icao 250000Z"))
        assertTrue("TAF for $icao should contain ICAO", outcome.rawTaf!!.contains("TAF $icao 242300Z"))
      }
    } finally {
      server.stop(0)
    }
  }
  @Test
  fun `umas wdr client omits preemptive basic auth handles unquoted hrefs and serves as fallback from wlogin`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/cgi-bin/wlogin") { ex ->
      // Legacy wlogin returns 404 or blank page -> CustomMeteoServerClient should fall back to /cgi-bin/wdr
      val err = "<html><body>Not on wlogin</body></html>".toByteArray(Charsets.UTF_8)
      ex.sendResponseHeaders(404, err.size.toLong())
      ex.responseBody.use { it.write(err) }
    }
    server.createContext("/cgi-bin/wdr") { ex ->
      // Ensure NO preemptive Basic Auth header is sent (matches Python requests.Session)
      val authHeader = ex.requestHeaders.getFirst("Authorization")
      assertTrue("Must not send preemptive Basic Auth header, was: $authHeader", authHeader == null)

      if (ex.requestMethod.equals("POST", ignoreCase = true)) {
        val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("user=meteo"))
        assertTrue(body.contains("pass=meteo"))
        val html = """
          <html><body>
            <a href=/cgi-bin/wdr?id=s1><b>SATR31UTAA_250100</b></a>
            <a href='/cgi-bin/wdr?id=s0'>SATR31UTAA_250000</a>
            <a href="/cgi-bin/wdr?id=f1">FTTR31UTAA_250000</a>
          </body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Set-Cookie", "SID=wdr_clean_99; Path=/")
        ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        ex.sendResponseHeaders(200, html.size.toLong())
        ex.responseBody.use { it.write(html) }
      } else {
        val q = ex.requestURI.rawQuery.orEmpty()
        assertTrue(ex.requestHeaders.getFirst("Cookie").orEmpty().contains("SID=wdr_clean_99"))
        val text = when {
          q.contains("id=s1") -> """
            METAR UTAA 250100Z 09004MPS 9999 FEW040 16/03 Q1023=
            METAR UTAM 250100Z 11005MPS CAVOK 18/02 Q1022=
          """.trimIndent()
          q.contains("id=f1") -> """
            TAF UTAA 250000Z 2500/2524 09006MPS 9999 SCT040=
            TAF UTAM 250000Z 2500/2524 11007MPS CAVOK=
          """.trimIndent()
          else -> "empty"
        }.toByteArray(Charsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        ex.sendResponseHeaders(200, text.size.toLong())
        ex.responseBody.use { it.write(text) }
      }
    }

    server.start()
    try {
      val port = server.address.port
      val wdrClient = com.example.data.UmasWdrClient()
      val directRes = wdrClient.fetchStation(
        "UTAM",
        ServerConfig(url = "http://127.0.0.1:$port/cgi-bin/wdr", username = "meteo", password = "meteo"),
      )
      assertEquals("METAR UTAM 250100Z 11005MPS CAVOK 18/02 Q1022", directRes.rawMetar)
      assertEquals("TAF UTAM 250000Z 2500/2524 11007MPS CAVOK", directRes.rawTaf)

      // Also verify legacy /cgi-bin/wlogin config falls back seamlessly to /cgi-bin/wdr
      val client = CustomMeteoServerClient()
      val fallbackOutcome = runBlocking {
        client.fetchMetarAndTaf(
          "UTAA",
          ServerConfig(url = "http://127.0.0.1:$port/cgi-bin/wlogin", username = "meteo", password = "meteo"),
        )
      }
      assertEquals("METAR UTAA 250100Z 09004MPS 9999 FEW040 16/03 Q1023", fallbackOutcome.rawMetar)
      assertEquals("TAF UTAA 250000Z 2500/2524 09006MPS 9999 SCT040", fallbackOutcome.rawTaf)
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun `unit converter supports bidirectional hPa-inHg and mps-kt conversions`() {
    val inHg = com.example.domain.UnitConverter.convertHpaToInHg(1013.25)
    assertEquals(29.92, inHg, 0.02)
    assertEquals(1013.25, com.example.domain.UnitConverter.convertInHgToHpa(inHg), 0.5)

    val knots = com.example.domain.UnitConverter.convertMpsToKnots(10.0)
    assertEquals(19.44, knots, 0.1)
    assertEquals(10.0, com.example.domain.UnitConverter.convertKnotsToMps(knots), 0.1)
  }

  @Test
  fun `umas wdr client picks newest DDHHMM link below stale links decodes KOI8-R and strips duplicate TAF prefix`() {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var sawNoCacheHeader = false
    val koi8 = java.nio.charset.Charset.forName("KOI8-R")

    val nowCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    val ddNow = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
    val prevCal = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, -1) }
    val ddPrev = prevCal.get(java.util.Calendar.DAY_OF_MONTH)
    val hhNow = nowCal.get(java.util.Calendar.HOUR_OF_DAY)

    val staleStamp = String.format("%02d%02d00", ddPrev, hhNow)
    val freshStamp = String.format("%02d%02d00", ddNow, hhNow)

    server.createContext("/cgi-bin/wdr") { ex ->
      if (ex.requestHeaders.getFirst("Cache-Control")?.contains("no-cache", ignoreCase = true) == true) {
        sawNoCacheHeader = true
      }
      val html = """
        <html><body>
          <a href="/cgi-bin/wdr?menu=home">Главная (RU: Главная)</a>
          <a href="/DATA/METEO/SATR31UTAA_$staleStamp">SATR31UTAA_$staleStamp</a>
          <a href="/DATA/METEO/FTTR31UTAA_$staleStamp">FTTR31UTAA_$staleStamp</a>
          <a href="/DATA/METEO/SATR31UTAA_$freshStamp">SATR31UTAA_$freshStamp</a>
          <a href="/DATA/METEO/FTTR31UTAA_$freshStamp">FTTR31UTAA_$freshStamp</a>
        </body></html>
      """.trimIndent().toByteArray(koi8)
      ex.responseHeaders.add("Set-Cookie", "WDR_ID=fresh99; Path=/")
      ex.responseHeaders.add("Content-Type", "text/html; charset=koi8-r")
      ex.sendResponseHeaders(200, html.size.toLong())
      ex.responseBody.use { it.write(html) }
    }

    server.createContext("/DATA/METEO/") { ex ->
      val path = ex.requestURI.path
      val text = when {
        path.endsWith("SATR31UTAA_$staleStamp") -> "METAR UTAM ${staleStamp}Z 01001KT CAVOK 05/01 Q1005="
        path.endsWith("FTTR31UTAA_$staleStamp") -> "TAF UTAM ${staleStamp}Z 0100/0124 01001KT CAVOK="
        path.endsWith("SATR31UTAA_$freshStamp") -> """
          ZCZC
          SATR31 UTAA $freshStamp
          METAR UTAM ${freshStamp}Z 31010G20KT 9999 OVC056 15/07 Q1026 R88/CLRD70 NOSIG=
          METAR UTAN ${freshStamp}Z 28008KT CAVOK 14/05 Q1025=
          NNNN
        """.trimIndent()
        path.endsWith("FTTR31UTAA_$freshStamp") -> """
          ZCZC
          FTTR31 UTAA $freshStamp
          TAF TAF UTAM ${freshStamp}Z 2605/2706 33010G20KT 5000 SCT020CB=
          TAF UTAN NIL=
          NNNN
        """.trimIndent()
        else -> ""
      }.toByteArray(koi8)
      ex.responseHeaders.add("Content-Type", "text/plain; charset=koi8-r")
      ex.sendResponseHeaders(200, text.size.toLong())
      ex.responseBody.use { it.write(text) }
    }

    server.start()
    try {
      val port = server.address.port
      val client = com.example.data.UmasWdrClient()
      val config = ServerConfig(
        url = "http://127.0.0.1:$port/cgi-bin/wdr",
        username = "meteo",
        password = "meteo",
      )
      val utamRes = client.fetchStation("UTAM", config)
      assertTrue("Expected Cache-Control: no-cache request header", sawNoCacheHeader)
      assertNotNull(utamRes.rawMetar)
      assertNotNull(utamRes.rawTaf)
      // Must pick newest DDHHMM link, not the older top link
      assertTrue("Expected fresh $freshStamp METAR, got ${utamRes.rawMetar}", utamRes.rawMetar!!.contains("${freshStamp}Z"))
      // Must strip duplicate "TAF TAF" prefix cleanly
      assertTrue("Expected single TAF prefix, got ${utamRes.rawTaf}", utamRes.rawTaf!!.startsWith("TAF UTAM ${freshStamp}Z"))
      assertFalse(utamRes.rawTaf!!.startsWith("TAF TAF"))

      // UTAN NIL TAF support
      val utanRes = client.fetchStation("UTAN", config)
      assertEquals("METAR UTAN ${freshStamp}Z 28008KT CAVOK 14/05 Q1025", utanRes.rawMetar)
      assertEquals("TAF UTAN NIL", utanRes.rawTaf)
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun `umas wdr resolves DDHHMM in UTC even under GMT-8 timezone and preserves diagnostics without HTTP 200 GET overwrite`() {
    // Regression: Streaming Emulator runs in PST (-08:00), where today's UTC bulletin _DDHHMM
    // looks 8 hours "in the future" if compared against local Calendar.getInstance() instead of UTC.
    val originalTz = java.util.TimeZone.getDefault()
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("GMT-08:00"))
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var postContentType = ""
    var getCountWithoutQuery = 0

    try {
      // Simulate 2026-11-26 08:50 UTC (= 2026-11-26 00:50 PST)
      val fixedUtcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, java.util.Calendar.NOVEMBER, 26, 8, 50, 0)
      }
      val nowUtcMillis = fixedUtcCal.timeInMillis

      val novStampMillis = com.example.data.UmasWdrClient.parseBulletinTimestampMillis(
        "/DATA/METEO/SATR31UTAA_260800",
        nowMillis = nowUtcMillis,
      )
      val novCheckCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = novStampMillis
      }
      assertEquals(java.util.Calendar.NOVEMBER, novCheckCal.get(java.util.Calendar.MONTH))
      assertEquals(26, novCheckCal.get(java.util.Calendar.DAY_OF_MONTH))
      assertEquals(8, novCheckCal.get(java.util.Calendar.HOUR_OF_DAY))

      // Verify collective SA lines without repeated METAR prefix are extracted
      val collectiveText = """
        ZCZC
        SATR31 UTAA 260800
        UTAA 260800Z 11005MPS 9999 SCT040 15/04 Q1023 NOSIG=
        UTAM 260800Z 31010G20KT 9999 OVC056 15/07 Q1026 NOSIG=
        NNNN
      """.trimIndent()
      assertEquals(
        "METAR UTAA 260800Z 11005MPS 9999 SCT040 15/04 Q1023 NOSIG",
        com.example.data.UmasWdrClient.extractWeatherMessage(collectiveText, "UTAA", "METAR"),
      )

      // Verify exact FormBody Content-Type and preserved WDR failure diagnostics on /cgi-bin/wdr
      server.createContext("/cgi-bin/wdr") { ex ->
        if (ex.requestMethod.equals("POST", ignoreCase = true)) {
          postContentType = ex.requestHeaders.getFirst("Content-Type").orEmpty()
          val html = "<html><body><h3>WDR Portal: empty inbox</h3></body></html>".toByteArray(Charsets.UTF_8)
          ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
          ex.sendResponseHeaders(200, html.size.toLong())
          ex.responseBody.use { it.write(html) }
        } else {
          if (ex.requestURI.rawQuery.isNullOrBlank()) {
            getCountWithoutQuery++
          }
          val empty = "".toByteArray(Charsets.UTF_8)
          ex.sendResponseHeaders(200, empty.size.toLong())
          ex.responseBody.use { it.write(empty) }
        }
      }
      server.start()

      val port = server.address.port
      val client = CustomMeteoServerClient()
      val config = ServerConfig(
        url = "http://127.0.0.1:$port/cgi-bin/wdr",
        username = "meteo",
        password = "meteo",
      )
      val report = runBlocking { client.testConnection("UTAA", config) }
      assertFalse(report.metarFound)
      assertEquals("application/x-www-form-urlencoded", postContentType)
      assertEquals(0, getCountWithoutQuery)
      assertTrue(
        "Expected WDR diagnostic summary, got: ${report.summary}",
        report.summary.contains("WDR") && report.summary.contains("ссылки SATR не найдены"),
      )
    } finally {
      server.stop(0)
      java.util.TimeZone.setDefault(originalTz)
    }
  }

  @Test
  fun `umas wdr and custom client handle collective FTTR-FCTR inline equals AMD CNL missing equals and KOI8-R`() {
    val collectiveTaf = """
      ZCZC
      FTTR31 UTAA 260600
      TAF
      UTAA 260600Z 2606/2706 11005MPS 9999 SCT040= UTAK AMD 260615Z 2606/2706 15008MPS 9999 FEW033
           TEMPO 2612/2618 16014KT=
      AMD UTAT 260600 2606/2706 07004MPS CAVOK
      UTAV 260600Z NIL=
      UTAE CNL=
      NNNN
    """.trimIndent()

    // Inline after '=' + AMD after ICAO
    assertEquals(
      "TAF UTAK AMD 260615Z 2606/2706 15008MPS 9999 FEW033 TEMPO 2612/2618 16014KT",
      com.example.data.UmasWdrClient.extractWeatherMessage(collectiveTaf, "UTAK", "TAF"),
    )
    // Missing trailing '=' on UTAT must NOT swallow UTAV/UTAE; missing 'Z' accepted
    assertEquals(
      "TAF AMD UTAT 260600 2606/2706 07004MPS CAVOK",
      com.example.data.UmasWdrClient.extractWeatherMessage(collectiveTaf, "UTAT", "TAF"),
    )
    // NIL & CNL without TAF prefix in FTTR bulletin
    assertEquals(
      "TAF UTAV 260600Z NIL",
      com.example.data.UmasWdrClient.extractWeatherMessage(collectiveTaf, "UTAV", "TAF"),
    )
    assertEquals(
      "TAF UTAE CNL",
      com.example.data.UmasWdrClient.extractWeatherMessage(collectiveTaf, "UTAE", "TAF"),
    )

    // Same parity inside CustomMeteoServerClient.extractAllMetarsAndTafs
    val (_, utakTafs) = CustomMeteoServerClient.extractAllMetarsAndTafs(collectiveTaf, "UTAK")
    assertEquals(
      "TAF UTAK AMD 260615Z 2606/2706 15008MPS 9999 FEW033 TEMPO 2612/2618 16014KT",
      utakTafs.firstOrNull(),
    )
    val (_, utatTafs) = CustomMeteoServerClient.extractAllMetarsAndTafs(collectiveTaf, "UTAT")
    assertEquals("TAF AMD UTAT 260600 2606/2706 07004MPS CAVOK", utatTafs.firstOrNull())

    // Server test: FCTR/FTTR where newest bulletin is UTAA-only AMD, next newest collective bulletin has UTAK
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val koi8 = java.nio.charset.Charset.forName("KOI8-R")
    val fixedNowCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    val dd = fixedNowCal.get(java.util.Calendar.DAY_OF_MONTH)
    val prevHour = (fixedNowCal.get(java.util.Calendar.HOUR_OF_DAY) - 2 + 24) % 24
    val prevCal = (fixedNowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.HOUR_OF_DAY, -2) }
    val olderStamp = String.format("%02d%02d00", prevCal.get(java.util.Calendar.DAY_OF_MONTH), prevHour)
    val newerStamp = String.format("%02d%02d00", dd, fixedNowCal.get(java.util.Calendar.HOUR_OF_DAY))

    server.createContext("/cgi-bin/wdr") { ex ->
      val q = ex.requestURI.rawQuery.orEmpty()
      if (ex.requestMethod.equals("POST", ignoreCase = true)) {
        val html = """
          <html><body>
            <h3>Сообщения для meteo</h3>
            <a href="/DATA/METEO/SATR31UTAA_$newerStamp">SATR31UTAA_$newerStamp</a>
            <a href="/DATA/METEO/FCTR31UTAA_$newerStamp">FCTR31UTAA_$newerStamp</a>
            <a href="/cgi-bin/wdr?file=FTTR31UTAA_$olderStamp">FTTR31UTAA_$olderStamp</a>
          </body></html>
        """.trimIndent().toByteArray(koi8)
        ex.responseHeaders.add("Content-Type", "text/html") // omit charset to test KOI8-R fallback
        ex.sendResponseHeaders(200, html.size.toLong())
        ex.responseBody.use { it.write(html) }
      } else {
        val body = when {
          q.contains("FTTR31UTAA_$olderStamp") -> """
            FTTR31 UTAA $olderStamp
            TAF
            UTAA ${olderStamp}Z 2606/2706 11005MPS 9999 FEW040=
            UTAK ${olderStamp}Z 2606/2706 15007MPS 9999 FEW033=
          """.trimIndent()
          else -> ""
        }.toByteArray(koi8)
        ex.responseHeaders.add("Content-Type", "text/plain")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { it.write(body) }
      }
    }
    server.createContext("/DATA/METEO/") { ex ->
      val p = ex.requestURI.path
      val body = when {
        p.endsWith("SATR31UTAA_$newerStamp") -> "METAR UTAK ${newerStamp}Z 15014KT 9999 FEW033 11/05 Q1025="
        p.endsWith("FCTR31UTAA_$newerStamp") -> "TAF AMD UTAA ${newerStamp}Z 2609/2618 12008KT CAVOK=" // UTAA only
        else -> ""
      }.toByteArray(koi8)
      ex.responseHeaders.add("Content-Type", "text/plain")
      ex.sendResponseHeaders(200, body.size.toLong())
      ex.responseBody.use { it.write(body) }
    }
    server.start()
    try {
      val port = server.address.port
      val client = CustomMeteoServerClient()
      val config = ServerConfig(
        url = "http://127.0.0.1:$port/cgi-bin/wdr",
        username = "meteo",
        password = "meteo",
      )
      val outcome = runBlocking { client.fetchMetarAndTaf("UTAK", config) }
      assertEquals("METAR UTAK ${newerStamp}Z 15014KT 9999 FEW033 11/05 Q1025", outcome.rawMetar)
      assertEquals("TAF UTAK ${olderStamp}Z 2606/2706 15007MPS 9999 FEW033", outcome.rawTaf)
    } finally {
      server.stop(0)
    }
  }

  @Test
  fun `repository AUTO_IP_FIRST backfills missing IP TAF from internet while IP_ONLY stays strictly IP`() {
    val ipServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    ipServer.createContext("/cgi-bin/wdr") { ex ->
      val html = """
        <html><body>
          <a href="/DATA/METEO/SATR31UTAA_260900">SATR31UTAA_260900</a>
        </body></html>
      """.trimIndent().toByteArray(Charsets.UTF_8)
      ex.sendResponseHeaders(200, html.size.toLong())
      ex.responseBody.use { it.write(html) }
    }
    ipServer.createContext("/DATA/METEO/") { ex ->
      val text = "METAR UTAK 260900Z 15014KT 9999 FEW033 11/05 Q1025=".toByteArray(Charsets.UTF_8)
      ex.sendResponseHeaders(200, text.size.toLong())
      ex.responseBody.use { it.write(text) }
    }

    val awcMockServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    awcMockServer.createContext("/api/data/taf") { ex ->
      val json = """[{"icaoId":"UTAK","rawTAF":"TAF UTAK 260500Z 2606/2706 15012KT 9999 FEW033"}]""".toByteArray(Charsets.UTF_8)
      ex.responseHeaders.add("Content-Type", "application/json")
      ex.sendResponseHeaders(200, json.size.toLong())
      ex.responseBody.use { it.write(json) }
    }

    ipServer.start()
    awcMockServer.start()
    try {
      val ipPort = ipServer.address.port
      val awcPort = awcMockServer.address.port
      val networkManager = com.example.network.AviationWeatherNetworkManager(
        awcBaseUrl = "http://127.0.0.1:$awcPort/",
      )
      val repo = com.example.data.AviationWeatherRepository(publicNetworkManager = networkManager)
      val utak = com.example.data.AirportCatalog.findKnown("UTAK")!!

      val autoRes = runBlocking {
        repo.fetchLiveWeather(
          utak,
          ServerConfig(
            url = "http://127.0.0.1:$ipPort/cgi-bin/wdr",
            username = "meteo",
            password = "meteo",
            useNoaaTgftp = false,
            useVatsimMetar = false,
          ).withMode(com.example.data.DataSourceMode.AUTO_IP_FIRST),
        )
      }.getOrThrow()
      assertEquals("METAR UTAK 260900Z 15014KT 9999 FEW033 11/05 Q1025", autoRes.weather.rawMetar)
      assertEquals("TAF UTAK 260500Z 2606/2706 15012KT 9999 FEW033", autoRes.weather.rawTaf)

      val ipOnlyRes = runBlocking {
        repo.fetchLiveWeather(
          utak,
          ServerConfig(
            url = "http://127.0.0.1:$ipPort/cgi-bin/wdr",
            username = "meteo",
            password = "meteo",
            useNoaaTgftp = false,
            useVatsimMetar = false,
          ).withMode(com.example.data.DataSourceMode.IP_ONLY),
        )
      }.getOrThrow()
      assertEquals("TAF UTAK — нет данных", ipOnlyRes.weather.rawTaf)
    } finally {
      ipServer.stop(0)
      awcMockServer.stop(0)
    }
  }
}
