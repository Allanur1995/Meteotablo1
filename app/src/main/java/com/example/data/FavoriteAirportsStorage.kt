package com.example.data

val DEFAULT_FAVORITE_ICAOS = listOf(
  "UTAM",
  "UTAA",
  "UTAK",
  "UTAN",
  "UTAT",
  "UTAV",
  "UTAE",
)

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
