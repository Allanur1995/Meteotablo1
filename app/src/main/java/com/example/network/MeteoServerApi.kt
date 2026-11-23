package com.example.network

import com.example.data.ServerConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface MeteoServerApi {

  @GET(".")
  suspend fun getRootPage(): Response<ResponseBody>

  @GET
  suspend fun fetchPage(@Url url: String): Response<ResponseBody>

  @GET
  suspend fun fetchPageWithQuery(
    @Url url: String,
    @QueryMap query: Map<String, String>,
  ): Response<ResponseBody>

  @FormUrlEncoded
  @POST
  suspend fun submitLoginForm(
    @Url url: String,
    @FieldMap fields: Map<String, String>,
  ): Response<ResponseBody>

  @POST("login")
  suspend fun loginJson(@Body request: MeteoLoginRequest): Response<MeteoLoginResponse>

  @GET("api/weather")
  suspend fun getStationWeather(@Query("icao") icao: String): Response<ServerWeatherEnvelopeDto>

  @GET("api/metar")
  suspend fun getMetarList(@Query("ids") icao: String): Response<List<ServerStationWeatherDto>>
}

class SessionCookieJar : CookieJar {
  private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

  override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
    val list = store.getOrPut(url.host) { mutableListOf() }
    synchronized(list) {
      cookies.forEach { newCookie ->
        list.removeAll { it.name == newCookie.name && it.path == newCookie.path }
        if (newCookie.expiresAt >= System.currentTimeMillis()) {
          list.add(newCookie)
        }
      }
    }
  }

  override fun loadForRequest(url: HttpUrl): List<Cookie> {
    val list = store[url.host] ?: return emptyList()
    val now = System.currentTimeMillis()
    synchronized(list) {
      list.removeAll { it.expiresAt < now }
      return list.filter { it.matches(url) }
    }
  }
}

class BasicAuthInterceptor(
  private val username: String,
  private val password: String,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
    val original = chain.request()
    val builder = original.newBuilder()
    if (original.header("User-Agent") == null) {
      builder.header("User-Agent", "Meteotablo/1.0")
    }
    if (username.isNotBlank() && original.header("Authorization") == null) {
      builder.header("Authorization", Credentials.basic(username, password))
    }
    return chain.proceed(builder.build())
  }
}

object MeteoServerRetrofitFactory {

  fun createMoshi(): Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  fun createOkHttpClient(
    config: ServerConfig,
    cookieJar: CookieJar = SessionCookieJar(),
  ): OkHttpClient {
    val logging = HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.BASIC
    }
    return OkHttpClient.Builder()
      .cookieJar(cookieJar)
      .addInterceptor(BasicAuthInterceptor(config.username, config.password))
      .addInterceptor(logging)
      .followRedirects(true)
      .followSslRedirects(true)
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(20, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  fun create(
    config: ServerConfig = ServerConfig(),
    cookieJar: CookieJar = SessionCookieJar(),
    moshi: Moshi = createMoshi(),
  ): MeteoServerApi {
    val baseUrl = ServerConfig.retrofitBaseUrl(config.url)
    val client = createOkHttpClient(config, cookieJar)
    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(client)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(MeteoServerApi::class.java)
  }
}
