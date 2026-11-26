package com.example.data

const val MAX_RECENT_SEARCHES = 5

interface RecentSearchesStorage {
  fun loadRecentSearches(): List<String>
  fun saveRecentSearches(icaos: List<String>)
}

object RecentSearchSerializer {

  fun normalizeIcaos(
    icaos: List<String>,
    maxCount: Int = MAX_RECENT_SEARCHES,
  ): List<String> {
    return icaos
      .map { it.trim().uppercase() }
      .filter { it.length == 4 && it.all { ch -> ch.isLetterOrDigit() } }
      .distinct()
      .take(maxCount.coerceAtLeast(1))
  }

  fun recordQuery(
    existing: List<String>,
    icaoInput: String,
    maxCount: Int = MAX_RECENT_SEARCHES,
  ): List<String> {
    val clean = icaoInput.trim().uppercase()
    if (clean.length != 4 || !clean.all { it.isLetterOrDigit() }) {
      return normalizeIcaos(existing, maxCount)
    }
    val combined = listOf(clean) + existing
    return normalizeIcaos(combined, maxCount)
  }
}

class InMemoryRecentSearchesStorage(
  initial: List<String> = emptyList(),
) : RecentSearchesStorage {
  private var items: List<String> = RecentSearchSerializer.normalizeIcaos(initial)

  override fun loadRecentSearches(): List<String> = items

  override fun saveRecentSearches(icaos: List<String>) {
    items = RecentSearchSerializer.normalizeIcaos(icaos)
  }
}
