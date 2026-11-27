package com.example.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedWeatherDao {
  @Query("SELECT * FROM cached_weather WHERE icao = :icao LIMIT 1")
  suspend fun getByIcao(icao: String): CachedWeatherEntity?

  @Query("SELECT * FROM cached_weather ORDER BY cached_at_millis DESC")
  suspend fun getAll(): List<CachedWeatherEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: CachedWeatherEntity)

  @Query("DELETE FROM cached_weather")
  suspend fun clearAll()
}
