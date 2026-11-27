package com.example.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [CachedWeatherEntity::class],
  version = 1,
  exportSchema = false,
)
abstract class MeteoDatabase : RoomDatabase() {
  abstract fun cachedWeatherDao(): CachedWeatherDao

  companion object {
    @Volatile
    private var instance: MeteoDatabase? = null

    fun getInstance(context: Context): MeteoDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          MeteoDatabase::class.java,
          "meteotablo_cache.db",
        ).build().also { instance = it }
      }
    }
  }
}
