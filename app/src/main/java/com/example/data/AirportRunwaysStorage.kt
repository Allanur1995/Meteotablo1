package com.example.data

import com.example.model.RunwayPair
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

interface AirportRunwaysStorage {
  fun loadAll(): Map<String, List<RunwayPair>>
  fun saveForAirport(icao: String, runways: List<RunwayPair>)
  fun clearForAirport(icao: String)
}

class InMemoryAirportRunwaysStorage : AirportRunwaysStorage {
  private val data = LinkedHashMap<String, List<RunwayPair>>()

  override fun loadAll(): Map<String, List<RunwayPair>> = data.toMap()

  override fun saveForAirport(icao: String, runways: List<RunwayPair>) {
    val clean = icao.trim().uppercase()
    if (clean.length == 4 && runways.isNotEmpty()) {
      data[clean] = runways.toList()
    }
  }

  override fun clearForAirport(icao: String) {
    data.remove(icao.trim().uppercase())
  }
}

object AirportRunwaysSerializer {

  fun normalizeHeading(headingDeg: Int): Int {
    val mod = ((headingDeg % 360) + 360) % 360
    return if (mod == 0) 360 else mod
  }

  fun sanitizeDesignator(raw: String, fallbackHeadingDeg: Int): String {
    val cleaned = raw.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9]"), "").take(4)
    if (cleaned.isNotBlank()) return cleaned
    val rwyNum = ((normalizeHeading(fallbackHeadingDeg) + 5) / 10).let { if (it <= 0) 36 else if (it > 36) it - 36 else it }
    return String.format(Locale.US, "%02d", rwyNum)
  }

  fun inferReciprocalDesignator(primaryDesignator: String, reciprocalHeadingDeg: Int): String {
    val clean = primaryDesignator.trim().uppercase(Locale.US)
    val match = Regex("^(\\d{1,2})([LCR]?)$").find(clean)
    if (match != null) {
      val num = match.groupValues[1].toIntOrNull()
      val suffix = match.groupValues[2]
      if (num != null && num in 1..36) {
        val revNum = ((num + 17) % 36) + 1
        val revSuffix = when (suffix) {
          "L" -> "R"
          "R" -> "L"
          "C" -> "C"
          else -> ""
        }
        return String.format(Locale.US, "%02d%s", revNum, revSuffix)
      }
    }
    val revByHdg = ((normalizeHeading(reciprocalHeadingDeg) + 5) / 10).let { if (it <= 0) 36 else if (it > 36) it - 36 else it }
    return String.format(Locale.US, "%02d", revByHdg)
  }

  fun buildRunwayPair(
    primaryDesignatorInput: String,
    primaryMagneticHeadingDeg: Int,
    reciprocalDesignatorInput: String? = null,
    reciprocalMagneticHeadingDegInput: Int? = null,
  ): RunwayPair {
    val primaryHeading = normalizeHeading(primaryMagneticHeadingDeg)
    val reciprocalHeading = reciprocalMagneticHeadingDegInput?.let { normalizeHeading(it) }
      ?: normalizeHeading(primaryHeading + 180)
    val primaryDes = sanitizeDesignator(primaryDesignatorInput, primaryHeading)
    val reciprocalDes = reciprocalDesignatorInput
      ?.trim()
      ?.takeIf { it.isNotBlank() }
      ?.let { sanitizeDesignator(it, reciprocalHeading) }
      ?: inferReciprocalDesignator(primaryDes, reciprocalHeading)
    return RunwayPair(
      primaryDesignator = primaryDes,
      primaryHeadingDeg = primaryHeading,
      reciprocalDesignator = reciprocalDes,
      reciprocalHeadingDeg = reciprocalHeading,
    )
  }

  fun formatMagneticHeading(headingDeg: Int): String {
    val norm = normalizeHeading(headingDeg)
    return String.format(Locale.US, "%03d°", norm)
  }

  fun toJson(byIcao: Map<String, List<RunwayPair>>): String {
    val root = JSONObject()
    byIcao.forEach { (icao, pairs) ->
      val clean = icao.trim().uppercase(Locale.US)
      if (clean.length == 4 && pairs.isNotEmpty()) {
        val arr = JSONArray()
        pairs.forEach { p ->
          arr.put(
            JSONObject().apply {
              put("pDes", p.primaryDesignator)
              put("pHdg", normalizeHeading(p.primaryHeadingDeg))
              put("rDes", p.reciprocalDesignator)
              put("rHdg", normalizeHeading(p.reciprocalHeadingDeg))
            },
          )
        }
        root.put(clean, arr)
      }
    }
    return root.toString()
  }

  fun fromJson(raw: String?): Map<String, List<RunwayPair>> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
      val root = JSONObject(raw)
      val out = LinkedHashMap<String, List<RunwayPair>>()
      val keys = root.keys()
      while (keys.hasNext()) {
        val icao = keys.next().trim().uppercase(Locale.US)
        if (icao.length != 4) continue
        val arr = root.optJSONArray(icao) ?: continue
        val list = mutableListOf<RunwayPair>()
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          val pHdg = obj.optInt("pHdg", -1)
          val rHdg = obj.optInt("rHdg", -1)
          if (pHdg !in 1..360 || rHdg !in 1..360) continue
          val pDes = obj.optString("pDes", "").trim()
          val rDes = obj.optString("rDes", "").trim()
          if (pDes.isBlank() || rDes.isBlank()) continue
          list.add(
            RunwayPair(
              primaryDesignator = pDes,
              primaryHeadingDeg = normalizeHeading(pHdg),
              reciprocalDesignator = rDes,
              reciprocalHeadingDeg = normalizeHeading(rHdg),
            ),
          )
        }
        if (list.isNotEmpty()) {
          out[icao] = list
        }
      }
      out
    }.getOrDefault(emptyMap())
  }
}
