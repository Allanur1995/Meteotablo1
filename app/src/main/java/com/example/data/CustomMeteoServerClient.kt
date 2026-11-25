package com.example.data

import com.example.network.MeteoLoginRequest
import com.example.network.MeteoServerApi
import com.example.network.MeteoServerRetrofitFactory
import com.example.network.ServerStationWeatherDto
import com.example.network.ServerWeatherEnvelopeDto
import com.example.network.SessionCookieJar
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.nio.charset.Charset
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import retrofit2.Response

class CustomMeteoServerClient {

  data class ParsedLoginForm(
    val actionUrl: String,
    val method: String,
    val fields: List<Pair<String, String>>,
  )

  enum class BulletinKind {
    METAR,
    TAF,
    GENERAL,
  }

  data class BulletinCandidate(
    val url: String,
    val label: String,
    val kind: BulletinKind,
    val stationMatchScore: Int,
    val rawTimestamp: Int?,
    val htmlOrder: Int,
  )

  data class CustomFetchOutcome(
    val rawMetar: String?,
    val rawTaf: String?,
    val sourceHost: String,
    val logs: List<String>,
    val errorMessage: String? = null,
  )

  data class HistoricalReportPair(
    val rawMetar: String,
    val rawTaf: String? = null,
    val obsTimeMillis: Long? = null,
  )

  data class CustomHistoryOutcome(
    val observations: List<HistoricalReportPair>,
    val tafs: List<String>,
    val sourceHost: String,
    val logs: List<String>,
    val errorMessage: String? = null,
  )

  data class ConnectionTestReport(
    val reachable: Boolean,
    val httpCode: Int?,
    val metarFound: Boolean,
    val summary: String,
    val logs: List<String>,
  )

  private val cookieJar = SessionCookieJar()
  private val moshi: Moshi = MeteoServerRetrofitFactory.createMoshi()
  private val wdrClient = UmasWdrClient(cookieJar)

  suspend fun testConnection(icao: String, config: ServerConfig): ConnectionTestReport {
    val outcome = fetchMetarAndTaf(icao, config)
    val httpCode = outcome.logs
      .mapNotNull { line ->
        Regex("(?:WDR HTTP|HTTP)\\s+(\\d{3})").find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
      }
      .firstOrNull()
    val reachable = httpCode != null
    val summary = when {
      !outcome.rawMetar.isNullOrBlank() ->
        "Успешно (${outcome.sourceHost}): ${outcome.rawMetar}"
      !outcome.errorMessage.isNullOrBlank() ->
        outcome.errorMessage
      else ->
        "Ответ получен, но METAR не найден"
    }
    return ConnectionTestReport(
      reachable = reachable,
      httpCode = httpCode,
      metarFound = !outcome.rawMetar.isNullOrBlank(),
      summary = summary,
      logs = outcome.logs,
    )
  }

  private data class AuthenticatedSessionDocs(
    val api: MeteoServerApi,
    val hostLabel: String,
    val baseDirUrlStr: String,
    val currentUrl: HttpUrl,
    val currentBody: String,
    val statusCode: Int,
    val inspectedDocs: List<Pair<HttpUrl, String>>,
    val candidates: List<BulletinCandidate>,
  )

  private suspend fun openAuthenticatedSessionDocs(
    cleanIcao: String,
    config: ServerConfig,
    logs: MutableList<String>,
  ): Pair<AuthenticatedSessionDocs?, String?> {
    val targetUrlStr = ServerConfig.normalizeServerUrl(config.url)
    val baseDirUrlStr = ServerConfig.retrofitBaseUrl(config.url)
    val baseHttpUrl = targetUrlStr.toHttpUrlOrNull()
      ?: return null to "Некорректный URL сервера: ${config.url}"
    val hostLabel = if (baseHttpUrl.port != 80 && baseHttpUrl.port != 443) {
      "${baseHttpUrl.host}:${baseHttpUrl.port}"
    } else {
      baseHttpUrl.host
    }

    val api = MeteoServerRetrofitFactory.create(
      config = config,
      cookieJar = cookieJar,
      moshi = moshi,
    )

    var currentUrl: HttpUrl
    var currentBody: String
    var statusCode: Int
    val wdrDirectFirst = if (baseHttpUrl.encodedPath.endsWith("/wdr", ignoreCase = true) && config.username.isNotBlank()) {
      tryUmasWdrDirectPost(api, baseHttpUrl, config, logs)
    } else {
      null
    }
    if (wdrDirectFirst != null) {
      currentUrl = wdrDirectFirst.first
      currentBody = wdrDirectFirst.second
      statusCode = 200
    } else {
      logs.add("Retrofit GET $targetUrlStr (логин: ${config.username.ifBlank { "нет" }})")
      val firstResp = api.fetchPage(targetUrlStr)
      currentUrl = firstResp.raw().request.url
      currentBody = readBody(firstResp)
      statusCode = firstResp.code()
      logs.add("HTTP $statusCode <- $currentUrl (${currentBody.length} символов)")
    }

    if (baseHttpUrl.encodedPath == "/") {
      val initialHasContent = isLoginFormHtml(currentBody) ||
        extractMetarAndTaf(currentBody, cleanIcao, moshi).first != null ||
        extractAftnBulletinCandidates(currentBody, cleanIcao, currentUrl).isNotEmpty() ||
        extractFrameUrls(currentBody, currentUrl).isNotEmpty()
      if (statusCode >= 400 || !initialHasContent) {
        val wloginUrl = baseHttpUrl.newBuilder().encodedPath("/cgi-bin/wlogin").build().toString()
        logs.add("Авто-проба UMAS CGI: GET $wloginUrl")
        val probeResp = runCatching { api.fetchPage(wloginUrl) }.getOrNull()
        if (probeResp != null) {
          val probeBody = readBody(probeResp)
          val probeCode = probeResp.code()
          logs.add("HTTP $probeCode <- ${probeResp.raw().request.url} (${probeBody.length} символов)")
          if (probeCode < 400 && (isLoginFormHtml(probeBody) || probeBody.isNotBlank())) {
            currentUrl = probeResp.raw().request.url
            currentBody = probeBody
            statusCode = probeCode
          }
        }
      }
    }

    if (statusCode == 401 || statusCode == 403) {
      if (config.username.isNotBlank()) {
        val loginResp = runCatching {
          api.loginJson(MeteoLoginRequest(config.username, config.password))
        }.getOrNull()
        if (loginResp != null && loginResp.isSuccessful) {
          logs.add("POST login -> HTTP ${loginResp.code()}")
          val retryResp = api.fetchPage(currentUrl.toString())
          currentUrl = retryResp.raw().request.url
          currentBody = readBody(retryResp)
          statusCode = retryResp.code()
          logs.add("Повторный GET -> HTTP $statusCode (${currentBody.length} символов)")
        }
      }
      if (statusCode == 401 || statusCode == 403) {
        return null to "Сервер $hostLabel отклонил авторизацию (HTTP $statusCode)"
      }
    }

    extractMetaRefreshUrl(currentBody, currentUrl)?.let { refreshUrl ->
      logs.add("Meta-refresh -> GET $refreshUrl")
      val redirResp = runCatching { api.fetchPage(refreshUrl) }.getOrNull()
      if (redirResp != null && redirResp.isSuccessful) {
        currentUrl = redirResp.raw().request.url
        currentBody = readBody(redirResp)
        statusCode = redirResp.code()
        logs.add("HTTP $statusCode <- $currentUrl (${currentBody.length} символов)")
      }
    }

    if (isLoginFormHtml(currentBody)) {
      val parsedForm = parseHtmlForm(currentBody, currentUrl, config.username, config.password)
      if (parsedForm != null) {
        logs.add("Форма входа -> Retrofit ${parsedForm.method.uppercase()} ${parsedForm.actionUrl}")
        val postResp = submitParsedForm(api, parsedForm)
        currentUrl = postResp.raw().request.url
        currentBody = readBody(postResp)
        statusCode = postResp.code()
        logs.add("Ответ после входа: HTTP $statusCode ($currentUrl)")

        extractMetaRefreshUrl(currentBody, currentUrl)?.let { refreshUrl ->
          logs.add("Meta-refresh после входа -> GET $refreshUrl")
          val redirResp = runCatching { api.fetchPage(refreshUrl) }.getOrNull()
          if (redirResp != null && redirResp.isSuccessful) {
            currentUrl = redirResp.raw().request.url
            currentBody = readBody(redirResp)
          }
        }
      }
    }

    if ((baseHttpUrl.encodedPath.endsWith("/wdr", ignoreCase = true) ||
        statusCode >= 400 ||
        isLoginFormHtml(currentBody) ||
        (extractMetarAndTaf(currentBody, cleanIcao, moshi).first.isNullOrBlank() &&
          extractAftnBulletinCandidates(currentBody, cleanIcao, currentUrl).isEmpty() &&
          extractFrameUrls(currentBody, currentUrl).isEmpty())) &&
      config.username.isNotBlank()
    ) {
      val wdrOutcome = tryUmasWdrDirectPost(api, baseHttpUrl, config, logs)
      if (wdrOutcome != null) {
        currentUrl = wdrOutcome.first
        currentBody = wdrOutcome.second
        statusCode = 200
      }
    }

    if (isLoginFormHtml(currentBody)) {
      return null to "Не удалось войти на $hostLabel (проверьте логин и пароль)"
    }

    val inspectedDocs = mutableListOf(currentUrl to currentBody)
    val frameUrls = extractFrameUrls(currentBody, currentUrl).take(4)
    for (frameUrl in frameUrls) {
      logs.add("Frame GET $frameUrl")
      val frameResp = runCatching { api.fetchPage(frameUrl) }.getOrNull() ?: continue
      val frameBody = readBody(frameResp)
      val frameResolvedUrl = frameResp.raw().request.url
      logs.add("Frame HTTP ${frameResp.code()} <- $frameUrl (${frameBody.length} символов)")
      if (frameResp.isSuccessful && !isLoginFormHtml(frameBody) && frameBody.isNotBlank()) {
        inspectedDocs.add(frameResolvedUrl to frameBody)
      }
    }

    var candidates = inspectedDocs
      .flatMap { (docUrl, docBody) -> extractAftnBulletinCandidates(docBody, cleanIcao, docUrl) }
      .distinctBy { it.url }

    if (candidates.isEmpty() && inspectedDocs.none { (_, body) -> extractMetarAndTaf(body, cleanIcao, moshi).first != null }) {
      val wmaNavUrl = inspectedDocs.firstNotNullOfOrNull { (docUrl, docBody) ->
        extractWmaNavUrl(docBody, docUrl)
      }
      if (wmaNavUrl != null && inspectedDocs.none { it.first.toString() == wmaNavUrl }) {
        logs.add("Вкладка WMA -> GET $wmaNavUrl")
        val wmaResp = runCatching { api.fetchPage(wmaNavUrl) }.getOrNull()
        if (wmaResp != null && wmaResp.isSuccessful) {
          val wmaBody = readBody(wmaResp)
          val wmaUrl = wmaResp.raw().request.url
          logs.add("WMA HTTP ${wmaResp.code()} <- $wmaUrl (${wmaBody.length} символов)")
          if (!isLoginFormHtml(wmaBody) && wmaBody.isNotBlank()) {
            inspectedDocs.add(wmaUrl to wmaBody)
            candidates = inspectedDocs
              .flatMap { (docUrl, docBody) -> extractAftnBulletinCandidates(docBody, cleanIcao, docUrl) }
              .distinctBy { it.url }
          }
        }
      }
    }

    if (candidates.isEmpty() &&
      inspectedDocs.none { (_, body) -> extractMetarAndTaf(body, cleanIcao, moshi).first != null } &&
      config.username.isNotBlank() &&
      !currentUrl.encodedPath.endsWith("/wdr", ignoreCase = true)
    ) {
      val wdrOutcome = tryUmasWdrDirectPost(api, baseHttpUrl, config, logs)
      if (wdrOutcome != null) {
        currentUrl = wdrOutcome.first
        currentBody = wdrOutcome.second
        inspectedDocs.add(currentUrl to currentBody)
        candidates = inspectedDocs
          .flatMap { (docUrl, docBody) -> extractAftnBulletinCandidates(docBody, cleanIcao, docUrl) }
          .distinctBy { it.url }
      }
    }

    return AuthenticatedSessionDocs(
      api = api,
      hostLabel = hostLabel,
      baseDirUrlStr = baseDirUrlStr,
      currentUrl = currentUrl,
      currentBody = currentBody,
      statusCode = statusCode,
      inspectedDocs = inspectedDocs,
      candidates = candidates,
    ) to null
  }

  suspend fun fetchHistory24h(
    icao: String,
    config: ServerConfig,
    maxBulletins: Int = 24,
  ): CustomHistoryOutcome {
    val logs = mutableListOf<String>()
    val cleanIcao = icao.trim().uppercase()
    val hostFallback = config.url
    return try {
      val normalizedUrl = ServerConfig.normalizeServerUrl(config.url).toHttpUrlOrNull()
      if (normalizedUrl != null && normalizedUrl.encodedPath.endsWith("/wdr", ignoreCase = true)) {
        val wdrHist = wdrClient.fetchHistory24h(cleanIcao, config, maxBulletins)
        logs.addAll(wdrHist.logs)
        if (wdrHist.metars.isNotEmpty()) {
          val latestTaf = wdrHist.tafs.firstOrNull()
          val pairs = wdrHist.metars.map { HistoricalReportPair(rawMetar = it, rawTaf = latestTaf) }
          return CustomHistoryOutcome(
            observations = pairs,
            tafs = wdrHist.tafs,
            sourceHost = wdrHist.hostLabel,
            logs = logs,
          )
        }
      }

      val (session, authErr) = openAuthenticatedSessionDocs(cleanIcao, config, logs)
      if (session == null) {
        val wdrHist = wdrClient.fetchHistory24h(cleanIcao, config, maxBulletins)
        logs.addAll(wdrHist.logs)
        if (wdrHist.metars.isNotEmpty()) {
          val latestTaf = wdrHist.tafs.firstOrNull()
          return CustomHistoryOutcome(
            observations = wdrHist.metars.map { HistoricalReportPair(rawMetar = it, rawTaf = latestTaf) },
            tafs = wdrHist.tafs,
            sourceHost = wdrHist.hostLabel,
            logs = logs,
          )
        }
        return CustomHistoryOutcome(
          observations = emptyList(),
          tafs = emptyList(),
          sourceHost = hostFallback,
          logs = logs,
          errorMessage = authErr ?: "Ошибка подключения к серверу",
        )
      }

      val collectedMetars = linkedSetOf<String>()
      val collectedTafs = linkedSetOf<String>()

      session.inspectedDocs.forEach { (_, docBody) ->
        val (mList, tList) = extractAllMetarsAndTafs(docBody, cleanIcao, moshi)
        collectedMetars.addAll(mList)
        collectedTafs.addAll(tList)
      }

      val visited = mutableSetOf<String>()
      val metarBulletins = rankCandidates(
        session.candidates.filter { it.kind == BulletinKind.METAR || it.kind == BulletinKind.GENERAL },
      ).take(maxBulletins)

      for (cand in metarBulletins) {
        if (cand.url == session.currentUrl.toString() || !visited.add(cand.url)) continue
        val resp = runCatching { session.api.fetchPage(cand.url) }.getOrNull() ?: continue
        if (!resp.isSuccessful) continue
        val body = readBody(resp)
        if (body.isBlank() || isLoginFormHtml(body)) continue
        val (mList, tList) = extractAllMetarsAndTafs(body, cleanIcao, moshi)
        collectedMetars.addAll(mList)
        collectedTafs.addAll(tList)
      }

      val tafBulletins = rankCandidates(
        session.candidates.filter { it.kind == BulletinKind.TAF },
      ).take(8)

      for (cand in tafBulletins) {
        if (cand.url == session.currentUrl.toString() || !visited.add(cand.url)) continue
        val resp = runCatching { session.api.fetchPage(cand.url) }.getOrNull() ?: continue
        if (!resp.isSuccessful) continue
        val body = readBody(resp)
        if (body.isBlank() || isLoginFormHtml(body)) continue
        val (mList, tList) = extractAllMetarsAndTafs(body, cleanIcao, moshi)
        collectedMetars.addAll(mList)
        collectedTafs.addAll(tList)
      }

      logs.add("История 24ч (${session.hostLabel}): METAR=${collectedMetars.size}, TAF=${collectedTafs.size}")
      val latestTaf = collectedTafs.firstOrNull()
      val pairs = collectedMetars.map { metarLine ->
        HistoricalReportPair(
          rawMetar = metarLine,
          rawTaf = latestTaf,
        )
      }
      CustomHistoryOutcome(
        observations = pairs,
        tafs = collectedTafs.toList(),
        sourceHost = session.hostLabel,
        logs = logs,
      )
    } catch (e: Exception) {
      val msg = e.message ?: e::class.simpleName ?: "Ошибка загрузки истории"
      logs.add("История 24ч ошибка: $msg")
      CustomHistoryOutcome(
        observations = emptyList(),
        tafs = emptyList(),
        sourceHost = hostFallback,
        logs = logs,
        errorMessage = msg,
      )
    }
  }

  suspend fun fetchMetarAndTaf(icao: String, config: ServerConfig): CustomFetchOutcome {
    val logs = mutableListOf<String>()
    val cleanIcao = icao.trim().uppercase()
    val targetUrlStr = ServerConfig.normalizeServerUrl(config.url)
    val baseDirUrlStr = ServerConfig.retrofitBaseUrl(config.url)
    val baseHttpUrl = targetUrlStr.toHttpUrlOrNull()
      ?: return CustomFetchOutcome(
        rawMetar = null,
        rawTaf = null,
        sourceHost = config.url,
        logs = listOf("Некорректный URL сервера: ${config.url}"),
        errorMessage = "Некорректный URL сервера: ${config.url}",
      )
    val hostLabel = if (baseHttpUrl.port != 80 && baseHttpUrl.port != 443) {
      "${baseHttpUrl.host}:${baseHttpUrl.port}"
    } else {
      baseHttpUrl.host
    }

    return try {
      val isExplicitWdrPath = baseHttpUrl.encodedPath.endsWith("/wdr", ignoreCase = true)
      if (isExplicitWdrPath) {
        val wdrResult = wdrClient.fetchStation(cleanIcao, config)
        logs.addAll(wdrResult.logs)
        if (!wdrResult.rawMetar.isNullOrBlank()) {
          return CustomFetchOutcome(
            rawMetar = wdrResult.rawMetar,
            rawTaf = wdrResult.rawTaf,
            sourceHost = wdrResult.hostLabel,
            logs = logs,
          )
        }
        if (!wdrResult.reachable) {
          return CustomFetchOutcome(
            rawMetar = null,
            rawTaf = null,
            sourceHost = wdrResult.hostLabel,
            logs = logs,
            errorMessage = wdrResult.errorMessage ?: "IP-сервер ${wdrResult.hostLabel} недоступен",
          )
        }
      }

      val api = MeteoServerRetrofitFactory.create(
        config = config,
        cookieJar = cookieJar,
        moshi = moshi,
      )

      var currentUrl: HttpUrl
      var currentBody: String
      var statusCode: Int
      logs.add("Retrofit GET $targetUrlStr (логин: ${config.username.ifBlank { "нет" }})")
      val firstResp = api.fetchPage(targetUrlStr)
      currentUrl = firstResp.raw().request.url
      currentBody = readBody(firstResp)
      statusCode = firstResp.code()
      logs.add("HTTP $statusCode <- $currentUrl (${currentBody.length} символов)")

      // If user configured bare host "/" and root returns an error or empty shell, auto-probe /cgi-bin/wlogin
      if (baseHttpUrl.encodedPath == "/") {
        val initialHasContent = isLoginFormHtml(currentBody) ||
          extractMetarAndTaf(currentBody, cleanIcao, moshi).first != null ||
          extractAftnBulletinCandidates(currentBody, cleanIcao, currentUrl).isNotEmpty() ||
          extractFrameUrls(currentBody, currentUrl).isNotEmpty()
        if (statusCode >= 400 || !initialHasContent) {
          val wloginUrl = baseHttpUrl.newBuilder().encodedPath("/cgi-bin/wlogin").build().toString()
          logs.add("Авто-проба UMAS CGI: GET $wloginUrl")
          val probeResp = runCatching { api.fetchPage(wloginUrl) }.getOrNull()
          if (probeResp != null) {
            val probeBody = readBody(probeResp)
            val probeCode = probeResp.code()
            logs.add("HTTP $probeCode <- ${probeResp.raw().request.url} (${probeBody.length} символов)")
            if (probeCode < 400 && (isLoginFormHtml(probeBody) || probeBody.isNotBlank())) {
              currentUrl = probeResp.raw().request.url
              currentBody = probeBody
              statusCode = probeCode
            }
          }
        }
      }

      if (statusCode == 401 || statusCode == 403) {
        if (config.username.isNotBlank()) {
          val loginResp = runCatching {
            api.loginJson(MeteoLoginRequest(config.username, config.password))
          }.getOrNull()
          if (loginResp != null && loginResp.isSuccessful) {
            logs.add("POST login -> HTTP ${loginResp.code()}")
            val retryResp = api.fetchPage(currentUrl.toString())
            currentUrl = retryResp.raw().request.url
            currentBody = readBody(retryResp)
            statusCode = retryResp.code()
            logs.add("Повторный GET -> HTTP $statusCode (${currentBody.length} символов)")
          }
        }
        if (statusCode == 401 || statusCode == 403) {
          return CustomFetchOutcome(
            rawMetar = null,
            rawTaf = null,
            sourceHost = hostLabel,
            logs = logs,
            errorMessage = "Сервер $hostLabel отклонил авторизацию (HTTP $statusCode)",
          )
        }
      }

      // Follow HTML <meta http-equiv="refresh" ...> if present
      extractMetaRefreshUrl(currentBody, currentUrl)?.let { refreshUrl ->
        logs.add("Meta-refresh -> GET $refreshUrl")
        val redirResp = runCatching { api.fetchPage(refreshUrl) }.getOrNull()
        if (redirResp != null && redirResp.isSuccessful) {
          currentUrl = redirResp.raw().request.url
          currentBody = readBody(redirResp)
          statusCode = redirResp.code()
          logs.add("HTTP $statusCode <- $currentUrl (${currentBody.length} символов)")
        }
      }

      if (baseHttpUrl.encodedPath.endsWith("/wdr", ignoreCase = true) && config.username.isNotBlank()) {
        val wdrDirect = tryUmasWdrDirectPost(api, baseHttpUrl, config, logs)
        if (wdrDirect != null) {
          currentUrl = wdrDirect.first
          currentBody = wdrDirect.second
          statusCode = 200
        }
      }

      // Handle HTML form login if the server presents a login form
      if (isLoginFormHtml(currentBody)) {
        val parsedForm = parseHtmlForm(currentBody, currentUrl, config.username, config.password)
        if (parsedForm != null) {
          logs.add("Форма входа -> Retrofit ${parsedForm.method.uppercase()} ${parsedForm.actionUrl}")
          val postResp = submitParsedForm(api, parsedForm)
          currentUrl = postResp.raw().request.url
          currentBody = readBody(postResp)
          statusCode = postResp.code()
          logs.add("Ответ после входа: HTTP $statusCode ($currentUrl)")

          extractMetaRefreshUrl(currentBody, currentUrl)?.let { refreshUrl ->
            logs.add("Meta-refresh после входа -> GET $refreshUrl")
            val redirResp = runCatching { api.fetchPage(refreshUrl) }.getOrNull()
            if (redirResp != null && redirResp.isSuccessful) {
              currentUrl = redirResp.raw().request.url
              currentBody = readBody(redirResp)
            }
          }

          if (isLoginFormHtml(currentBody)) {
            val wdrFallback = if (config.username.isNotBlank()) {
              tryUmasWdrDirectPost(api, baseHttpUrl, config, logs)
            } else {
              null
            }
            if (wdrFallback != null) {
              currentUrl = wdrFallback.first
              currentBody = wdrFallback.second
              statusCode = 200
            } else {
              return CustomFetchOutcome(
                rawMetar = null,
                rawTaf = null,
                sourceHost = hostLabel,
                logs = logs,
                errorMessage = "Не удалось войти на $hostLabel (проверьте логин и пароль)",
              )
            }
          }
        }
      }

      // Also probe POST /cgi-bin/wdr directly if initial path failed or produced no SATR/FTTR/METAR data
      if ((statusCode >= 400 ||
          (extractMetarAndTaf(currentBody, cleanIcao, moshi).first.isNullOrBlank() &&
            extractAftnBulletinCandidates(currentBody, cleanIcao, currentUrl).isEmpty() &&
            extractFrameUrls(currentBody, currentUrl).isEmpty() &&
            extractWmaNavUrl(currentBody, currentUrl) == null)) &&
        config.username.isNotBlank() &&
        !currentUrl.encodedPath.endsWith("/wdr", ignoreCase = true)
      ) {
        val wdrFallback = tryUmasWdrDirectPost(api, baseHttpUrl, config, logs)
        if (wdrFallback != null) {
          currentUrl = wdrFallback.first
          currentBody = wdrFallback.second
          statusCode = 200
        }
      }

      // 1. Collect landing document + any <frame>/<iframe> documents
      val inspectedDocs = mutableListOf(currentUrl to currentBody)
      var (metar, taf) = extractMetarAndTaf(currentBody, cleanIcao, moshi)

      if (metar.isNullOrBlank() || taf.isNullOrBlank()) {
        val frameUrls = extractFrameUrls(currentBody, currentUrl).take(4)
        for (frameUrl in frameUrls) {
          logs.add("Frame GET $frameUrl")
          val frameResp = runCatching { api.fetchPage(frameUrl) }.getOrNull() ?: continue
          val frameBody = readBody(frameResp)
          val frameResolvedUrl = frameResp.raw().request.url
          logs.add("Frame HTTP ${frameResp.code()} <- $frameUrl (${frameBody.length} символов)")
          if (frameResp.isSuccessful && !isLoginFormHtml(frameBody) && frameBody.isNotBlank()) {
            inspectedDocs.add(frameResolvedUrl to frameBody)
            val (fm, ft) = extractMetarAndTaf(frameBody, cleanIcao, moshi)
            if (metar.isNullOrBlank() && !fm.isNullOrBlank()) metar = fm
            if (taf.isNullOrBlank() && !ft.isNullOrBlank()) taf = ft
          }
        }
      }

      // 2. Gather AFTN/WMO bulletin candidates (e.g. UMAS WWW-Interface WMA message list: SATR31UTAA_231200, FTTR31UTAA_231100)
      var candidates = inspectedDocs
        .flatMap { (docUrl, docBody) -> extractAftnBulletinCandidates(docBody, cleanIcao, docUrl) }
        .distinctBy { it.url }

      // If no METAR/TAF and no AFTN bulletins found yet, check if there is a WMA / inbox navigation tab link
      if (metar.isNullOrBlank() && candidates.isEmpty()) {
        val wmaNavUrl = inspectedDocs.firstNotNullOfOrNull { (docUrl, docBody) ->
          extractWmaNavUrl(docBody, docUrl)
        }
        if (wmaNavUrl != null && inspectedDocs.none { it.first.toString() == wmaNavUrl }) {
          logs.add("Вкладка WMA -> GET $wmaNavUrl")
          val wmaResp = runCatching { api.fetchPage(wmaNavUrl) }.getOrNull()
          if (wmaResp != null && wmaResp.isSuccessful) {
            val wmaBody = readBody(wmaResp)
            val wmaUrl = wmaResp.raw().request.url
            logs.add("WMA HTTP ${wmaResp.code()} <- $wmaUrl (${wmaBody.length} символов)")
            if (!isLoginFormHtml(wmaBody) && wmaBody.isNotBlank()) {
              inspectedDocs.add(wmaUrl to wmaBody)
              val (wm, wt) = extractMetarAndTaf(wmaBody, cleanIcao, moshi)
              if (metar.isNullOrBlank() && !wm.isNullOrBlank()) metar = wm
              if (taf.isNullOrBlank() && !wt.isNullOrBlank()) taf = wt
              candidates = inspectedDocs
                .flatMap { (docUrl, docBody) -> extractAftnBulletinCandidates(docBody, cleanIcao, docUrl) }
                .distinctBy { it.url }
            }
          }
        }
      }

      val fetchedBulletinUrls = mutableSetOf<String>()

      // 3. Fetch newest matching METAR bulletin links (SATR* / SA.. / SP.. or station links)
      if (metar.isNullOrBlank() && candidates.isNotEmpty()) {
        val metarCandidates = rankCandidates(
          candidates.filter { it.kind == BulletinKind.METAR || it.kind == BulletinKind.GENERAL },
        ).take(12)

        for (candidate in metarCandidates) {
          if (candidate.url == currentUrl.toString()) continue
          fetchedBulletinUrls.add(candidate.url)
          logs.add("Бюллетень METAR (${candidate.label}) -> GET ${candidate.url}")
          val subResp = runCatching { api.fetchPage(candidate.url) }.getOrNull() ?: continue
          val subStatus = subResp.code()
          val subBody = readBody(subResp)
          logs.add("HTTP $subStatus <- ${candidate.url} (${subBody.length} символов)")
          if (subStatus in 200..299 && !isLoginFormHtml(subBody) && subBody.isNotBlank()) {
            val (subMetar, subTaf) = extractMetarAndTaf(subBody, cleanIcao, moshi)
            if (!subMetar.isNullOrBlank()) {
              metar = subMetar
              if (taf.isNullOrBlank() && !subTaf.isNullOrBlank()) taf = subTaf
              break
            } else if (!subTaf.isNullOrBlank() && taf.isNullOrBlank()) {
              taf = subTaf
            }
          }
        }
      }

      // 4. Independently fetch newest matching TAF bulletin links (FTTR* / FT.. / FC..) if TAF is still missing
      if (taf.isNullOrBlank() && candidates.isNotEmpty()) {
        val tafCandidates = rankCandidates(
          candidates.filter { it.kind == BulletinKind.TAF || it.kind == BulletinKind.GENERAL },
        ).take(8)

        for (candidate in tafCandidates) {
          if (candidate.url == currentUrl.toString() || candidate.url in fetchedBulletinUrls) continue
          fetchedBulletinUrls.add(candidate.url)
          logs.add("Бюллетень TAF (${candidate.label}) -> GET ${candidate.url}")
          val subResp = runCatching { api.fetchPage(candidate.url) }.getOrNull() ?: continue
          val subStatus = subResp.code()
          val subBody = readBody(subResp)
          logs.add("HTTP $subStatus <- ${candidate.url} (${subBody.length} символов)")
          if (subStatus in 200..299 && !isLoginFormHtml(subBody) && subBody.isNotBlank()) {
            val (subMetar, subTaf) = extractMetarAndTaf(subBody, cleanIcao, moshi)
            if (metar.isNullOrBlank() && !subMetar.isNullOrBlank()) {
              metar = subMetar
            }
            if (!subTaf.isNullOrBlank()) {
              taf = subTaf
              break
            }
          }
        }
      }

      // 5. Try typed Retrofit JSON endpoints if still missing METAR
      if (metar.isNullOrBlank()) {
        val dtoResult = tryTypedRetrofitEndpoints(api, cleanIcao, logs)
        if (!dtoResult.first.isNullOrBlank()) {
          metar = dtoResult.first
          if (taf.isNullOrBlank()) taf = dtoResult.second
        }
      }

      // 6. Fallback to generic station links and common aviation URL patterns
      if (metar.isNullOrBlank()) {
        val fallbackUrls = buildList {
          addAll(extractStationLinks(currentBody, cleanIcao, currentUrl))
          add("${baseDirUrlStr}?icao=$cleanIcao")
          add("${baseDirUrlStr}?station=$cleanIcao")
          add("${baseDirUrlStr}?id=$cleanIcao")
          add("${baseDirUrlStr}?airport=$cleanIcao")
          add("${baseDirUrlStr}metar/$cleanIcao")
          add("${baseDirUrlStr}metar.php?icao=$cleanIcao")
          add("${baseDirUrlStr}weather?icao=$cleanIcao")
          add("${baseDirUrlStr}svodka")
          add("${baseDirUrlStr}$cleanIcao.txt")
        }
          .distinct()
          .filterNot { it == currentUrl.toString() || it in fetchedBulletinUrls }
          .take(10)

        for (candidate in fallbackUrls) {
          logs.add("Retrofit GET $candidate")
          val subResp = runCatching { api.fetchPage(candidate) }.getOrNull() ?: continue
          val subStatus = subResp.code()
          val subBody = readBody(subResp)
          logs.add("HTTP $subStatus <- $candidate (${subBody.length} символов)")
          if (subStatus in 200..299 && !isLoginFormHtml(subBody) && subBody.isNotBlank()) {
            val (subMetar, subTaf) = extractMetarAndTaf(subBody, cleanIcao, moshi)
            if (!subMetar.isNullOrBlank()) {
              metar = subMetar
              if (taf.isNullOrBlank()) taf = subTaf
              break
            } else if (!subTaf.isNullOrBlank() && taf.isNullOrBlank()) {
              taf = subTaf
            }
          }
        }
      }

      // 7. Guaranteed fallback to clean Python-style /cgi-bin/wdr flow if not already tried
      if (metar.isNullOrBlank() && !isExplicitWdrPath && config.username.isNotBlank()) {
        val wdrResult = wdrClient.fetchStation(cleanIcao, config)
        logs.addAll(wdrResult.logs)
        if (!wdrResult.rawMetar.isNullOrBlank()) {
          metar = wdrResult.rawMetar
          if (taf.isNullOrBlank() && !wdrResult.rawTaf.isNullOrBlank()) {
            taf = wdrResult.rawTaf
          }
        }
      }

      if (metar.isNullOrBlank()) {
        val snippet = stripHtml(currentBody).replace(Regex("\\s+"), " ").take(140)
        logs.add("METAR для $cleanIcao не найден на $hostLabel. Фрагмент: $snippet")
        CustomFetchOutcome(
          rawMetar = null,
          rawTaf = taf,
          sourceHost = hostLabel,
          logs = logs,
          errorMessage = "На сервере $hostLabel нет METAR для $cleanIcao (HTTP $statusCode)",
        )
      } else {
        logs.add("Получен METAR с $hostLabel: $metar")
        if (!taf.isNullOrBlank()) {
          logs.add("Получен TAF с $hostLabel: ${taf.take(100)}")
        }
        CustomFetchOutcome(
          rawMetar = metar,
          rawTaf = taf,
          sourceHost = hostLabel,
          logs = logs,
        )
      }
    } catch (e: Exception) {
      val msg = e.message ?: e::class.simpleName ?: "Ошибка соединения"
      logs.add("Ошибка сервера $hostLabel: $msg")
      CustomFetchOutcome(
        rawMetar = null,
        rawTaf = null,
        sourceHost = hostLabel,
        logs = logs,
        errorMessage = "Сервер $hostLabel недоступен: $msg",
      )
    }
  }

  private suspend fun tryUmasWdrDirectPost(
    api: MeteoServerApi,
    baseHttpUrl: HttpUrl,
    config: ServerConfig,
    logs: MutableList<String>,
  ): Pair<HttpUrl, String>? {
    val wdrUrl = baseHttpUrl.newBuilder().encodedPath("/cgi-bin/wdr").query(null).build().toString()
    logs.add("UMAS WDR POST $wdrUrl (user=${config.username})")
    val resp = runCatching {
      api.submitLoginForm(
        wdrUrl,
        linkedMapOf(
          "user" to config.username,
          "pass" to config.password,
        ),
      )
    }.getOrNull() ?: return null
    val body = readBody(resp)
    val resolvedUrl = resp.raw().request.url
    logs.add("WDR HTTP ${resp.code()} <- $resolvedUrl (${body.length} символов)")
    if (!resp.isSuccessful || body.isBlank() || isLoginFormHtml(body)) {
      return null
    }
    return resolvedUrl to body
  }

  private suspend fun submitParsedForm(
    api: MeteoServerApi,
    form: ParsedLoginForm,
  ): Response<ResponseBody> {
    val fieldMap = linkedMapOf<String, String>().apply {
      form.fields.forEach { (k, v) -> put(k, v) }
    }
    return if (form.method.equals("get", ignoreCase = true)) {
      api.fetchPageWithQuery(form.actionUrl, fieldMap)
    } else {
      api.submitLoginForm(form.actionUrl, fieldMap)
    }
  }

  private suspend fun tryTypedRetrofitEndpoints(
    api: MeteoServerApi,
    icao: String,
    logs: MutableList<String>,
  ): Pair<String?, String?> {
    runCatching {
      val resp = api.getStationWeather(icao)
      logs.add("Retrofit GET api/weather?icao=$icao -> HTTP ${resp.code()}")
      if (resp.isSuccessful) {
        val dto = resp.body()?.findForStation(icao)
        val m = dto?.resolvedMetar()
        val t = dto?.resolvedTaf()
        if (!m.isNullOrBlank()) return m to t
      }
    }
    runCatching {
      val resp = api.getMetarList(icao)
      logs.add("Retrofit GET api/metar?ids=$icao -> HTTP ${resp.code()}")
      if (resp.isSuccessful) {
        val list = resp.body().orEmpty()
        val dto = list.firstOrNull { it.resolvedIcao() == icao } ?: list.firstOrNull()
        val m = dto?.resolvedMetar()
        val t = dto?.resolvedTaf()
        if (!m.isNullOrBlank()) return m to t
      }
    }
    return null to null
  }

  private fun readBody(response: Response<ResponseBody>): String {
    val body = if (response.isSuccessful) response.body() else response.errorBody()
    if (body == null) return ""
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

    private val wmoBulletinRegex = Regex(
      "\\b((SA|SP|FT|FC)[A-Z0-9]{2,4})\\s*([A-Z]{4})(?:[_\\s-]?(\\d{6}))?\\b",
      RegexOption.IGNORE_CASE,
    )

    private val anchorRegex = Regex(
      "<a\\b([^>]*)>([\\s\\S]*?)</a>",
      RegexOption.IGNORE_CASE,
    )

    private val excludedAssetExtensions = listOf(".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg")

    fun isLoginFormHtml(html: String): Boolean {
      if (!html.contains("<form", ignoreCase = true)) return false
      if (wmoBulletinRegex.containsMatchIn(html) ||
        html.contains("Сообщения для пользователя", ignoreCase = true)
      ) {
        return false
      }
      val hasPasswordInput = Regex(
        "<input[^>]*type\\s*=\\s*[\"']?password[\"']?[^>]*>",
        setOf(RegexOption.IGNORE_CASE),
      ).containsMatchIn(html)
      if (hasPasswordInput) return true
      val lower = html.lowercase()
      return lower.contains("<form") &&
        (lower.contains("name=\"password\"") ||
          lower.contains("name='password'") ||
          lower.contains("name=\"parol\"") ||
          Regex("\\bвход\\b").containsMatchIn(lower) ||
          lower.contains("авторизац"))
    }

    fun extractMetaRefreshUrl(html: String, baseUrl: HttpUrl): String? {
      val metaRegex = Regex(
        "<meta[^>]*http-equiv\\s*=\\s*[\"']?refresh[\"']?[^>]*content\\s*=\\s*[\"'][^\"']*url\\s*=\\s*([^\"';>\\s]+)",
        RegexOption.IGNORE_CASE,
      )
      val target = metaRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: return null
      return baseUrl.resolve(target)?.toString()
    }

    fun extractFrameUrls(html: String, baseUrl: HttpUrl): List<String> {
      val frameRegex = Regex(
        "<i?frame[^>]*\\bsrc\\s*=\\s*[\"']([^\"'#]+)[\"']",
        RegexOption.IGNORE_CASE,
      )
      return frameRegex.findAll(html)
        .mapNotNull { baseUrl.resolve(it.groupValues[1].trim())?.toString() }
        .distinct()
        .toList()
    }

    fun extractWmaNavUrl(html: String, baseUrl: HttpUrl): String? {
      for (match in anchorRegex.findAll(html)) {
        val attrs = match.groupValues[1]
        val text = stripHtml(match.groupValues[2]).trim()
        val href = extractAttr(attrs, "href")?.trim().orEmpty()
        if (href.isBlank() || href.startsWith("#") || href.startsWith("javascript:", ignoreCase = true)) continue
        if (text.equals("WMA", ignoreCase = true) ||
          text.contains("Сообщения", ignoreCase = true) ||
          href.contains("/wma", ignoreCase = true) ||
          href.contains("tab=wma", ignoreCase = true)
        ) {
          return baseUrl.resolve(href)?.toString()
        }
      }
      return null
    }

    fun parseHtmlForm(
      html: String,
      baseUrl: HttpUrl,
      username: String,
      password: String,
    ): ParsedLoginForm? {
      val formRegex = Regex("<form([^>]*)>([\\s\\S]*?)</form>", RegexOption.IGNORE_CASE)
      val forms = formRegex.findAll(html).toList()
      if (forms.isEmpty()) return null

      val targetForm = forms.firstOrNull {
        it.value.contains("password", ignoreCase = true) || it.value.contains("parol", ignoreCase = true)
      } ?: forms.first()

      val formAttrs = targetForm.groupValues[1]
      val formBody = targetForm.groupValues[2]

      val rawAction = extractAttr(formAttrs, "action").orEmpty()
      val resolvedAction = if (rawAction.isBlank()) {
        baseUrl.toString()
      } else {
        baseUrl.resolve(rawAction)?.toString() ?: baseUrl.toString()
      }
      val method = extractAttr(formAttrs, "method")?.ifBlank { "post" } ?: "post"

      val inputRegex = Regex("<input([^>]*)/?>", RegexOption.IGNORE_CASE)
      val fields = mutableListOf<Pair<String, String>>()
      var usernameAssigned = false
      var passwordAssigned = false

      for (match in inputRegex.findAll(formBody)) {
        val attrs = match.groupValues[1]
        val name = extractAttr(attrs, "name")?.takeIf { it.isNotBlank() } ?: continue
        val type = extractAttr(attrs, "type")?.lowercase().orEmpty()
        val value = extractAttr(attrs, "value").orEmpty()
        val lowerName = name.lowercase()

        when {
          type == "password" || (!passwordAssigned && (lowerName.contains("pass") || lowerName.contains("pwd") || lowerName.contains("parol"))) -> {
            fields.add(name to password)
            passwordAssigned = true
          }
          type == "hidden" -> {
            fields.add(name to value)
          }
          type == "checkbox" || type == "radio" -> {
            if (attrs.contains("checked", ignoreCase = true)) {
              fields.add(name to value.ifBlank { "on" })
            }
          }
          type == "submit" || type == "button" || type == "image" -> {
            if (value.isNotBlank() || type != "image") {
              fields.add(name to value)
            }
          }
          !usernameAssigned && (type.isBlank() || type == "text" || type == "email" ||
            lowerName.contains("user") || lowerName.contains("login") || lowerName.contains("usr") || lowerName.contains("name")) -> {
            fields.add(name to username)
            usernameAssigned = true
          }
          else -> {
            fields.add(name to value)
          }
        }
      }

      // Preserve selected options from <select> elements if any (common on CGI web gateways)
      val selectRegex = Regex("<select([^>]*)>([\\s\\S]*?)</select>", RegexOption.IGNORE_CASE)
      val optionRegex = Regex("<option([^>]*)>([\\s\\S]*?)</option>", RegexOption.IGNORE_CASE)
      for (sel in selectRegex.findAll(formBody)) {
        val selName = extractAttr(sel.groupValues[1], "name")?.takeIf { it.isNotBlank() } ?: continue
        if (fields.any { it.first == selName }) continue
        val options = optionRegex.findAll(sel.groupValues[2]).toList()
        val chosen = options.firstOrNull { it.groupValues[1].contains("selected", ignoreCase = true) }
          ?: options.firstOrNull()
        if (chosen != null) {
          val optValue = extractAttr(chosen.groupValues[1], "value")
            ?: stripHtml(chosen.groupValues[2]).trim()
          fields.add(selName to optValue)
        }
      }

      if (!usernameAssigned) fields.add("username" to username)
      if (!passwordAssigned) fields.add("password" to password)

      return ParsedLoginForm(
        actionUrl = resolvedAction,
        method = method.lowercase(),
        fields = fields,
      )
    }

    fun extractAftnBulletinCandidates(
      html: String,
      icao: String,
      baseUrl: HttpUrl,
    ): List<BulletinCandidate> {
      val cleanIcao = icao.trim().uppercase()
      val stationPrefix2 = cleanIcao.take(2)
      val stationPrefix1 = cleanIcao.take(1)
      val result = mutableListOf<BulletinCandidate>()
      var order = 0

      for (match in anchorRegex.findAll(html)) {
        val attrs = match.groupValues[1]
        val linkText = stripHtml(match.groupValues[2]).replace(Regex("\\s+"), " ").trim()
        val rawTarget = resolveAnchorTarget(attrs) ?: continue
        val resolvedUrl = baseUrl.resolve(rawTarget)?.toString() ?: continue
        if (excludedAssetExtensions.any { resolvedUrl.substringBefore('?').endsWith(it, ignoreCase = true) }) {
          continue
        }

        val combined = "$linkText $resolvedUrl"
        val wmoMatch = wmoBulletinRegex.find(combined)
        val kind: BulletinKind
        val rawTimestamp: Int?
        var matchScore = 0
        val label: String

        if (wmoMatch != null) {
          val tt = wmoMatch.groupValues[2].uppercase()
          val originCccc = wmoMatch.groupValues[3].uppercase()
          val fullHeader = "${wmoMatch.groupValues[1].uppercase()}$originCccc"
          val tsStr = wmoMatch.groupValues[4]
          rawTimestamp = tsStr.takeIf { it.length == 6 }?.toIntOrNull()
          label = if (tsStr.isNotBlank()) "${fullHeader}_$tsStr" else fullHeader

          kind = when (tt) {
            "SA", "SP" -> BulletinKind.METAR
            "FT", "FC" -> BulletinKind.TAF
            else -> BulletinKind.GENERAL
          }

          matchScore = when {
            combined.contains(cleanIcao, ignoreCase = true) || originCccc == cleanIcao -> 3
            stationPrefix2 == "UT" && (originCccc == "UTAA" || fullHeader.contains("TR")) -> 2
            originCccc.startsWith(stationPrefix2) -> 2
            stationPrefix1.isNotEmpty() && originCccc.startsWith(stationPrefix1) -> 1
            else -> 0
          }
        } else {
          val isSatrLink = linkText.startsWith("SATR", ignoreCase = true)
          val isFttrLink = linkText.startsWith("FTTR", ignoreCase = true)
          val hasIcao = combined.contains(cleanIcao, ignoreCase = true)
          val hasMetarWord = isSatrLink ||
            combined.contains("metar", ignoreCase = true) ||
            combined.contains("speci", ignoreCase = true) ||
            combined.contains("svodka", ignoreCase = true) ||
            combined.contains("fact", ignoreCase = true) ||
            combined.contains("obs", ignoreCase = true)
          val hasTafWord = isFttrLink ||
            combined.contains("taf", ignoreCase = true) ||
            combined.contains("forecast", ignoreCase = true)

          if (!hasIcao && !hasMetarWord && !hasTafWord) continue

          kind = when {
            isFttrLink || (hasTafWord && !hasMetarWord) -> BulletinKind.TAF
            isSatrLink || (hasMetarWord && !hasTafWord) -> BulletinKind.METAR
            else -> BulletinKind.GENERAL
          }
          matchScore = when {
            hasIcao -> 3
            (isSatrLink || isFttrLink) && stationPrefix2 == "UT" -> 2
            else -> 0
          }
          rawTimestamp = Regex("(?:^|_)(\\d{6})(?:$|[^\\d])")
            .find(linkText)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
          label = linkText.ifBlank { resolvedUrl.substringAfterLast('/').take(24) }
        }

        result.add(
          BulletinCandidate(
            url = resolvedUrl,
            label = label,
            kind = kind,
            stationMatchScore = matchScore,
            rawTimestamp = rawTimestamp,
            htmlOrder = order++,
          ),
        )
      }

      return rankCandidates(result).distinctBy { it.url }
    }

    fun rankCandidates(candidates: List<BulletinCandidate>): List<BulletinCandidate> {
      if (candidates.size <= 1) return candidates
      val days = candidates.mapNotNull { it.rawTimestamp?.div(10000) }
      val hasMonthBoundary = days.any { it >= 25 } && days.any { it in 1..5 }
      return candidates.sortedWith(
        compareByDescending<BulletinCandidate> { it.stationMatchScore }
          .thenByDescending { c ->
            val ts = c.rawTimestamp ?: return@thenByDescending -1
            val day = ts / 10000
            val hhmm = ts % 10000
            val adjustedDay = if (hasMonthBoundary && day in 1..5) day + 31 else day
            adjustedDay * 10000 + hhmm
          }
          .thenBy { it.htmlOrder },
      )
    }

    private fun resolveAnchorTarget(attrs: String): String? {
      val href = extractAttr(attrs, "href")?.trim()
      if (!href.isNullOrBlank() &&
        !href.startsWith("#") &&
        !href.startsWith("javascript:", ignoreCase = true)
      ) {
        return href
      }
      val onclick = extractAttr(attrs, "onclick")?.trim().orEmpty()
      if (onclick.isBlank()) return null
      val jsUrl = Regex("['\"]((?:https?://|/|\\.?/)?[A-Za-z0-9_./?&=%+-]+(?:\\?[A-Za-z0-9_./?&=%+-]+)?)['\"]")
        .findAll(onclick)
        .map { it.groupValues[1].trim() }
        .firstOrNull { candidate ->
          candidate.contains("/") || candidate.contains("?") || candidate.contains(".php", true) || candidate.contains("wma", true)
        }
      return jsUrl
    }

    fun extractStationLinks(
      html: String,
      icao: String,
      baseUrl: HttpUrl,
    ): List<String> {
      val urls = mutableListOf<String>()
      val linkRegex = Regex("(?:href|src|value|location\\.href)\\s*=\\s*[\"']([^\"'#]+)[\"']", RegexOption.IGNORE_CASE)
      val keywords = listOf(
        icao, "metar", "taf", "weather", "svodka", "tablo", "meteo",
        "pogoda", "airport", "aeroport", "forecast", "main", "list", "data", "obs", "fact",
      )
      for (match in linkRegex.findAll(html)) {
        val candidate = match.groupValues[1].trim()
        if (keywords.any { candidate.contains(it, ignoreCase = true) }) {
          val resolved = baseUrl.resolve(candidate)?.toString()
          if (resolved != null &&
            !excludedAssetExtensions.any { resolved.substringBefore('?').endsWith(it, ignoreCase = true) }
          ) {
            urls.add(resolved)
          }
        }
      }
      return urls.distinct()
    }

    fun extractMetarAndTaf(
      rawBody: String,
      icao: String,
      moshi: Moshi = MeteoServerRetrofitFactory.createMoshi(),
    ): Pair<String?, String?> {
      val (metars, tafs) = extractAllMetarsAndTafs(rawBody, icao, moshi)
      return metars.firstOrNull() to tafs.firstOrNull()
    }

    fun extractAllMetarsAndTafs(
      rawBody: String,
      icao: String,
      moshi: Moshi = MeteoServerRetrofitFactory.createMoshi(),
    ): Pair<List<String>, List<String>> {
      val trimmed = rawBody.trim()
      if (trimmed.isEmpty()) return emptyList<String>() to emptyList()

      // 1. Try Moshi structured DTOs first when JSON is returned
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        val jsonResult = extractAllFromMoshiJson(trimmed, icao, moshi)
        if (jsonResult.first.isNotEmpty() || jsonResult.second.isNotEmpty()) {
          return jsonResult
        }
      }

      // 2. HTML / AFTN / Plaintext extraction
      val plain = stripHtml(trimmed)
        .replace(Regex("\\b(?:ZCZC|NNNN)\\b[^\\r\\n]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\b(METAR|SPECI|TAF)\\s*\\r?\\n\\s*", RegexOption.IGNORE_CASE), "$1 ")
      val upperIcao = Regex.escape(icao.trim().uppercase())

      val metarBoundaryLookahead = "(?==|\"|\\r?\\n\\s*(?:METAR|SPECI|TAF(?:\\s|$)|[A-Z]{4}\\s+\\d{6}Z|NNNN|\\r?\\n)|$)"
      val metarPatterns = listOf(
        Regex("\\b(?:METAR|SPECI)(?:\\s+(?:COR|RTD|AMD|CC[A-Z]))?\\s+$upperIcao\\s+\\d{6}Z(?!\\s+\\d{4}/\\d{4}\\b)[^=\"]*?$metarBoundaryLookahead", RegexOption.IGNORE_CASE),
        Regex("(?m)^\\s*(?:METAR|SPECI)(?:\\s+(?:COR|RTD|AMD|CC[A-Z]))?\\s+$upperIcao\\b[^=]*?(?==|$)", RegexOption.IGNORE_CASE),
        Regex("\\b$upperIcao\\s+\\d{6}Z(?!\\s+\\d{4}/\\d{4}\\b)\\s+(?:AUTO\\s+|COR\\s+)?(?:(?:\\d{3}|VRB|///)\\d{2,3}(?:G\\d{2,3})?(?:KT|MPS|KMH)|/////(?:KT|MPS|KMH)?)\\b[^=\"]*?$metarBoundaryLookahead", RegexOption.IGNORE_CASE),
        Regex("\\b$upperIcao\\s+\\d{6}Z(?!\\s+\\d{4}/\\d{4}\\b)\\s+(?:CAVOK|\\d{4}|////)\\b[^=\"]*?$metarBoundaryLookahead", RegexOption.IGNORE_CASE),
      )

      val foundMetars = linkedSetOf<String>()
      for (pattern in metarPatterns) {
        pattern.findAll(plain).forEach { m ->
          var line = m.value.trim()
            .replace(Regex("\\s+"), " ")
            .trimEnd('=', '"', ',')
            .trim()
          val tafIdx = line.indexOf(" TAF ", ignoreCase = true)
          if (tafIdx > 0) line = line.substring(0, tafIdx).trim()
          if (!line.startsWith("METAR", ignoreCase = true) && !line.startsWith("SPECI", ignoreCase = true)) {
            line = "METAR $line"
          }
          if (line.isNotBlank()) foundMetars.add(line)
        }
        if (foundMetars.isNotEmpty()) break
      }

      val tafBoundaryLookahead = "(?==|\"|\\r?\\n\\s*(?:METAR|SPECI|TAF(?:\\s|$)|[A-Z]{4}\\s+\\d{6}Z|NNNN|\\r?\\n)|$)"
      val tafPatterns = listOf(
        Regex("\\bTAF(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))?\\s+$upperIcao\\s+\\d{6}Z[^=\"]*?$tafBoundaryLookahead", RegexOption.IGNORE_CASE),
        Regex("(?m)^\\s*TAF(?:\\s+(?:AMD|COR|RTD|CC[A-Z]))?\\s+$upperIcao\\b[^=]*?(?==|$)", RegexOption.IGNORE_CASE),
        // Collective FT bulletins list each station on its own line with DDHHMMZ DDHH/DDHH without repeating "TAF"
        Regex("\\b$upperIcao\\s+\\d{6}Z\\s+\\d{4}/\\d{4}\\b[^=\"]*?$tafBoundaryLookahead", RegexOption.IGNORE_CASE),
      )

      val foundTafs = linkedSetOf<String>()
      for (tafPattern in tafPatterns) {
        tafPattern.findAll(plain).forEach { tm ->
          var line = tm.value.trim()
            .replace(Regex("\\s+"), " ")
            .trimEnd('=', '"', ',')
            .trim()
          if (!line.startsWith("TAF", ignoreCase = true)) {
            line = "TAF $line"
          }
          if (line.isNotBlank()) foundTafs.add(line)
        }
        if (foundTafs.isNotEmpty()) break
      }

      return foundMetars.toList() to foundTafs.toList()
    }

    private fun extractAllFromMoshiJson(
      jsonText: String,
      icao: String,
      moshi: Moshi,
    ): Pair<List<String>, List<String>> {
      val upper = icao.trim().uppercase()
      return try {
        if (jsonText.startsWith("[")) {
          val listType = Types.newParameterizedType(List::class.java, ServerStationWeatherDto::class.java)
          val adapter = moshi.adapter<List<ServerStationWeatherDto>>(listType)
          val list = adapter.fromJson(jsonText).orEmpty()
          val matches = list.filter { it.resolvedIcao() == upper || (it.resolvedIcao() == null && it.resolvedMetar() != null) }
          val metars = matches.mapNotNull { it.resolvedMetar() }.distinct()
          val tafs = matches.mapNotNull { it.resolvedTaf() }.distinct()
          metars to tafs
        } else {
          val (singleM, singleT) = extractFromMoshiJson(jsonText, icao, moshi)
          listOfNotNull(singleM) to listOfNotNull(singleT)
        }
      } catch (_: Exception) {
        emptyList<String>() to emptyList()
      }
    }

    private fun extractFromMoshiJson(
      jsonText: String,
      icao: String,
      moshi: Moshi,
    ): Pair<String?, String?> {
      val upper = icao.trim().uppercase()
      return try {
        if (jsonText.startsWith("[")) {
          val listType = Types.newParameterizedType(List::class.java, ServerStationWeatherDto::class.java)
          val adapter = moshi.adapter<List<ServerStationWeatherDto>>(listType)
          val list = adapter.fromJson(jsonText).orEmpty()
          val match = list.firstOrNull { it.resolvedIcao() == upper }
            ?: list.firstOrNull { it.resolvedIcao() == null && it.resolvedMetar() != null }
          match?.resolvedMetar() to match?.resolvedTaf()
        } else {
          val envelopeAdapter = moshi.adapter(ServerWeatherEnvelopeDto::class.java)
          val envelope = envelopeAdapter.fromJson(jsonText)
          val dto = envelope?.findForStation(upper)
          if (dto != null) {
            dto.resolvedMetar() to dto.resolvedTaf()
          } else {
            val single = moshi.adapter(ServerStationWeatherDto::class.java).fromJson(jsonText)
            if (single != null && (single.resolvedIcao() == null || single.resolvedIcao() == upper)) {
              single.resolvedMetar() to single.resolvedTaf()
            } else {
              null to null
            }
          }
        }
      } catch (_: Exception) {
        null to null
      }
    }

    fun stripHtml(html: String): String {
      return html
        .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<(?:br|p|div|tr|li|h[1-6]|pre|/p|/div|/tr|/li|/pre)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<td[^>]*>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
    }

    private fun extractAttr(attrs: String, attrName: String): String? {
      val dq = Regex("\\b$attrName\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
      if (dq != null) return dq
      val sq = Regex("\\b$attrName\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
      if (sq != null) return sq
      return Regex("\\b$attrName\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
    }
  }
}
