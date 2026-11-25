package com.example.data

import com.example.network.SessionCookieJar
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/**
 * Native Kotlin implementation of the UMAS `/cgi-bin/wdr` Python session flow:
 * 1. `POST <base>/cgi-bin/wdr` with form fields `user` & `pass` (preserving session cookies, no preemptive Basic Auth).
 * 2. Parse `<a href="...">` links whose visible text starts with `SATR` (METAR/SPECI) or `FTTR` (TAF).
 * 3. `GET` resolved bulletin URLs with the authenticated session cookies.
 * 4. Extract `METAR/SPECI/TAF <ICAO> ... =` reports for any requested airport (including all 7 TM stations:
 *    UTAM, UTAK, UTAA, UTAN, UTAT, UTAV, UTAE).
 */
class UmasWdrClient(
  private val cookieJar: CookieJar = SessionCookieJar(),
) {

  data class WdrLink(
    val name: String,
    val resolvedUrl: String,
    val order: Int,
  )

  data class WdrFetchResult(
    val rawMetar: String?,
    val rawTaf: String?,
    val hostLabel: String,
    val logs: List<String>,
    val reachable: Boolean = false,
    val httpCode: Int? = null,
    val errorMessage: String? = null,
  )

  data class WdrHistoryResult(
    val metars: List<String>,
    val tafs: List<String>,
    val hostLabel: String,
    val logs: List<String>,
    val errorMessage: String? = null,
  )

  private val client: OkHttpClient = OkHttpClient.Builder()
    .cookieJar(cookieJar)
    .protocols(listOf(Protocol.HTTP_1_1))
    .followRedirects(true)
    .followSslRedirects(true)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

  fun fetchStation(icao: String, config: ServerConfig): WdrFetchResult {
    val cleanIcao = icao.trim().uppercase()
    val logs = mutableListOf<String>()
    val wdrHttpUrl = resolveWdrUrl(config.url)
      ?: return WdrFetchResult(
        rawMetar = null,
        rawTaf = null,
        hostLabel = config.url,
        logs = listOf("WDR: некорректный URL ${config.url}"),
        errorMessage = "Некорректный URL сервера: ${config.url}",
      )
    val hostLabel = formatHostLabel(wdrHttpUrl)

    return try {
      val (postUrl, postCode, postHtml) = postWdrLogin(wdrHttpUrl, config, logs)
      if (postCode !in 200..299 || postHtml.isBlank()) {
        return WdrFetchResult(
          rawMetar = null,
          rawTaf = null,
          hostLabel = hostLabel,
          logs = logs,
          reachable = true,
          httpCode = postCode,
          errorMessage = "WDR POST вернул HTTP $postCode на $hostLabel",
        )
      }

      if (CustomMeteoServerClient.isLoginFormHtml(postHtml)) {
        return WdrFetchResult(
          rawMetar = null,
          rawTaf = null,
          hostLabel = hostLabel,
          logs = logs,
          reachable = true,
          httpCode = postCode,
          errorMessage = "WDR отклонил логин/пароль (показана форма входа)",
        )
      }

      val (satrLinks, fttrLinks) = extractSatrAndFttrLinks(postHtml, postUrl)
      logs.add("WDR ссылки: SATR=${satrLinks.size}, FTTR=${fttrLinks.size}")

      var foundMetar = extractWeatherMessage(postHtml, cleanIcao, "METAR")
      var foundTaf = extractWeatherMessage(postHtml, cleanIcao, "TAF")

      val visited = mutableSetOf<String>()

      // Prioritize SATR links (exact Python order: SATR[0] first, then remaining up to 12)
      val orderedSatr = prioritizeLinksForStation(satrLinks, cleanIcao).take(12)
      for (link in orderedSatr) {
        if (!visited.add(link.resolvedUrl)) continue
        logs.add("WDR SATR GET ${link.name} -> ${link.resolvedUrl}")
        val (code, body) = getPage(link.resolvedUrl)
        logs.add("WDR HTTP $code <- ${link.name} (${body.length} символов)")
        if (code in 200..299 && body.isNotBlank()) {
          if (foundMetar.isNullOrBlank()) {
            foundMetar = extractWeatherMessage(body, cleanIcao, "METAR")
              ?: CustomMeteoServerClient.extractMetarAndTaf(body, cleanIcao).first
          }
          if (foundTaf.isNullOrBlank()) {
            foundTaf = extractWeatherMessage(body, cleanIcao, "TAF")
              ?: CustomMeteoServerClient.extractMetarAndTaf(body, cleanIcao).second
          }
          if (!foundMetar.isNullOrBlank()) break
        }
      }

      // Fetch FTTR links for TAF (FTTR[0] first, scanning up to 10)
      if (foundTaf.isNullOrBlank()) {
        val orderedFttr = prioritizeLinksForStation(fttrLinks, cleanIcao).take(10)
        for (link in orderedFttr) {
          if (!visited.add(link.resolvedUrl)) continue
          logs.add("WDR FTTR GET ${link.name} -> ${link.resolvedUrl}")
          val (code, body) = getPage(link.resolvedUrl)
          logs.add("WDR HTTP $code <- ${link.name} (${body.length} символов)")
          if (code in 200..299 && body.isNotBlank()) {
            if (foundMetar.isNullOrBlank()) {
              foundMetar = extractWeatherMessage(body, cleanIcao, "METAR")
                ?: CustomMeteoServerClient.extractMetarAndTaf(body, cleanIcao).first
            }
            if (foundTaf.isNullOrBlank()) {
              foundTaf = extractWeatherMessage(body, cleanIcao, "TAF")
                ?: CustomMeteoServerClient.extractMetarAndTaf(body, cleanIcao).second
            }
            if (!foundTaf.isNullOrBlank()) break
          }
        }
      }

      if (foundMetar.isNullOrBlank()) {
        val preview = stripHtmlPreserveLines(postHtml).replace(Regex("\\s+"), " ").take(120)
        logs.add("WDR: METAR для $cleanIcao не найден. Ответ: $preview")
        WdrFetchResult(
          rawMetar = null,
          rawTaf = foundTaf,
          hostLabel = hostLabel,
          logs = logs,
          reachable = true,
          httpCode = postCode,
          errorMessage = "На сервере $hostLabel (WDR) нет METAR для $cleanIcao",
        )
      } else {
        logs.add("WDR OK ($cleanIcao): $foundMetar")
        WdrFetchResult(
          rawMetar = foundMetar,
          rawTaf = foundTaf,
          hostLabel = hostLabel,
          logs = logs,
          reachable = true,
          httpCode = postCode,
        )
      }
    } catch (e: Exception) {
      val msg = e.message ?: e::class.simpleName ?: "Ошибка соединения WDR"
      logs.add("WDR ошибка ($hostLabel): $msg")
      WdrFetchResult(
        rawMetar = null,
        rawTaf = null,
        hostLabel = hostLabel,
        logs = logs,
        reachable = false,
        errorMessage = "IP-сервер $hostLabel недоступен: $msg",
      )
    }
  }

  fun fetchHistory24h(
    icao: String,
    config: ServerConfig,
    maxBulletins: Int = 24,
  ): WdrHistoryResult {
    val cleanIcao = icao.trim().uppercase()
    val logs = mutableListOf<String>()
    val wdrHttpUrl = resolveWdrUrl(config.url)
      ?: return WdrHistoryResult(emptyList(), emptyList(), config.url, logs, "Некорректный URL ${config.url}")
    val hostLabel = formatHostLabel(wdrHttpUrl)

    return try {
      val (postUrl, postCode, postHtml) = postWdrLogin(wdrHttpUrl, config, logs)
      if (postCode !in 200..299 || postHtml.isBlank() || CustomMeteoServerClient.isLoginFormHtml(postHtml)) {
        return WdrHistoryResult(emptyList(), emptyList(), hostLabel, logs, "WDR вход не удался (HTTP $postCode)")
      }

      val (satrLinks, fttrLinks) = extractSatrAndFttrLinks(postHtml, postUrl)
      val metars = linkedSetOf<String>()
      val tafs = linkedSetOf<String>()

      CustomMeteoServerClient.extractAllMetarsAndTafs(postHtml, cleanIcao).let { (m, t) ->
        metars.addAll(m)
        tafs.addAll(t)
      }

      val visited = mutableSetOf<String>()
      for (link in satrLinks.take(maxBulletins)) {
        if (!visited.add(link.resolvedUrl)) continue
        val (code, body) = getPage(link.resolvedUrl)
        if (code in 200..299 && body.isNotBlank()) {
          val (m, t) = CustomMeteoServerClient.extractAllMetarsAndTafs(body, cleanIcao)
          metars.addAll(m)
          tafs.addAll(t)
        }
      }
      for (link in fttrLinks.take(8)) {
        if (!visited.add(link.resolvedUrl)) continue
        val (code, body) = getPage(link.resolvedUrl)
        if (code in 200..299 && body.isNotBlank()) {
          val (m, t) = CustomMeteoServerClient.extractAllMetarsAndTafs(body, cleanIcao)
          metars.addAll(m)
          tafs.addAll(t)
        }
      }

      logs.add("WDR 24ч ($cleanIcao): METAR=${metars.size}, TAF=${tafs.size}")
      WdrHistoryResult(metars.toList(), tafs.toList(), hostLabel, logs)
    } catch (e: Exception) {
      val msg = e.message ?: e::class.simpleName ?: "Ошибка WDR истории"
      logs.add("WDR 24ч ошибка: $msg")
      WdrHistoryResult(emptyList(), emptyList(), hostLabel, logs, msg)
    }
  }

  private fun postWdrLogin(
    wdrUrl: HttpUrl,
    config: ServerConfig,
    logs: MutableList<String>,
  ): Triple<HttpUrl, Int, String> {
    logs.add("WDR POST $wdrUrl (user=${config.username.ifBlank { "нет" }})")
    val formBody = FormBody.Builder(Charsets.UTF_8)
      .add("user", config.username)
      .add("pass", config.password)
      .build()
    val request = Request.Builder()
      .url(wdrUrl)
      .header("User-Agent", BROWSER_USER_AGENT)
      .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.8")
      .post(formBody)
      .build()

    client.newCall(request).execute().use { resp ->
      val bodyText = decodeResponseBody(resp)
      val finalUrl = resp.request.url
      logs.add("WDR HTTP ${resp.code} <- $finalUrl (${bodyText.length} символов)")
      return Triple(finalUrl, resp.code, bodyText)
    }
  }

  private fun getPage(url: String): Pair<Int, String> {
    val request = Request.Builder()
      .url(url)
      .header("User-Agent", BROWSER_USER_AGENT)
      .header("Accept", "text/html,text/plain,*/*;q=0.8")
      .get()
      .build()
    client.newCall(request).execute().use { resp ->
      return resp.code to decodeResponseBody(resp)
    }
  }

  private fun decodeResponseBody(resp: Response): String {
    val body = resp.body ?: return ""
    val bytes = runCatching { body.bytes() }.getOrNull() ?: return ""
    if (bytes.isEmpty()) return ""

    val headerCharset = body.contentType()?.charset(null)
    if (headerCharset != null) {
      return String(bytes, headerCharset)
    }

    val previewAscii = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
    val metaCharset = Regex("charset\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)", RegexOption.IGNORE_CASE)
      .find(previewAscii)
      ?.groupValues
      ?.getOrNull(1)
      ?.let { runCatching { Charset.forName(it) }.getOrNull() }
    if (metaCharset != null) {
      return String(bytes, metaCharset)
    }

    val utf8 = String(bytes, Charsets.UTF_8)
    return if (utf8.contains('\uFFFD')) {
      runCatching { String(bytes, Charset.forName("windows-1251")) }.getOrDefault(utf8)
    } else {
      utf8
    }
  }

  companion object {
    val TURKMEN_AIRPORT_ICAOS = setOf("UTAM", "UTAK", "UTAA", "UTAN", "UTAT", "UTAV", "UTAE")

    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (compatible; Meteotablo/1.0)"
    private val anchorRegex = Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
    private val hrefDqRegex = Regex("\\bhref\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val hrefSqRegex = Regex("\\bhref\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
    private val hrefBareRegex = Regex("\\bhref\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE)

    fun resolveWdrUrl(rawServerUrl: String): HttpUrl? {
      val normalized = ServerConfig.normalizeServerUrl(rawServerUrl)
      val parsed = normalized.toHttpUrlOrNull() ?: return null
      return if (parsed.encodedPath.endsWith("/wdr", ignoreCase = true)) {
        parsed.newBuilder().query(null).build()
      } else {
        parsed.newBuilder().encodedPath("/cgi-bin/wdr").query(null).build()
      }
    }

    fun formatHostLabel(url: HttpUrl): String {
      return if (url.port != 80 && url.port != 443) {
        "${url.host}:${url.port}"
      } else {
        url.host
      }
    }

    /**
     * Parses links exactly like the Python script:
     * `name = link.get_text(strip=True)`
     * `href = link.get("href", "")`
     * keeping links whose text starts with `SATR` or `FTTR`.
     */
    fun extractSatrAndFttrLinks(
      html: String,
      baseUrl: HttpUrl,
    ): Pair<List<WdrLink>, List<WdrLink>> {
      val satr = mutableListOf<WdrLink>()
      val fttr = mutableListOf<WdrLink>()
      var order = 0

      for (match in anchorRegex.findAll(html)) {
        val attrs = match.groupValues[1]
        val innerHtml = match.groupValues[2]
        val text = stripHtmlPreserveLines(innerHtml).replace(Regex("\\s+"), " ").trim()
        val href = extractHref(attrs)?.trim().orEmpty()
        if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", ignoreCase = true)) {
          continue
        }
        val resolved = baseUrl.resolve(href)?.toString() ?: continue
        val link = WdrLink(name = text, resolvedUrl = resolved, order = order++)
        when {
          text.startsWith("SATR", ignoreCase = true) -> satr.add(link)
          text.startsWith("FTTR", ignoreCase = true) -> fttr.add(link)
        }
      }

      return satr.distinctBy { it.resolvedUrl } to fttr.distinctBy { it.resolvedUrl }
    }

    /**
     * Orders links so station-specific ones come first, preserving original HTML order
     * (where `messages[kind][0]` in Python is the newest bulletin at the top of the table)
     * as the tie-breaker rather than reversing it.
     */
    fun prioritizeLinksForStation(links: List<WdrLink>, icao: String): List<WdrLink> {
      if (links.size <= 1) return links
      val upper = icao.trim().uppercase()
      return links.sortedWith(
        compareByDescending<WdrLink> {
          it.name.contains(upper, ignoreCase = true) || it.resolvedUrl.contains(upper, ignoreCase = true)
        }.thenBy { it.order },
      )
    }

    /**
     * Direct port of Python `extract_weather(text, kind)` extended to work cleanly on both single
     * and collective multi-airport bulletins (`UTAM, UTAK, UTAA, UTAN, UTAT, UTAV, UTAE`) and HTML pages.
     */
    fun extractWeatherMessage(
      rawText: String,
      icao: String,
      kind: String,
    ): String? {
      val cleanIcao = Regex.escape(icao.trim().uppercase())
      val normalized = stripHtmlPreserveLines(rawText).replace("\r", "")

      val patterns = if (kind.equals("METAR", ignoreCase = true)) {
        listOf(
          // Exact Python regex extended with SPECI & optional COR/RTD/AMD modifiers
          Regex("(?m)^\\s*(?:METAR|SPECI)(?:\\s+(?:COR|RTD|AMD|CC[A-Z]))?\\s+$cleanIcao\\b"),
          // Also match METAR/SPECI mid-line if multiple reports share a paragraph
          Regex("\\b(?:METAR|SPECI)(?:\\s+(?:COR|RTD|AMD|CC[A-Z]))?\\s+$cleanIcao\\b"),
        )
      } else {
        listOf(
          // Exact Python TAF regex + modifiers
          Regex("(?m)^\\s*TAF(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))?\\s+$cleanIcao\\b"),
          // Collective TAF bulletins where header line has `TAF` alone and station line starts with ICAO DDHHMMZ DDHH/DDHH
          Regex("(?m)^\\s*$cleanIcao\\s+\\d{6}Z\\s+\\d{4}/\\d{4}\\b"),
        )
      }

      for (pattern in patterns) {
        val match = pattern.find(normalized) ?: continue
        val start = match.range.first
        val end = normalized.indexOf('=', startIndex = match.range.last + 1)
        val rawSlice = if (end != -1) {
          normalized.substring(start, end)
        } else {
          // If no '=' terminator, fall back to next station/report boundary or end of line block
          val tail = normalized.substring(start)
          val nextBoundary = Regex("\\n\\s*(?:METAR|SPECI|TAF|[A-Z]{4}\\s+\\d{6}Z|NNNN)\\b")
            .find(tail, startIndex = 1)
          if (nextBoundary != null) tail.substring(0, nextBoundary.range.first) else tail
        }

        var singleLine = rawSlice.replace(Regex("\\s+"), " ").trim().trimEnd('=')
        if (!kind.equals("METAR", ignoreCase = true) &&
          !singleLine.startsWith("TAF", ignoreCase = true)
        ) {
          singleLine = "TAF $singleLine"
        }
        if (singleLine.isNotBlank()) return singleLine
      }
      return null
    }

    /**
     * Extracts weather reports for all requested ICAO codes from a raw bulletin body at once.
     */
    fun extractAllStationsFromBulletin(
      rawText: String,
      kind: String,
      icaos: Set<String> = TURKMEN_AIRPORT_ICAOS,
    ): Map<String, String> {
      val result = linkedMapOf<String, String>()
      icaos.forEach { code ->
        extractWeatherMessage(rawText, code, kind)?.let { msg ->
          result[code] = msg
        }
      }
      return result
    }

    private fun extractHref(attrs: String): String? {
      hrefDqRegex.find(attrs)?.groupValues?.get(1)?.let { return it }
      hrefSqRegex.find(attrs)?.groupValues?.get(1)?.let { return it }
      return hrefBareRegex.find(attrs)?.groupValues?.get(1)
    }

    private fun stripHtmlPreserveLines(text: String): String {
      if (!text.contains('<')) {
        return text
          .replace("&nbsp;", " ")
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
      }
      return text
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<(?:br|p|div|tr|li|h[1-6]|pre|/p|/div|/tr|/li|/pre)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<td[^>]*>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    }
  }
}
