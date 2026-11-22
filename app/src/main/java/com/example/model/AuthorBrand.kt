package com.example.model

object AuthorBrand {
  const val APP_TITLE = "Meteotablo"
  const val SUBTITLE = "Aviation Weather Display"
  const val APP_VERSION = "1.0.0"
  const val AUTHOR_NAME = "Allanur Saparow"
  const val AUTHOR_HANDLE = "@Allanur1995"
  const val AUTHOR_EMAIL = "allanur.saparow9509@gmail.com"
  const val AUTHOR_URL = "https://github.com/Allanur1995"
  const val COPYRIGHT_YEAR = "2026"
  const val COPYRIGHT_LINE = "© $COPYRIGHT_YEAR $AUTHOR_NAME ($AUTHOR_HANDLE)"
  const val ANDROID_PACKAGE_ID = "com.aistudio.meteotablo.kxmqzp"
  const val OWNERSHIP_NOTICE =
    "Разработчик и правообладатель: $AUTHOR_NAME ($AUTHOR_HANDLE). " +
      "Несанкционированное переименование, удаление сведений об авторе и распространение под чужим именем запрещены."

  fun isExpectedBrand(runtimeTitle: String, runtimePackageId: String? = null): Boolean {
    val titleMatches = runtimeTitle.trim().equals(APP_TITLE, ignoreCase = true)
    val packageMatches = runtimePackageId == null || runtimePackageId == ANDROID_PACKAGE_ID
    return titleMatches && packageMatches
  }
}
