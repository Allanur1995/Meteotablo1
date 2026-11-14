package com.example.data

import android.content.Context

interface FavoriteAirportsStorage {
  fun loadFavorites(): List<String>
  fun saveFavorites(icaos: List<String>)
}

class InMemoryFavoriteStorage(
  initial: List<String> = DEFAULT_FAVORITE_ICAOS,
) : FavoriteAirportsStorage {
  private var stored: List<String> = initial

  override fun loadFavorites(): List<String> = stored

  override fun saveFavorites(icaos: List<String>) {
    stored = icaos
  }
}

private val DEFAULT_FAVORITE_ICAOS = listOf(
  "UTAM",
  "UTAA",
  "UTAK",
  "UTAN",
  "UTAT",
  "UTAV",
  "UTAE",
)

class SharedPreferencesFavoriteStorage(
  context: Context,
) : FavoriteAirportsStorage {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  override fun loadFavorites(): List<String> {
    if (!prefs.contains(KEY_FAVORITES)) {
      saveFavorites(DEFAULT_FAVORITE_ICAOS)
      return DEFAULT_FAVORITE_ICAOS
    }
    val raw = prefs.getString(KEY_FAVORITES, "").orEmpty()
    return raw
      .split(",")
      .map { it.trim().uppercase() }
      .filter { it.length == 4 }
      .distinct()
  }

  override fun saveFavorites(icaos: List<String>) {
    val clean = icaos
      .map { it.trim().uppercase() }
      .filter { it.length == 4 }
      .distinct()
    prefs.edit().putString(KEY_FAVORITES, clean.joinToString(",")).apply()
  }

  private companion object {
    const val PREFS_NAME = "meteotablo_prefs"
    const val KEY_FAVORITES = "favorite_icao_codes"
  }
}
