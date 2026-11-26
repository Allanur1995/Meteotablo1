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
 * Native Kotlin implementation of the UMAS `/cgi-bin/wdr` Python `WDRData` session flow:
 * 1. `POST <base>/cgi-bin/wdr` with form fields `user` & `pass` and `Cache-Control: no-cache` headers.
 * 2. Parse `<a href="...">` links matching `SATR` (METAR/SPECI) or `FTTR` (TAF), preferring `/DATA/METEO/`.
 * 3. Rank candidates strictly by bulletin filename timestamp `_DDHHMM` (most recent date <= now+1h first).
 * 4. `GET` newest bulletin URLs with session cookies + `no-cache` headers and KOI8-R/UTF-8 decoding.
 * 5. Extract `METAR/SPECI/TAF <ICAO> ... =` (and `TAF <ICAO> NIL =`) for all requested airports
 *    (`UTAA`, `UTAK`, `UTAM`, `UTAT`, `UTAV`, `UTAE`, `UTAN`).
 */
class UmasWdrClient(
  private val cookieJar: CookieJar = SessionCookieJar(),
) {

  data class WdrLink(
    val name: String,
    val resolvedUrl: String,
    val order: Int,
    val stampMillis: Long = Long.MIN_VALUE,
    val isDataMeteoPath: Boolean = false,
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
    .readTimeout(30, TimeUnit.SECONDS)
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

      val (satrLinks, fttrLinks) = extractSatrAndFttrLinks(postHtml, postUrl)
      logs.add("WDR ссылки: SATR=${satrLinks.size}, FTTR=${fttrLinks.size}")
      if (satrLinks.isNotEmpty()) {
        logs.add("WDR SATR топ: ${satrLinks.take(3).joinToString { it.name }}")
      }
      if (fttrLinks.isNotEmpty()) {
        logs.add("WDR FTTR топ: ${fttrLinks.take(3).joinToString { it.name }}")
      }

      if (satrLinks.isEmpty() && fttrLinks.isEmpty() && CustomMeteoServerClient.isLoginFormHtml(postHtml)) {
        return WdrFetchResult(
          rawMetar = null,
          rawTaf = null,
          hostLabel = hostLabel,
          logs = logs,
          reachable = true,
          httpCode = postCode,
          errorMessage = "WDR отклонил логин/пароль (показана форма входа на $hostLabel)",
        )
      }

      var foundMetar = extractWeatherMessage(postHtml, cleanIcao, "METAR")
        ?: CustomMeteoServerClient.extractMetarAndTaf(postHtml, cleanIcao).first
      var foundTaf = extractWeatherMessage(postHtml, cleanIcao, "TAF")
        ?: CustomMeteoServerClient.extractMetarAndTaf(postHtml, cleanIcao).second

      val visited = mutableSetOf<String>()

      // Fetch newest _DDHHMM SATR link first (matching Python WDRData.newest_link), scanning fallbacks if needed
      val orderedSatr = prioritizeLinksForStation(satrLinks, cleanIcao).take(24)
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

      // Fetch newest _DDHHMM FTTR/FCTR link first for TAF
      var inspectedFttrCount = 0
      if (foundTaf.isNullOrBlank()) {
        val orderedFttr = prioritizeLinksForStation(fttrLinks, cleanIcao).take(30)
        for (link in orderedFttr) {
          if (!visited.add(link.resolvedUrl)) continue
          inspectedFttrCount++
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

      if (!foundTaf.isNullOrBlank()) {
        logs.add("WDR TAF OK ($cleanIcao): ${foundTaf.take(100)}")
      } else {
        val topFttr = fttrLinks.firstOrNull()?.name
        logs.add(
          "WDR TAF не найден ($cleanIcao): FTTR=${fttrLinks.size}, проверено=$inspectedFttrCount" +
            (topFttr?.let { ", верхний: $it" } ?: ""),
        )
      }

      if (foundMetar.isNullOrBlank()) {
        val preview = stripHtmlPreserveLines(postHtml).replace(Regex("\\s+"), " ").take(120)
        logs.add("WDR: METAR для $cleanIcao не найден. Ответ: $preview")
        val linkHint = if (satrLinks.isEmpty()) {
          "ссылки SATR не найдены (ответ: ${preview.take(60)})"
        } else {
          "проверены SATR=${orderedSatr.size} (верхний: ${orderedSatr.first().name})"
        }
        WdrFetchResult(
          rawMetar = null,
          rawTaf = foundTaf,
          hostLabel = hostLabel,
          logs = logs,
          reachable = true,
          httpCode = postCode,
          errorMessage = "На $hostLabel (WDR) нет METAR для $cleanIcao: $linkHint",
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
      val orderedSatr = prioritizeLinksForStation(satrLinks, cleanIcao)
      val orderedFttr = prioritizeLinksForStation(fttrLinks, cleanIcao)
      val metars = linkedSetOf<String>()
      val tafs = linkedSetOf<String>()

      CustomMeteoServerClient.extractAllMetarsAndTafs(postHtml, cleanIcao).let { (m, t) ->
        metars.addAll(m)
        tafs.addAll(t)
      }

      val visited = mutableSetOf<String>()
      for (link in orderedSatr.take(maxBulletins)) {
        if (!visited.add(link.resolvedUrl)) continue
        val (code, body) = getPage(link.resolvedUrl)
        if (code in 200..299 && body.isNotBlank()) {
          val (m, t) = CustomMeteoServerClient.extractAllMetarsAndTafs(body, cleanIcao)
          metars.addAll(m)
          tafs.addAll(t)
        }
      }
      for (link in orderedFttr.take(16)) {
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
    val formBody = FormBody.Builder()
      .add("user", config.username)
      .add("pass", config.password)
      .build()
    val request = Request.Builder()
      .url(wdrUrl)
      .header("User-Agent", BROWSER_USER_AGENT)
      .header("Cache-Control", "no-cache")
      .header("Pragma", "no-cache")
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
      .header("Cache-Control", "no-cache")
      .header("Pragma", "no-cache")
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

    // Strict UTF-8 check: if valid UTF-8 use UTF-8; otherwise decode as KOI8-R (per Python WDRData)
    val strictUtf8 = runCatching {
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString()
    }.getOrNull()
    if (strictUtf8 != null) {
      return strictUtf8
    }

    return runCatching { String(bytes, Charset.forName("KOI8-R")) }
      .recoverCatching { String(bytes, Charset.forName("windows-1251")) }
      .getOrElse { String(bytes, Charsets.UTF_8) }
  }

  companion object {
    val TURKMEN_AIRPORT_ICAOS = setOf("UTAA", "UTAK", "UTAM", "UTAT", "UTAV", "UTAE", "UTAN")

    private const val BROWSER_USER_AGENT = "Mozilla/5.0"
    private val anchorRegex = Regex("<a\\b([^>]*)>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
    private val hrefDqRegex = Regex("\\bhref\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
    private val hrefSqRegex = Regex("\\bhref\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE)
    private val hrefBareRegex = Regex("\\bhref\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE)
    private val stampRegex = Regex("_(\\d{6})(?:\\.[A-Za-z0-9]+)?(?:$|[^\\d])")
    private val wmoTafHeaderRegex = Regex("\\b(?:FT|FC)[A-Z0-9]{2}(?:UT|TR)[A-Z0-9]{0,2}\\b")
    private val wmoMetarHeaderRegex = Regex("\\b(?:SA|SP)[A-Z0-9]{2}(?:UT|TR)[A-Z0-9]{0,2}\\b")
    private val tafContextRegex = Regex("\\b(?:TAF|FTTR|FCTR|FT[A-Z0-9]{2}|FC[A-Z0-9]{2})\\b", RegexOption.IGNORE_CASE)
    private val nextReportBoundaryRegex = Regex(
      "(?:^|\\n)\\s*(?:METAR|SPECI|TAF|ZCZC|NNNN|(?:(?:AMD|COR|RTD|CC[A-Z])\\s+)?[A-Z]{4}\\s+(?:\\d{6}Z?\\s+\\d{4}/\\d{4}|\\d{6}Z|(?:NIL|CNL)\\b))",
      RegexOption.IGNORE_CASE,
    )

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
     * Port of Python `WDRData.message_datetime(href)`:
     * Extracts `_DDHHMM` at the end of the bulletin filename and resolves it across
     * `(0, 1, 2)` months back so `candidate <= now + 1 hour`.
     */
    fun parseBulletinTimestampMillis(
      urlOrHref: String,
      nowMillis: Long = System.currentTimeMillis(),
    ): Long {
      val pathOnly = urlOrHref.substringBefore('?').substringBefore('#')
      val filename = pathOnly.substringAfterLast('/')
      val match = stampRegex.find(filename) ?: stampRegex.find(urlOrHref) ?: return Long.MIN_VALUE
      val stamp = match.groupValues[1]
      val day = stamp.substring(0, 2).toIntOrNull() ?: return Long.MIN_VALUE
      val hour = stamp.substring(2, 4).toIntOrNull() ?: return Long.MIN_VALUE
      val minute = stamp.substring(4, 6).toIntOrNull() ?: return Long.MIN_VALUE
      if (day !in 1..31 || hour !in 0..23 || minute !in 0..59) return Long.MIN_VALUE

      val utcTz = java.util.TimeZone.getTimeZone("UTC")
      val baseCal = java.util.Calendar.getInstance(utcTz).apply {
        timeInMillis = nowMillis
      }
      val baseYear = baseCal.get(java.util.Calendar.YEAR)
      val baseMonth = baseCal.get(java.util.Calendar.MONTH) // 0-indexed

      for (offset in 0..2) {
        var month = baseMonth - offset
        var year = baseYear
        while (month < 0) {
          month += 12
          year -= 1
        }
        val candidate = java.util.Calendar.getInstance(utcTz).apply {
          isLenient = false
          clear()
          set(year, month, day, hour, minute, 0)
          set(java.util.Calendar.MILLISECOND, 0)
        }
        val candidateMillis = runCatching { candidate.timeInMillis }.getOrNull() ?: continue
        if (candidateMillis <= nowMillis + 6L * 3_600_000L) {
          return candidateMillis
        }
      }
      return Long.MIN_VALUE
    }

    /**
     * Parses `<a href="...">` links matching `SATR`/`SPTR` or `FTTR`/`FCTR` in either anchor text or href
     * (`(a.get_text(" ", strip=True) + " " + href).upper()`). When `/DATA/METEO/` links are
     * present on the UMAS directory page, restricts to `/DATA/METEO/` exactly like Python `WDRData`.
     */
    fun extractSatrAndFttrLinks(
      html: String,
      baseUrl: HttpUrl,
      nowMillis: Long = System.currentTimeMillis(),
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
        val combinedUpper = "$text $href $resolved".uppercase()
        val isDataMeteo = href.uppercase().contains("/DATA/METEO/") ||
          resolved.uppercase().contains("/DATA/METEO/")
        val stampMillis = parseBulletinTimestampMillis(resolved, nowMillis)
          .takeIf { it != Long.MIN_VALUE }
          ?: parseBulletinTimestampMillis(text, nowMillis).takeIf { it != Long.MIN_VALUE }
          ?: parseBulletinTimestampMillis(href, nowMillis)
        val displayName = text.ifBlank { resolved.substringAfterLast('/') }
        val link = WdrLink(
          name = displayName,
          resolvedUrl = resolved,
          order = order++,
          stampMillis = stampMillis,
          isDataMeteoPath = isDataMeteo,
        )
        val isSatrOrSptr = combinedUpper.contains("SATR") ||
          combinedUpper.contains("SPTR") ||
          wmoMetarHeaderRegex.containsMatchIn(combinedUpper)
        val isFttrOrFctr = combinedUpper.contains("FTTR") ||
          combinedUpper.contains("FCTR") ||
          wmoTafHeaderRegex.containsMatchIn(combinedUpper)
        when {
          isFttrOrFctr -> fttr.add(link)
          isSatrOrSptr -> satr.add(link)
        }
      }

      val distinctSatr = satr.distinctBy { it.resolvedUrl }
      val distinctFttr = fttr.distinctBy { it.resolvedUrl }
      return distinctSatr to distinctFttr
    }

    /**
     * Orders bulletin links by newest `_DDHHMM` timestamp first (matching Python `max(..., key=message_datetime)`),
     * then `/DATA/METEO/` paths, then `FTTR`/`SATR` priority over secondary types, then station-specific URL/name matches, then HTML order.
     */
    fun prioritizeLinksForStation(links: List<WdrLink>, icao: String): List<WdrLink> {
      if (links.size <= 1) return links
      val upper = icao.trim().uppercase()
      return links.sortedWith(
        compareByDescending<WdrLink> { it.stampMillis }
          .thenByDescending { it.isDataMeteoPath }
          .thenByDescending {
            it.name.contains("FTTR", ignoreCase = true) ||
              it.resolvedUrl.contains("FTTR", ignoreCase = true) ||
              it.name.contains("SATR", ignoreCase = true) ||
              it.resolvedUrl.contains("SATR", ignoreCase = true)
          }
          .thenByDescending {
            it.name.contains(upper, ignoreCase = true) || it.resolvedUrl.contains(upper, ignoreCase = true)
          }
          .thenBy { it.order },
      )
    }

    /**
     * Direct port of Python `extract_report(text, kind, code)` supporting METAR/SPECI,
     * TAF, `TAF <ICAO> NIL =`, `TAF <ICAO> CNL =`, AMD/COR/RTD modifiers before or after ICAO,
     * collective multi-airport bulletins (`UTAA, UTAK, UTAM, UTAT, UTAV, UTAE, UTAN`),
     * and stripping accidental double `TAF TAF` / `METAR METAR` prefixes.
     */
    fun extractWeatherMessage(
      rawText: String,
      icao: String,
      kind: String,
    ): String? {
      val cleanIcao = Regex.escape(icao.trim().uppercase())
      val normalized = stripHtmlPreserveLines(rawText).replace("\r", "")

      val isMetar = kind.equals("METAR", ignoreCase = true)
      val patterns = if (isMetar) {
        listOf(
          Regex("\\b(?:METAR|SPECI)(?:\\s+(?:COR|RTD|AMD|CC[A-Z]))*\\s+$cleanIcao\\b", RegexOption.IGNORE_CASE),
          Regex("(?:^|=|\\n)\\s*(?:(?:COR|RTD|CC[A-Z])\\s+)?$cleanIcao\\s+\\d{6}Z(?!\\s+\\d{4}/\\d{4}\\b)", RegexOption.IGNORE_CASE),
        )
      } else {
        buildList {
          add(
            Regex(
              "\\bTAF(?:\\s+TAF)?(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))*\\s+$cleanIcao(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))?\\b",
              RegexOption.IGNORE_CASE,
            ),
          )
          add(
            Regex(
              "(?:^|=|\\n)\\s*(?:(?:AMD|COR|RTD|CC[A-Z])\\s+)?$cleanIcao(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))?\\s+\\d{6}Z?\\s+(?:\\d{4}/\\d{4}|\\d{6}|\\d{4})\\b",
              RegexOption.IGNORE_CASE,
            ),
          )
          if (tafContextRegex.containsMatchIn(normalized)) {
            add(
              Regex(
                "(?:^|=|\\n)\\s*(?:(?:AMD|COR|RTD|CC[A-Z])\\s+)?$cleanIcao(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))?(?:\\s+\\d{6}Z?)?\\s+(?:NIL|CNL)\\b",
                RegexOption.IGNORE_CASE,
              ),
            )
          }
        }
      }

      for (pattern in patterns) {
        val match = pattern.find(normalized) ?: continue
        var start = match.range.first
        while (start <= match.range.last && (normalized[start] == '=' || normalized[start].isWhitespace())) {
          start++
        }
        val searchFrom = (match.range.last + 1).coerceAtMost(normalized.length)
        val eqIdx = normalized.indexOf('=', startIndex = searchFrom)
        val nextBoundaryIdx = nextReportBoundaryRegex.find(normalized, startIndex = searchFrom)?.range?.first ?: -1
        val end = listOf(eqIdx, nextBoundaryIdx).filter { it >= 0 }.minOrNull() ?: normalized.length
        if (end <= start) continue

        val rawSlice = normalized.substring(start, end)
        var singleLine = rawSlice
          .replace(Regex("\\s+"), " ")
          .trim()
          .trimStart('=')
          .trimEnd('=')
          .trim()
          .replace(Regex("^TAF\\s+TAF\\b", RegexOption.IGNORE_CASE), "TAF")
          .replace(Regex("^METAR\\s+METAR\\b", RegexOption.IGNORE_CASE), "METAR")

        if (isMetar &&
          !singleLine.startsWith("METAR", ignoreCase = true) &&
          !singleLine.startsWith("SPECI", ignoreCase = true)
        ) {
          singleLine = "METAR $singleLine"
        } else if (!isMetar && !singleLine.startsWith("TAF", ignoreCase = true)) {
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
