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
  fun `default server config points to user IP wlogin CGI with meteo credentials`() {
    val cfg = ServerConfig()
    assertEquals("http://217.174.229.206:8181/cgi-bin/wlogin", cfg.url)
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
    } finally {
      server.stop(0)
    }
  }
}
