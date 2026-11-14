package com.example.data

import com.example.network.MeteoServerApi
import com.example.network.MeteoServerRetrofitFactory
import com.example.network.ServerStationWeatherDto
import com.example.network.ServerWeatherEnvelopeDto
import com.example.network.SessionCookieJar
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

  data class CustomFetchOutcome(
    val rawMetar: String?,
    val rawTaf: String?,
    val sourceHost: String,
    val logs: List<String>,
    val errorMessage: String? = null,
  )

  private val cookieJar = SessionCookieJar()
  private val moshi: Moshi = MeteoServerRetrofitFactory.createMoshi()

  suspend fun fetchMetarAndTaf(icao: String, config: ServerConfig): CustomFetchOutcome {
    val logs = mutableListOf<String>()
    val cleanIcao = icao.trim().uppercase()
    val baseUrlStr = SharedPreferencesServerConfigStorage.normalizeServerUrl(config.url)
    val baseHttpUrl = baseUrlStr.toHttpUrlOrNull()
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
      val api = MeteoServerRetrofitFactory.create(
        config = config,
        cookieJar = cookieJar,
        moshi = moshi,
      )

      logs.add("Retrofit GET $baseUrlStr (логин: ${config.username.ifBlank { "нет" }})")
      val firstResp = api.getRootPage()
      var currentUrl = firstResp.raw().request.url
      var currentBody = readBody(firstResp)
      var statusCode = firstResp.code()
      logs.add("HTTP $statusCode <- $currentUrl (${currentBody.length} байт)")

      if (statusCode == 401 || statusCode == 403) {
        return CustomFetchOutcome(
          rawMetar = null,
          rawTaf = null,
          sourceHost = hostLabel,
          logs = logs,
          errorMessage = "Сервер $hostLabel отклонил авторизацию (HTTP $statusCode)",
        )
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

          if (isLoginFormHtml(currentBody)) {
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

      // 1. Check landing page with Moshi DTO + HTML/text extraction
      var (metar, taf) = extractMetarAndTaf(currentBody, cleanIcao, moshi)

      // 2. If landing page did not include this station directly, try typed Retrofit JSON endpoints
      if (metar.isNullOrBlank()) {
        val dtoResult = tryTypedRetrofitEndpoints(api, cleanIcao, logs)
        if (!dtoResult.first.isNullOrBlank()) {
          metar = dtoResult.first
          if (taf.isNullOrBlank()) taf = dtoResult.second
        }
      }

      // 3. Fallback to station links discovered on the page or standard query URLs via Retrofit
      if (metar.isNullOrBlank()) {
        val candidateUrls = buildList {
          addAll(extractStationLinks(currentBody, cleanIcao, currentUrl))
          add("${baseUrlStr}?icao=$cleanIcao")
          add("${baseUrlStr}?station=$cleanIcao")
          add("${baseUrlStr}metar/$cleanIcao")
          add("${baseUrlStr}$cleanIcao.txt")
        }.distinct().take(6)

        for (candidate in candidateUrls) {
          if (candidate == currentUrl.toString()) continue
          logs.add("Retrofit GET $candidate")
          val subResp = runCatching { api.fetchPage(candidate) }.getOrNull() ?: continue
          val subStatus = subResp.code()
          val subBody = readBody(subResp)
          logs.add("HTTP $subStatus <- $candidate (${subBody.length} байт)")
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

      if (metar.isNullOrBlank()) {
        val snippet = stripHtml(currentBody).replace(Regex("\\s+"), " ").take(140)
        logs.add("METAR для $cleanIcao не найден на $hostLabel. Фрагмент: $snippet")
        CustomFetchOutcome(
          rawMetar = null,
          rawTaf = taf,
          sourceHost = hostLabel,
          logs = logs,
          errorMessage = "На сервере $hostLabel нет METAR для $cleanIcao",
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
    return if (response.isSuccessful) {
      response.body()?.string().orEmpty()
    } else {
      response.errorBody()?.string().orEmpty()
    }
  }

  companion object {

    fun isLoginFormHtml(html: String): Boolean {
      if (!html.contains("<form", ignoreCase = true)) return false
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
          lower.contains("вход") ||
          lower.contains("авторизац"))
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
          type == "submit" || type == "button" -> {
            fields.add(name to value)
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

      if (!usernameAssigned) fields.add("username" to username)
      if (!passwordAssigned) fields.add("password" to password)

      return ParsedLoginForm(
        actionUrl = resolvedAction,
        method = method.lowercase(),
        fields = fields,
      )
    }

    fun extractStationLinks(
      html: String,
      icao: String,
      baseUrl: HttpUrl,
    ): List<String> {
      val urls = mutableListOf<String>()
      val hrefRegex = Regex("(?:href|value)\\s*=\\s*[\"']([^\"'#]+)[\"']", RegexOption.IGNORE_CASE)
      for (match in hrefRegex.findAll(html)) {
        val candidate = match.groupValues[1].trim()
        if (candidate.contains(icao, ignoreCase = true) ||
          candidate.contains("metar", ignoreCase = true) ||
          candidate.contains("weather", ignoreCase = true) ||
          candidate.contains("svodka", ignoreCase = true)
        ) {
          val resolved = baseUrl.resolve(candidate)?.toString()
          if (resolved != null && !resolved.endsWith(".css", true) && !resolved.endsWith(".js", true)) {
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
      val trimmed = rawBody.trim()
      if (trimmed.isEmpty()) return null to null

      // 1. Try Moshi structured DTOs first when JSON is returned
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        val jsonResult = extractFromMoshiJson(trimmed, icao, moshi)
        if (jsonResult.first != null || jsonResult.second != null) {
          return jsonResult
        }
      }

      // 2. HTML / Plaintext extraction
      val plain = stripHtml(trimmed)
      val upperIcao = Regex.escape(icao.trim().uppercase())

      val metarPatterns = listOf(
        Regex("\\b(?:METAR|SPECI)(?:\\s+COR)?\\s+$upperIcao\\s+\\d{6}Z\\b[^=\\r\\n\"]*", RegexOption.IGNORE_CASE),
        Regex("\\b$upperIcao\\s+\\d{6}Z\\s+(?:AUTO\\s+)?(?:\\d{3}|VRB)\\d{2,3}(?:G\\d{2,3})?(?:KT|MPS)\\b[^=\\r\\n\"]*", RegexOption.IGNORE_CASE),
      )

      var foundMetar: String? = null
      for (pattern in metarPatterns) {
        val m = pattern.find(plain)
        if (m != null) {
          var line = m.value.trim()
            .replace(Regex("\\s+"), " ")
            .trimEnd('=', '"', ',')
            .trim()
          val tafIdx = line.indexOf(" TAF ", ignoreCase = true)
          if (tafIdx > 0) line = line.substring(0, tafIdx).trim()
          if (!line.startsWith("METAR", ignoreCase = true) && !line.startsWith("SPECI", ignoreCase = true)) {
            line = "METAR $line"
          }
          foundMetar = line
          break
        }
      }

      val tafRegex = Regex(
        "\\bTAF(?:\\s+(?:AMD|COR))?\\s+$upperIcao\\s+\\d{6}Z[^=\"]*?(?==|\"|\\r?\\n\\s*(?:METAR|SPECI|TAF\\s+[A-Z]{4}|[A-Z]{4}\\s+\\d{6}Z|\\r?\\n)|$)",
        RegexOption.IGNORE_CASE,
      )
      val foundTaf = tafRegex.find(plain)?.value
        ?.trim()
        ?.replace(Regex("\\s+"), " ")
        ?.trimEnd('=', '"', ',')
        ?.trim()

      return foundMetar to foundTaf
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
