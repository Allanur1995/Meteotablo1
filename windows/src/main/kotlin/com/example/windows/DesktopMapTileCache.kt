package com.example.windows

import com.example.domain.MapLayerStyle
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import okhttp3.OkHttpClient
import okhttp3.Request

object DesktopMapTileCache {

  private const val MAX_MEMORY_TILES = 180
  private const val FAILURE_COOLDOWN_MS = 30_000L

  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(7, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

  private val memoryCache: MutableMap<String, BufferedImage> = Collections.synchronizedMap(
    object : LinkedHashMap<String, BufferedImage>(MAX_MEMORY_TILES, 0.75f, true) {
      override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>?): Boolean {
        return size > MAX_MEMORY_TILES
      }
    },
  )

  private val inFlight = ConcurrentHashMap.newKeySet<String>()
  private val recentFailures = ConcurrentHashMap<String, Long>()
  private val executor = Executors.newFixedThreadPool(3) { r ->
    Thread(r, "MeteotabloMapTile").apply { isDaemon = true }
  }

  private val cacheRoot: File by lazy {
    File(System.getProperty("user.home"), ".meteotablo/tile_cache").apply { mkdirs() }
  }

  fun getOrFetch(
    style: MapLayerStyle,
    z: Int,
    x: Int,
    y: Int,
    onLoaded: () -> Unit,
  ): BufferedImage? {
    val key = "${style.id}_${z}_${x}_${y}"
    synchronized(memoryCache) {
      memoryCache[key]?.let { return it }
    }

    val lastFail = recentFailures[key]
    if (lastFail != null && System.currentTimeMillis() - lastFail < FAILURE_COOLDOWN_MS) {
      return null
    }

    if (inFlight.add(key)) {
      executor.submit {
        try {
          val img = loadFromDiskOrNetwork(style, z, x, y)
          if (img != null) {
            synchronized(memoryCache) {
              memoryCache[key] = img
            }
            recentFailures.remove(key)
            SwingUtilities.invokeLater { onLoaded() }
          } else {
            recentFailures[key] = System.currentTimeMillis()
          }
        } catch (_: Exception) {
          recentFailures[key] = System.currentTimeMillis()
        } finally {
          inFlight.remove(key)
        }
      }
    }
    return null
  }

  private fun loadFromDiskOrNetwork(
    style: MapLayerStyle,
    z: Int,
    x: Int,
    y: Int,
  ): BufferedImage? {
    val styleDir = File(cacheRoot, style.id).apply { mkdirs() }
    val tileFile = File(styleDir, "${z}_${x}_${y}.png")
    if (tileFile.exists() && tileFile.length() > 100L) {
      val cached = runCatching { ImageIO.read(tileFile) }.getOrNull()
      if (cached != null) return cached
    }

    val url = style.tileUrl(z, x, y)
    val req = Request.Builder()
      .url(url)
      .header("User-Agent", "Meteotablo/1.0 (Aviation Weather Viewer)")
      .header("Accept", "image/png,image/jpeg,image/*")
      .build()

    client.newCall(req).execute().use { resp ->
      if (!resp.isSuccessful) return null
      val bytes = resp.body?.bytes() ?: return null
      if (bytes.size < 100) return null
      val decoded = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
      runCatching { tileFile.writeBytes(bytes) }
      return decoded
    }
  }
}
