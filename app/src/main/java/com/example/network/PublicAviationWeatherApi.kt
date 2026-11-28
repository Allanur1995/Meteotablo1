package com.example.network

import com.squareup.moshi.Json
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

data class PublicMetarDto(
  @Json(name = "icaoId") val icaoId: String? = null,
  @Json(name = "rawOb") val rawOb: String? = null,
  @Json(name = "lat") val lat: Double? = null,
  @Json(name = "lon") val lon: Double? = null,
  @Json(name = "elev") val elev: Int? = null,
  @Json(name = "name") val name: String? = null,
  @Json(name = "obsTime") val obsTime: Long? = null,
)

data class PublicTafDto(
  @Json(name = "icaoId") val icaoId: String? = null,
  @Json(name = "rawTAF") val rawTaf: String? = null,
  @Json(name = "issueTime") val issueTime: Long? = null,
)

/**
 * Retrofit service interface for fetching live and historical aviation weather data (METAR / TAF)
 * from public APIs (aviationweather.gov JSON endpoints and NOAA/VATSIM plain-text feeds).
 */
interface PublicAviationWeatherApi {

  @GET("api/data/metar")
  suspend fun getMetars(
    @Query("ids") ids: String,
    @Query("format") format: String = "json",
    @Query("hours") hours: Int? = null,
  ): Response<List<PublicMetarDto>>

  @GET("api/data/metar")
  suspend fun getMetarsByBoundingBox(
    @Query("bbox") bbox: String,
    @Query("format") format: String = "json",
  ): Response<List<PublicMetarDto>>

  @GET("api/data/taf")
  suspend fun getTafs(
    @Query("ids") ids: String,
    @Query("format") format: String = "json",
    @Query("hours") hours: Int? = null,
  ): Response<List<PublicTafDto>>

  @GET("api/data/isigmet")
  suspend fun getISigmets(
    @Query("format") format: String = "json",
  ): Response<ResponseBody>

  @GET("api/data/airsigmet")
  suspend fun getAirSigmets(
    @Query("format") format: String = "json",
  ): Response<ResponseBody>

  @GET
  suspend fun fetchRawTextFeed(
    @Url fullUrl: String,
  ): Response<ResponseBody>
}
