package com.example.windows

import com.example.data.RadarSourceMode
import com.example.data.RadarTimelineSnapshot
import com.example.data.WeatherRadarRepository
import com.example.domain.AirportMapProjection
import com.example.domain.MapLayerStyle
import com.example.domain.MapRegionPreset
import com.example.domain.MapViewportCamera
import com.example.model.AirportStation
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Path2D
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs

class WeatherRadarDesktopPanel(
  private val onBackToBoard: () -> Unit,
  private val onSelectStationOnBoard: (String) -> Unit,
) : JPanel(BorderLayout(8, 8)) {

  private val radarRepo = WeatherRadarRepository()
  private val bgExecutor = Executors.newSingleThreadExecutor { r ->
    Thread(r, "MeteotabloRadarMeta").apply { isDaemon = true }
  }
  private val configFile = File(System.getProperty("user.home"), ".meteotablo/radar.properties")

  private var sourceMode: RadarSourceMode = RadarSourceMode.AUTO
  private var owmApiKey: String = ""
  private var timeline: RadarTimelineSnapshot? = null
  private var selectedFrameIndex: Int = 0
  private var isPlaying: Boolean = false
  private var isLoading: Boolean = false
  private var errorMessage: String? = null
  private var useDarkMap: Boolean = true
  private var currentState: DesktopUiState? = null

  private val topBar = JPanel(BorderLayout(8, 4))
  private val backBtn = JButton("← На табло")
  private val titleLbl = JLabel("Метеорадар осадков (без ключа: Авто / LibreWXR / NASA GIBS)")
  private val apiSourceBtn = JButton("Источник / API")
  private val layerToggleBtn = JButton("Тёмная карта")
  private val centerAirportBtn = JButton("Центр")
  private val refreshBtn = JButton("↻ Обновить")
  private val statusLbl = JLabel("Загрузка кадров радара…")

  private val canvas = RadarMapCanvas { icao -> onSelectStationOnBoard(icao) }
  private val controlsBar = JPanel(BorderLayout(8, 4))
  private val playBtn = JButton("▶ Старт")
  private val frameSlider = JSlider(0, 0, 0)
  private val frameTimeLbl = JLabel("--:--Z")

  private val animTimer = Timer(750) {
    val frames = timeline?.frames ?: return@Timer
    if (frames.isEmpty()) return@Timer
    selectedFrameIndex = (selectedFrameIndex + 1) % frames.size
    updateTimelineUi()
  }

  init {
    loadSavedRadarSettings()
    border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

    val leftHdr = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
      isOpaque = false
      titleLbl.font = Font("Segoe UI", Font.BOLD, 14)
      add(backBtn)
      add(titleLbl)
    }
    val rightHdr = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2)).apply {
      isOpaque = false
      add(apiSourceBtn)
      add(layerToggleBtn)
      add(centerAirportBtn)
      add(refreshBtn)
    }
    topBar.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    topBar.add(leftHdr, BorderLayout.WEST)
    topBar.add(rightHdr, BorderLayout.EAST)

    statusLbl.font = Font("Segoe UI", Font.PLAIN, 11)
    statusLbl.border = BorderFactory.createEmptyBorder(2, 6, 2, 6)

    val zoomControls = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 4)).apply {
      isOpaque = false
      add(JButton("+").apply { addActionListener { canvas.adjustZoom(0.7) } })
      add(JButton("−").apply { addActionListener { canvas.adjustZoom(-0.7) } })
    }
    canvas.layout = BorderLayout()
    canvas.add(zoomControls, BorderLayout.NORTH)
    canvas.preferredSize = Dimension(680, 440)

    frameTimeLbl.font = Font("Consolas", Font.BOLD, 13)
    frameSlider.isOpaque = false
    controlsBar.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    controlsBar.add(playBtn, BorderLayout.WEST)
    controlsBar.add(frameSlider, BorderLayout.CENTER)
    controlsBar.add(frameTimeLbl, BorderLayout.EAST)

    val southPanel = JPanel(BorderLayout(4, 4)).apply {
      isOpaque = false
      add(controlsBar, BorderLayout.CENTER)
      add(statusLbl, BorderLayout.SOUTH)
    }

    add(topBar, BorderLayout.NORTH)
    add(canvas, BorderLayout.CENTER)
    add(southPanel, BorderLayout.SOUTH)

    wireActions()
  }

  private fun loadSavedRadarSettings() {
    runCatching {
      if (configFile.exists()) {
        val props = Properties()
        configFile.inputStream().use { props.load(it) }
        sourceMode = RadarSourceMode.fromId(props.getProperty("radar_source"))
        owmApiKey = props.getProperty("owm_api_key", "").orEmpty().trim()
      }
    }
  }

  private fun saveRadarSettings() {
    runCatching {
      configFile.parentFile?.mkdirs()
      val props = Properties().apply {
        setProperty("radar_source", sourceMode.id)
        setProperty("owm_api_key", owmApiKey)
      }
      configFile.outputStream().use { props.store(it, "Meteotablo Radar Settings") }
    }
  }

  private fun wireActions() {
    backBtn.addActionListener {
      setPlaying(false)
      onBackToBoard()
    }
    apiSourceBtn.addActionListener { showSourceAndApiDialog() }
    refreshBtn.addActionListener { loadTimeline() }
    layerToggleBtn.addActionListener {
      useDarkMap = !useDarkMap
      refreshUi()
    }
    centerAirportBtn.addActionListener {
      currentState?.station?.let { canvas.centerOn(it) }
    }
    playBtn.addActionListener { setPlaying(!isPlaying) }
    frameSlider.addChangeListener {
      val idx = frameSlider.value
      val frames = timeline?.frames.orEmpty()
      if (idx in frames.indices && idx != selectedFrameIndex) {
        selectedFrameIndex = idx
        updateTimelineUi()
      }
    }
  }

  private fun showSourceAndApiDialog() {
    val owner = SwingUtilities.getWindowAncestor(this)
    val pal = currentState?.palette ?: NightDesktopPalette
    val dialog = JDialog(owner, "Источник метеорадара и API")
    dialog.isModal = true
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(520, 290)
    dialog.setLocationRelativeTo(this)

    val root = JPanel(BorderLayout(10, 10)).apply {
      border = BorderFactory.createEmptyBorder(12, 14, 12, 14)
    }
    val infoLbl = JLabel().apply {
      putClientProperty(
        "htmlBody",
        "<b>API-ключ НЕ требуется</b> для режимов «Авто», «RainViewer (до Z7)», «LibreWXR» и «NASA GIBS».<br/>" +
          "Опциональный бесплатный ключ OpenWeatherMap можно получить на <i>openweathermap.org/api</i>.",
      )
    }
    val form = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }
    val sourceCombo = JComboBox(RadarSourceMode.entries.map { it.fullLabel }.toTypedArray()).apply {
      selectedIndex = sourceMode.ordinal
    }
    val keyField = JTextField(owmApiKey, 32)
    form.add(JLabel("Источник радиолокации осадков:"))
    form.add(sourceCombo)
    form.add(JLabel("OpenWeatherMap API Key (только для режима OpenWeatherMap):"))
    form.add(keyField)

    val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
      val saveBtn = JButton("Применить").apply {
        putClientProperty("accentStyled", true)
        addActionListener {
          sourceMode = RadarSourceMode.entries[sourceCombo.selectedIndex.coerceIn(0, RadarSourceMode.entries.lastIndex)]
          owmApiKey = keyField.text.trim()
          saveRadarSettings()
          dialog.dispose()
          loadTimeline()
        }
      }
      val cancelBtn = JButton("Закрыть").apply { addActionListener { dialog.dispose() } }
      add(cancelBtn)
      add(saveBtn)
    }

    root.add(infoLbl, BorderLayout.NORTH)
    root.add(form, BorderLayout.CENTER)
    root.add(buttons, BorderLayout.SOUTH)
    dialog.contentPane = root
    DesktopThemeHelper.applyThemeTree(dialog, pal)
    dialog.isVisible = true
  }

  private fun setPlaying(play: Boolean) {
    isPlaying = play
    if (play) {
      if (!animTimer.isRunning) animTimer.start()
    } else {
      if (animTimer.isRunning) animTimer.stop()
    }
    playBtn.text = if (isPlaying) "❚❚ Пауза" else "▶ Старт"
  }

  fun onShown(state: DesktopUiState) {
    applyState(state)
    if (timeline == null && !isLoading) {
      loadTimeline()
    }
  }

  fun loadTimeline() {
    if (isLoading) return
    isLoading = true
    errorMessage = null
    refreshUi()
    val targetSource = sourceMode
    val targetKey = owmApiKey
    bgExecutor.submit {
      val res = kotlinx.coroutines.runBlocking {
        radarRepo.fetchLatestTimeline(preferredSource = targetSource, owmApiKey = targetKey)
      }
      SwingUtilities.invokeLater {
        isLoading = false
        res.onSuccess { snap ->
          timeline = snap
          selectedFrameIndex = (snap.frames.lastIndex).coerceAtLeast(0)
          errorMessage = null
        }.onFailure { err ->
          errorMessage = err.message ?: "Ошибка загрузки данных радара"
        }
        updateTimelineUi()
        refreshUi()
      }
    }
  }

  fun applyState(state: DesktopUiState) {
    currentState = state
    useDarkMap = state.isNightMode
    refreshUi()
  }

  private fun updateTimelineUi() {
    val frames = timeline?.frames.orEmpty()
    val maxIdx = (frames.lastIndex).coerceAtLeast(0)
    if (frameSlider.maximum != maxIdx) {
      frameSlider.maximum = maxIdx
    }
    if (frameSlider.value != selectedFrameIndex.coerceIn(0, maxIdx)) {
      frameSlider.value = selectedFrameIndex.coerceIn(0, maxIdx)
    }
    val curFrame = frames.getOrNull(selectedFrameIndex)
    frameTimeLbl.text = if (curFrame != null) {
      curFrame.timeUtcLabel
    } else {
      "--:--Z"
    }
    canvas.updateFrame(curFrame)
  }

  private fun refreshUi() {
    val state = currentState ?: return
    val pal = state.palette
    background = pal.bezelSurface
    topBar.background = pal.panelSurface
    topBar.border = BorderFactory.createLineBorder(pal.border, 1)
    controlsBar.background = pal.panelSurface
    controlsBar.border = BorderFactory.createLineBorder(pal.border, 1)
    apiSourceBtn.text = "Источник: ${sourceMode.shortLabel}"
    layerToggleBtn.text = if (useDarkMap) "Тёмная карта" else "Светлая карта"
    refreshBtn.text = if (isLoading) "Загрузка…" else "↻ Обновить"
    refreshBtn.isEnabled = !isLoading
    centerAirportBtn.text = "Центр ${state.station.icao}"

    val frames = timeline?.frames.orEmpty()
    val curFrame = frames.getOrNull(selectedFrameIndex)
    val srcName = timeline?.sourceLabel ?: sourceMode.shortLabel
    val notice = timeline?.fallbackNotice
    statusLbl.text = when {
      errorMessage != null -> "⚠ $errorMessage"
      isLoading -> "Обновление снимков радара…"
      !notice.isNullOrBlank() && curFrame != null -> "$notice · Кадр ${selectedFrameIndex + 1}/${frames.size} (${curFrame.timeUtcLabel})"
      curFrame != null -> "Кадр ${selectedFrameIndex + 1}/${frames.size} · $srcName (${curFrame.timeUtcLabel}) · Нажмите на аэропорт для открытия"
      else -> "Нажмите «Обновить» для загрузки радиолокационной карты осадков"
    }

    canvas.updateData(
      stations = state.mapStations,
      activeStation = state.station,
      frame = curFrame,
      layerStyle = if (useDarkMap) MapLayerStyle.CARTO_DARK else MapLayerStyle.CARTO_VOYAGER,
      palette = pal,
    )
    DesktopThemeHelper.applyThemeTree(this, pal, skipComponents = setOf(canvas))
    statusLbl.foreground = when {
      errorMessage != null -> pal.tailwindNegative
      !notice.isNullOrBlank() -> pal.metarAmber
      else -> pal.textSecondary
    }
    frameTimeLbl.foreground = pal.metarAmber
  }

  private inner class RadarMapCanvas(
    private val onSelectPin: (String) -> Unit,
  ) : JPanel() {

    private var stations: List<AirportStation> = emptyList()
    private var activeStation: AirportStation? = null
    private var frame: com.example.data.RadarFrame? = null
    private var layerStyle: MapLayerStyle = MapLayerStyle.CARTO_DARK
    private var palette: DesktopPalette = NightDesktopPalette
    private var camera: MapViewportCamera = MapViewportCamera(centerLat = 39.0, centerLon = 58.0, zoom = 5.0)
    private var cameraInitialized = false
    private var lastDrag: Point? = null
    private val pinHits = mutableListOf<Pair<String, Rectangle>>()

    init {
      isOpaque = true
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
          lastDrag = e.point
        }
        override fun mouseReleased(e: MouseEvent) {
          lastDrag = null
        }
        override fun mouseClicked(e: MouseEvent) {
          val hit = pinHits.lastOrNull { (_, r) -> r.contains(e.point) }
          if (hit != null) onSelectPin(hit.first)
        }
      })
      addMouseMotionListener(object : MouseAdapter() {
        override fun mouseDragged(e: MouseEvent) {
          val prev = lastDrag ?: e.point
          val dx = (e.x - prev.x).toFloat()
          val dy = (e.y - prev.y).toFloat()
          lastDrag = e.point
          if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
            camera = AirportMapProjection.panCamera(camera, dx, dy)
            repaint()
          }
        }
      })
      addMouseWheelListener { e: MouseWheelEvent ->
        adjustZoom(-e.preciseWheelRotation * 0.45)
      }
    }

    fun adjustZoom(delta: Double) {
      camera = camera.copy(zoom = (camera.zoom + delta).coerceIn(2.0, 10.0))
      repaint()
    }

    fun centerOn(station: AirportStation) {
      camera = MapViewportCamera(centerLat = station.latitude, centerLon = station.longitude, zoom = 6.2)
      cameraInitialized = true
      repaint()
    }

    fun updateFrame(next: com.example.data.RadarFrame?) {
      frame = next
      repaint()
    }

    fun updateData(
      stations: List<AirportStation>,
      activeStation: AirportStation,
      frame: com.example.data.RadarFrame?,
      layerStyle: MapLayerStyle,
      palette: DesktopPalette,
    ) {
      this.stations = stations
      this.activeStation = activeStation
      this.frame = frame
      this.layerStyle = layerStyle
      this.palette = palette
      repaint()
    }

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      val g2 = g.create() as Graphics2D
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 8f || h < 8f) return

        if (!cameraInitialized) {
          val st = activeStation
          camera = if (st != null) {
            MapViewportCamera(centerLat = st.latitude, centerLon = st.longitude, zoom = 5.4)
          } else {
            AirportMapProjection.cameraForRegion(MapRegionPreset.TURKMENISTAN, stations, w, h)
          }
          cameraInitialized = true
        }

        val isDark = layerStyle == MapLayerStyle.CARTO_DARK
        g2.color = if (isDark) Color(0x0D, 0x15, 0x20) else Color(0xEC, 0xEF, 0xEA)
        g2.fillRect(0, 0, width, height)

        AirportMapProjection.waterPolygons.forEach { poly ->
          if (poly.points.size >= 3) {
            val path = Path2D.Float()
            poly.points.forEachIndexed { idx, (lat, lon) ->
              val p = AirportMapProjection.projectMercator(lat, lon, camera, w, h)
              if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.closePath()
            g2.color = if (isDark) Color(0x13, 0x24, 0x38) else Color(0xBF, 0xD9, 0xEE)
            g2.fill(path)
          }
        }

        val tiles = AirportMapProjection.visibleTiles(camera, w, h)
        val radarHost = timeline?.host ?: "https://tilecache.rainviewer.com"
        tiles.forEach { tile ->
          val baseImg = DesktopMapTileCache.getOrFetch(layerStyle, tile.z, tile.x, tile.y) { repaint() }
          val dx = tile.drawX.toInt()
          val dy = tile.drawY.toInt()
          val sizePx = kotlin.math.ceil(tile.tileSizePx.toDouble()).toInt() + 1
          if (baseImg != null) {
            g2.drawImage(baseImg, dx, dy, sizePx, sizePx, null)
          }
          val radarFrame = frame
          if (radarFrame != null) {
            val req = radarFrame.resolveTileRequest(radarHost, tile.z, tile.x, tile.y, owmApiKey)
            val radarImg = DesktopMapTileCache.getOrFetchUrl(req.cacheKey, req.url) { repaint() }
            if (radarImg != null) {
              val srcW = (radarImg.width / req.subGridSize).coerceAtLeast(1)
              val srcH = (radarImg.height / req.subGridSize).coerceAtLeast(1)
              val sx1 = (req.subCol * srcW).coerceIn(0, (radarImg.width - srcW).coerceAtLeast(0))
              val sy1 = (req.subRow * srcH).coerceIn(0, (radarImg.height - srcH).coerceAtLeast(0))
              val prevComp = g2.composite
              g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.72f)
              g2.drawImage(
                radarImg,
                dx,
                dy,
                dx + sizePx,
                dy + sizePx,
                sx1,
                sy1,
                sx1 + srcW,
                sy1 + srcH,
                null,
              )
              g2.composite = prevComp
            }
          }
        }

        pinHits.clear()
        stations.forEach { st ->
          val p = AirportMapProjection.projectMercator(st.latitude, st.longitude, camera, w, h)
          if (p.x < -60 || p.x > w + 60 || p.y < -40 || p.y > h + 40) return@forEach
          val isAct = st.icao == activeStation?.icao
          val r = if (isAct) 7 else 5
          g2.color = if (isAct) palette.metarAmber else palette.accentCyan
          g2.fillOval((p.x - r).toInt(), (p.y - r).toInt(), r * 2, r * 2)
          g2.color = Color.WHITE
          g2.stroke = BasicStroke(1.5f)
          g2.drawOval((p.x - r).toInt(), (p.y - r).toInt(), r * 2, r * 2)

          g2.font = Font("Consolas", Font.BOLD, 11)
          val fm = g2.fontMetrics
          val tw = fm.stringWidth(st.icao)
          val bx = (p.x - tw / 2f - 4f).toInt()
          val by = (p.y + r + 3f).toInt()
          g2.color = Color(10, 16, 24, 190)
          g2.fillRoundRect(bx, by, tw + 8, 15, 4, 4)
          g2.color = if (isAct) palette.metarAmber else Color(235, 242, 250)
          g2.drawString(st.icao, bx + 4, by + 12)

          pinHits.add(st.icao to Rectangle((p.x - 24).toInt(), (p.y - 16).toInt(), 48, 38))
        }
      } finally {
        g2.dispose()
      }
    }
  }
}
