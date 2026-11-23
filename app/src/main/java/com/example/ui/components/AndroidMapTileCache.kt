package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.domain.MapLayerStyle
import com.example.domain.TilePlacement
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object AndroidMapTileLoader {
  private val memoryCache = object : LruCache<String, ImageBitmap>(180) {}
  private val inFlight: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
  private val client = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

  fun getCached(key: String): ImageBitmap? = memoryCache.get(key)

  suspend fun loadTile(
    context: Context,
    tile: TilePlacement,
    style: MapLayerStyle,
  ): ImageBitmap? {
    val key = tile.cacheKey(style)
    memoryCache.get(key)?.let { return it }
    if (!inFlight.add(key)) return null

    return try {
      withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "map_tiles/${style.id}")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val diskFile = File(cacheDir, "${tile.z}_${tile.x}_${tile.y}.png")

        if (diskFile.exists() && diskFile.length() > 100L) {
          val bytes = runCatching { diskFile.readBytes() }.getOrNull()
          val decoded = bytes?.let { decodeImageBitmap(it) }
          if (decoded != null) {
            memoryCache.put(key, decoded)
            return@withContext decoded
          }
        }

        val url = style.tileUrl(tile.z, tile.x, tile.y)
        val request = Request.Builder()
          .url(url)
          .header("User-Agent", "Meteotablo/1.0 (Aviation Weather Viewer)")
          .header("Accept", "image/png,image/*")
          .build()

        runCatching {
          client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val bytes = resp.body?.bytes() ?: return@use null
            if (bytes.size < 100) return@use null
            val decoded = decodeImageBitmap(bytes) ?: return@use null
            runCatching { diskFile.writeBytes(bytes) }
            memoryCache.put(key, decoded)
            decoded
          }
        }.getOrNull()
      }
    } finally {
      inFlight.remove(key)
    }
  }

  private fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bmp.asImageBitmap()
  }
}

@Composable
fun rememberMapTileSnapshot(
  context: Context,
  tiles: List<TilePlacement>,
  style: MapLayerStyle,
): Int {
  var revision by remember { mutableIntStateOf(0) }
  val tileSignature = remember(tiles, style) {
    style.id + ":" + tiles.joinToString(",") { "${it.z}/${it.x}/${it.y}" }
  }

  LaunchedEffect(tileSignature) {
    val missing = tiles.filter { AndroidMapTileLoader.getCached(it.cacheKey(style)) == null }
    if (missing.isEmpty()) return@LaunchedEffect

    coroutineScope {
      missing.take(24).map { tile ->
        async {
          val loaded = AndroidMapTileLoader.loadTile(context, tile, style)
          if (loaded != null) {
            revision++
          }
        }
      }.awaitAll()
    }
  }
  return revision
}
