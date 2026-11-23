package com.example.windows

import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.MapLayerStyle
import com.example.domain.MapRegionPreset
import com.example.domain.MapViewportCamera
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class AirportMapDesktopPanel(
  private val onBackToBoard: () -> Unit,
  private val onOpenStationOnBoard: (String) -> Unit,
  private val onRefreshAllStations: () -> Unit,
  private val onRefreshSingleStation: (String) -> Unit,
  private val onToggleFavorite: (String) -> Unit,
) : JPanel(BorderLayout(10, 10)) {

  private var currentState: DesktopUiState? = null
  private var selectedIcao: String = "UTAM"
  private var activeLayer: MapLayerStyle = MapLayerStyle.CARTO_VOYAGER
  private var activeRegion: MapRegionPreset = MapRegionPreset.TURKMENISTAN
  private var layerManuallyChosen: Boolean = false

  // Header controls
  private val topBar = JPanel(BorderLayout(8, 6))
  private val backButton = JButton("← На табло")
  private val refreshAllButton = JButton("Обновить карту")
  private val layerButtons = linkedMapOf<MapLayerStyle, JButton>()
  private val regionButtons = linkedMapOf<MapRegionPreset, JButton>()

  // Canvas & station quick-jump bar
  private val mapCanvas = SlippyAirportMapCanvas(
    fetchTiles = true,
    onSelectPin = { icao -> selectPin(icao) },
  )
  private val zoomInBtn = JButton("+")
  private val zoomOutBtn = JButton("−")
  private val resetViewBtn = JButton("Сброс")
  private val attributionLabel = JLabel()
  private val quickStationsStrip = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))

  // Right-hand Brief Weather Summary Card
  private val summaryCard = JPanel(BorderLayout(8, 10))
  private val summaryIcaoLabel = JLabel("UTAM")
  private val summaryCategoryBadge = JLabel("Н/Д", SwingConstants.CENTER)
  private val summaryNameLabel = JLabel("Мары")
  private val summaryCoordsLabel = JLabel("--")
  private val summaryFavoriteBtn = JButton("☆")
  private val summaryRefreshBtn = JButton("↻ Обновить пин")
  private val summaryWindLabel = JLabel("--")
  private val summaryVisLabel = JLabel("--")
  private val summaryTempLabel = JLabel("--")
  private val summaryPressLabel = JLabel("--")
  private val summaryRawMetarLabel = JLabel("--")
  private val openOnBoardBtn = JButton("Открыть на табло →")
  private val summaryTileBoxes = mutableListOf<JPanel>()

  init {
    border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    buildLayout()
    wireControls()
  }

  private fun buildLayout() {
    val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
      isOpaque = false
      backButton.font = Font("Segoe UI", Font.BOLD, 12)
      add(backButton)
      val titleLbl = JLabel("Карта аэродромов").apply {
        font = Font("Segoe UI", Font.BOLD, 14)
      }
      add(titleLbl)
    }

    val centerHeader = JPanel(FlowLayout(FlowLayout.CENTER, 4, 2)).apply {
      isOpaque = false
      MapLayerStyle.entries.forEach { style ->
        val btn = JButton(style.label).apply {
          font = Font("Segoe UI", Font.BOLD, 11)
          putClientProperty("layerStyle", style)
        }
        layerButtons[style] = btn
        add(btn)
      }
      add(Box.createHorizontalStrut(8))
      MapRegionPreset.entries.forEach { preset ->
        val btn = JButton(preset.label).apply {
          font = Font("Segoe UI", Font.PLAIN, 11)
        }
        regionButtons[preset] = btn
        add(btn)
      }
    }

    val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2)).apply {
      isOpaque = false
      refreshAllButton.font = Font("Segoe UI", Font.BOLD, 12)
      add(refreshAllButton)
    }

    topBar.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    topBar.add(leftHeader, BorderLayout.WEST)
    topBar.add(centerHeader, BorderLayout.CENTER)
    topBar.add(rightHeader, BorderLayout.EAST)
    add(topBar, BorderLayout.NORTH)

    // Center: Map container + quick chips
    val mapWrap = JPanel(BorderLayout(0, 6)).apply {
      isOpaque = false
    }

    val canvasOverlay = JPanel(BorderLayout()).apply {
      isOpaque = false
      val controlsCol = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 6)).apply {
        isOpaque = false
        zoomInBtn.font = Font("Segoe UI", Font.BOLD, 13)
        zoomOutBtn.font = Font("Segoe UI", Font.BOLD, 13)
        resetViewBtn.font = Font("Segoe UI", Font.BOLD, 11)
        add(zoomInBtn)
        add(zoomOutBtn)
        add(resetViewBtn)
      }
      val bottomMeta = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(4, 10, 4, 10)
        attributionLabel.font = Font("Segoe UI", Font.PLAIN, 10)
        add(attributionLabel, BorderLayout.WEST)
      }
      add(controlsCol, BorderLayout.NORTH)
      add(bottomMeta, BorderLayout.SOUTH)
    }

    mapCanvas.layout = BorderLayout()
    mapCanvas.add(canvasOverlay, BorderLayout.CENTER)
    mapCanvas.preferredSize = Dimension(640, 440)

    val chipsScroll = JScrollPane(
      quickStationsStrip,
      JScrollPane.VERTICAL_SCROLLBAR_NEVER,
      JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    ).apply {
      border = null
      preferredSize = Dimension(600, 44)
    }

    mapWrap.add(mapCanvas, BorderLayout.CENTER)
    mapWrap.add(chipsScroll, BorderLayout.SOUTH)
    add(mapWrap, BorderLayout.CENTER)

    // Right: Summary Card
    summaryCard.preferredSize = Dimension(336, 460)
    summaryCard.border = BorderFactory.createEmptyBorder(12, 14, 12, 14)

    val topSummary = JPanel(BorderLayout(6, 4)).apply {
      isOpaque = false
      val titleRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        isOpaque = false
        summaryIcaoLabel.font = Font("Segoe UI", Font.BOLD, 22)
        summaryCategoryBadge.font = Font("Segoe UI", Font.BOLD, 11)
        summaryCategoryBadge.border = BorderFactory.createEmptyBorder(3, 8, 3, 8)
        summaryCategoryBadge.isOpaque = true
        add(summaryIcaoLabel)
        add(summaryCategoryBadge)
      }
      val rightIcons = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
        isOpaque = false
        summaryFavoriteBtn.font = Font("Segoe UI", Font.BOLD, 15)
        add(summaryFavoriteBtn)
      }
      val subCol = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        summaryNameLabel.font = Font("Segoe UI", Font.BOLD, 13)
        summaryCoordsLabel.font = Font("Consolas", Font.PLAIN, 11)
        add(summaryNameLabel)
        add(Box.createVerticalStrut(2))
        add(summaryCoordsLabel)
      }
      add(titleRow, BorderLayout.WEST)
      add(rightIcons, BorderLayout.EAST)
      add(subCol, BorderLayout.SOUTH)
    }

    val metricsGrid = JPanel(GridLayout(2, 2, 8, 8)).apply {
      isOpaque = false
      add(buildSummaryMetricTile("ВЕТЕР", summaryWindLabel))
      add(buildSummaryMetricTile("ВИДИМОСТЬ / ОБЛ.", summaryVisLabel))
      add(buildSummaryMetricTile("ТЕМП. / РОСА", summaryTempLabel))
      add(buildSummaryMetricTile("ДАВЛЕНИЕ QNH", summaryPressLabel))
    }

    val rawBox = JPanel(BorderLayout(4, 4)).apply {
      isOpaque = false
      val rawTitle = JLabel("ПОСЛЕДНЯЯ СВОДКА METAR").apply {
        font = Font("Segoe UI", Font.BOLD, 10)
      }
      summaryRawMetarLabel.font = Font("Consolas", Font.PLAIN, 11)
      add(rawTitle, BorderLayout.NORTH)
      add(summaryRawMetarLabel, BorderLayout.CENTER)
    }

    val bodyStack = JPanel(BorderLayout(8, 10)).apply {
      isOpaque = false
      add(metricsGrid, BorderLayout.CENTER)
      add(rawBox, BorderLayout.SOUTH)
    }

    val footerActions = JPanel(GridLayout(2, 1, 6, 6)).apply {
      isOpaque = false
      summaryRefreshBtn.font = Font("Segoe UI", Font.BOLD, 12)
      openOnBoardBtn.font = Font("Segoe UI", Font.BOLD, 13)
      add(summaryRefreshBtn)
      add(openOnBoardBtn)
    }

    summaryCard.add(topSummary, BorderLayout.NORTH)
    summaryCard.add(bodyStack, BorderLayout.CENTER)
    summaryCard.add(footerActions, BorderLayout.SOUTH)
    add(summaryCard, BorderLayout.EAST)
  }

  private fun buildSummaryMetricTile(caption: String, valueLabel: JLabel): JPanel {
    val tile = JPanel(BorderLayout(4, 4)).apply {
      border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
      val cap = JLabel(caption).apply {
        font = Font("Segoe UI", Font.BOLD, 10)
      }
      valueLabel.font = Font("Segoe UI", Font.BOLD, 12)
      add(cap, BorderLayout.NORTH)
      add(valueLabel, BorderLayout.CENTER)
    }
    summaryTileBoxes.add(tile)
    return tile
  }

  private fun wireControls() {
    backButton.addActionListener { onBackToBoard() }
    refreshAllButton.addActionListener { onRefreshAllStations() }
    zoomInBtn.addActionListener { mapCanvas.adjustZoom(+0.85) }
    zoomOutBtn.addActionListener { mapCanvas.adjustZoom(-0.85) }
    resetViewBtn.addActionListener { mapCanvas.fitRegion(activeRegion) }

    layerButtons.forEach { (style, btn) ->
      btn.addActionListener {
        activeLayer = style
        layerManuallyChosen = true
        mapCanvas.layerStyle = style
        currentState?.let { refreshLayerAndRegionButtons(it) }
      }
    }

    regionButtons.forEach { (preset, btn) ->
      btn.addActionListener {
        activeRegion = preset
        mapCanvas.fitRegion(preset)
        currentState?.let { refreshLayerAndRegionButtons(it) }
      }
    }

    summaryFavoriteBtn.addActionListener { onToggleFavorite(selectedIcao) }
    summaryRefreshBtn.addActionListener { onRefreshSingleStation(selectedIcao) }
    openOnBoardBtn.addActionListener { onOpenStationOnBoard(selectedIcao) }
  }

  fun selectPin(icao: String) {
    selectedIcao = icao.trim().uppercase()
    currentState?.let { st ->
      if (st.mapWeatherByIcao[selectedIcao] == null) {
        onRefreshSingleStation(selectedIcao)
      }
      applyState(st)
    }
  }

  fun setLiveTileFetchingEnabled(enabled: Boolean) {
    mapCanvas.fetchTiles = enabled
  }

  fun applyState(state: DesktopUiState) {
    currentState = state
    if (!layerManuallyChosen) {
      activeLayer = if (state.isNightMode) MapLayerStyle.CARTO_DARK else MapLayerStyle.CARTO_VOYAGER
    }
    if (state.mapStations.none { it.icao == selectedIcao }) {
      selectedIcao = state.station.icao
    }

    val pal = state.palette
    background = pal.bezelSurface
    topBar.background = pal.panelSurface
    topBar.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(6, 10, 6, 10),
    )

    quickStationsStrip.background = pal.panelSurface
    summaryCard.background = pal.panelSurface
    summaryCard.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(12, 14, 12, 14),
    )

    refreshAllButton.text = if (state.isRefreshingMap) "Обновление..." else "Обновить карту"
    refreshAllButton.isEnabled = !state.isRefreshingMap
    styleSecondaryButton(backButton, pal)
    styleSecondaryButton(refreshAllButton, pal)
    styleSecondaryButton(zoomInBtn, pal)
    styleSecondaryButton(zoomOutBtn, pal)
    styleSecondaryButton(resetViewBtn, pal)

    attributionLabel.text = when (activeLayer) {
      MapLayerStyle.OSM -> "OpenStreetMap · EPSG:3857"
      MapLayerStyle.CARTO_VOYAGER, MapLayerStyle.CARTO_DARK -> "Carto Basemaps · OSM"
      MapLayerStyle.ESRI_SATELLITE -> "Esri World Imagery"
    }
    attributionLabel.foreground = if (activeLayer == MapLayerStyle.CARTO_DARK || activeLayer == MapLayerStyle.ESRI_SATELLITE) {
      Color(235, 242, 250)
    } else {
      pal.textSecondary
    }

    refreshLayerAndRegionButtons(state)

    // Rebuild station chips
    quickStationsStrip.removeAll()
    state.mapStations.forEach { st ->
      val isSel = st.icao == selectedIcao
      val isFav = st.icao in state.favoriteIcaos
      val chip = JButton(if (isFav) "★ ${st.icao}" else st.icao).apply {
        font = Font("Segoe UI", if (isSel) Font.BOLD else Font.PLAIN, 11)
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        background = if (isSel) pal.buttonActiveBackground else pal.fieldBackground
        foreground = if (isFav && !isSel) pal.metarAmber else pal.textPrimary
        border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(if (isSel) pal.metarAmber else pal.border, if (isSel) 2 else 1),
          BorderFactory.createEmptyBorder(3, 8, 3, 8),
        )
        addActionListener { selectPin(st.icao) }
      }
      quickStationsStrip.add(chip)
    }
    quickStationsStrip.revalidate()
    quickStationsStrip.repaint()

    // Update Map Canvas
    mapCanvas.updateData(
      stations = state.mapStations,
      weatherByIcao = state.mapWeatherByIcao,
      selectedIcao = selectedIcao,
      favoriteIcaos = state.favoriteIcaos,
      windUnit = state.windUnit,
      layerStyle = activeLayer,
      palette = pal,
    )

    // Update Summary Card
    val selectedStation = state.mapStations.firstOrNull { it.icao == selectedIcao } ?: state.station
    val weather = state.mapWeatherByIcao[selectedStation.icao]
      ?: if (selectedStation.icao == state.station.icao) state.weather else null
    updateSummaryCard(selectedStation, weather, state)
  }

  private fun refreshLayerAndRegionButtons(state: DesktopUiState) {
    val pal = state.palette
    layerButtons.forEach { (style, btn) ->
      val active = style == activeLayer
      btn.background = if (active) pal.buttonActiveBackground else pal.buttonBackground
      btn.foreground = pal.textPrimary
      btn.isFocusPainted = false
      btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      btn.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(if (active) pal.metarAmber else pal.border, if (active) 2 else 1),
        BorderFactory.createEmptyBorder(4, 8, 4, 8),
      )
    }
    regionButtons.forEach { (preset, btn) ->
      val active = preset == activeRegion
      btn.background = if (active) pal.buttonActiveBackground else pal.buttonBackground
      btn.foreground = pal.textPrimary
      btn.isFocusPainted = false
      btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      btn.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(if (active) pal.borderStrong else pal.border, 1),
        BorderFactory.createEmptyBorder(4, 8, 4, 8),
      )
    }
  }

  private fun updateSummaryCard(
    station: AirportStation,
    weather: DecodedWeather?,
    state: DesktopUiState,
  ) {
    val pal = state.palette
    val cat = AirportMapProjection.evaluateFlightCategory(weather)
    val catColor = flightCategoryColor(cat, pal)

    summaryIcaoLabel.text = station.icao
    summaryIcaoLabel.foreground = pal.textPrimary

    summaryCategoryBadge.text = cat.code
    summaryCategoryBadge.background = Color(catColor.red, catColor.green, catColor.blue, 36)
    summaryCategoryBadge.foreground = catColor
    summaryCategoryBadge.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(catColor, 1),
      BorderFactory.createEmptyBorder(3, 8, 3, 8),
    )

    summaryNameLabel.text = "${station.city} · ${station.name}"
    summaryNameLabel.foreground = pal.textSecondary
    summaryCoordsLabel.text = String.format(
      Locale.US,
      "%.2f°, %.2f° · %d м",
      station.latitude,
      station.longitude,
      station.elevationMeters,
    )
    summaryCoordsLabel.foreground = pal.textMuted

    val isFav = station.icao in state.favoriteIcaos
    summaryFavoriteBtn.text = if (isFav) "★" else "☆"
    summaryFavoriteBtn.foreground = if (isFav) pal.metarAmber else pal.textMuted
    styleSecondaryButton(summaryFavoriteBtn, pal)

    val isLoadingThis = state.loadingMapIcao == station.icao
    summaryRefreshBtn.text = if (isLoadingThis) "Загрузка ${station.icao}…" else "↻ Обновить пин (${station.icao})"
    summaryRefreshBtn.isEnabled = !isLoadingThis
    styleSecondaryButton(summaryRefreshBtn, pal)

    openOnBoardBtn.text = "Открыть ${station.icao} на табло →"
    openOnBoardBtn.background = pal.metarAmber
    openOnBoardBtn.foreground = Color.WHITE
    openOnBoardBtn.isFocusPainted = false
    openOnBoardBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    openOnBoardBtn.border = BorderFactory.createEmptyBorder(8, 12, 8, 12)

    summaryTileBoxes.forEach { box ->
      box.background = pal.fieldBackground
      box.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(8, 10, 8, 10),
      )
      box.components.forEach { c ->
        if (c is JLabel && c !== summaryWindLabel && c !== summaryVisLabel && c !== summaryTempLabel && c !== summaryPressLabel) {
          c.foreground = pal.textMuted
        }
      }
    }

    if (weather == null || weather.observationTimeUtc == "--:--Z") {
      summaryWindLabel.text = "Нет данных"
      summaryVisLabel.text = "Ожидание METAR"
      summaryTempLabel.text = "--"
      summaryPressLabel.text = "--"
      summaryRawMetarLabel.text = "<html><body style='width:270px'>Нажмите «Обновить пин», чтобы загрузить METAR для ${station.icao}.</body></html>"
    } else {
      val dirStr = when {
        weather.wind.isCalm -> "Штиль"
        weather.wind.isVariable -> "VRB"
        weather.wind.directionDeg != null -> String.format(Locale.US, "%03d°", weather.wind.directionDeg)
        else -> "--"
      }
      val spdStr = UnitConverter.formatWindSpeedValue(weather.wind.speedKt, state.windUnit)
      val gustStr = weather.wind.gustKt?.let { " G${UnitConverter.formatWindSpeedValue(it, state.windUnit)}" }.orEmpty()
      summaryWindLabel.text = "$dirStr $spdStr$gustStr ${state.windUnit.label}"

      val visStr = when {
        weather.isCavok -> "CAVOK"
        weather.visibilityMeters != null && weather.visibilityMeters >= 9999 -> "≥ 10 км"
        weather.visibilityMeters != null -> "${weather.visibilityMeters} м"
        else -> "--"
      }
      val cloudBrief = weather.clouds.firstOrNull()?.let { " · ${it.cover} ${it.baseFeet} ft" }.orEmpty()
      summaryVisLabel.text = "$visStr$cloudBrief"

      val t = weather.temperatureC?.let { "$it°C" } ?: "--"
      val d = weather.dewPointC?.let { "$it°C" } ?: "--"
      summaryTempLabel.text = "$t / $d"

      summaryPressLabel.text = when (state.pressureUnit) {
        PressureUnit.HPA -> weather.qnhHpa?.let { "$it гПа" } ?: "--"
        PressureUnit.INHG -> weather.qnhInHg?.let { String.format(Locale.forLanguageTag("ru-RU"), "%.2f дм", it) } ?: "--"
      }

      val escaped = weather.rawMetar
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
      summaryRawMetarLabel.text = "<html><body style='width:275px'>$escaped</body></html>"
    }

    summaryWindLabel.foreground = pal.textPrimary
    summaryVisLabel.foreground = pal.textPrimary
    summaryTempLabel.foreground = pal.textPrimary
    summaryPressLabel.foreground = pal.textPrimary
    summaryRawMetarLabel.foreground = pal.textSecondary
  }

  private fun styleSecondaryButton(btn: JButton, pal: DesktopPalette) {
    btn.background = pal.buttonBackground
    btn.foreground = pal.textPrimary
    btn.isFocusPainted = false
    btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    btn.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(5, 10, 5, 10),
    )
  }

  private fun flightCategoryColor(cat: FlightCategory, pal: DesktopPalette): Color = when (cat) {
    FlightCategory.VFR -> pal.headwindPositive
    FlightCategory.MVFR -> pal.metarAmber
    FlightCategory.IFR -> pal.tailwindNegative
    FlightCategory.UNKNOWN -> pal.textMuted
  }
}

class SlippyAirportMapCanvas(
  var fetchTiles: Boolean = true,
  private val onSelectPin: (String) -> Unit,
) : JPanel() {

  private var stations: List<AirportStation> = emptyList()
  private var weatherByIcao: Map<String, DecodedWeather> = emptyMap()
  private var selectedIcao: String = "UTAM"
  private var favoriteIcaos: List<String> = emptyList()
  private var windUnit: WindSpeedUnit = WindSpeedUnit.KT
  private var palette: DesktopPalette = DayDesktopPalette

  var layerStyle: MapLayerStyle = MapLayerStyle.CARTO_VOYAGER
    set(value) {
      field = value
      repaint()
    }

  private var camera: MapViewportCamera = MapViewportCamera(
    centerLat = 39.2,
    centerLon = 59.0,
    zoom = 5.3,
  )
  private var cameraInitializedForSize = false
  private var lastDragPoint: Point? = null
  private val pinHitBoxes = mutableListOf<Pair<String, Rectangle>>()

  init {
    isOpaque = true
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        lastDragPoint = e.point
      }

      override fun mouseReleased(e: MouseEvent) {
        lastDragPoint = null
      }

      override fun mouseClicked(e: MouseEvent) {
        val hit = pinHitBoxes.lastOrNull { (_, rect) -> rect.contains(e.point) }
        if (hit != null) {
          onSelectPin(hit.first)
        }
      }
    })

    addMouseMotionListener(object : MouseAdapter() {
      override fun mouseDragged(e: MouseEvent) {
        val prev = lastDragPoint ?: e.point
        val dx = (e.x - prev.x).toFloat()
        val dy = (e.y - prev.y).toFloat()
        lastDragPoint = e.point
        if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
          camera = AirportMapProjection.panCamera(camera, dx, dy)
          repaint()
        }
      }
    })

    addMouseWheelListener { e: MouseWheelEvent ->
      val delta = -e.preciseWheelRotation * 0.45
      adjustZoom(delta)
    }
  }

  fun updateData(
    stations: List<AirportStation>,
    weatherByIcao: Map<String, DecodedWeather>,
    selectedIcao: String,
    favoriteIcaos: List<String>,
    windUnit: WindSpeedUnit,
    layerStyle: MapLayerStyle,
    palette: DesktopPalette,
  ) {
    this.stations = stations
    this.weatherByIcao = weatherByIcao
    this.selectedIcao = selectedIcao
    this.favoriteIcaos = favoriteIcaos
    this.windUnit = windUnit
    this.layerStyle = layerStyle
    this.palette = palette
    repaint()
  }

  fun adjustZoom(delta: Double) {
    camera = camera.copy(zoom = (camera.zoom + delta).coerceIn(2.0, 12.0))
    repaint()
  }

  fun fitRegion(preset: MapRegionPreset) {
    camera = AirportMapProjection.cameraForRegion(
      preset = preset,
      stations = stations,
      widthPx = width.toFloat().coerceAtLeast(1f),
      heightPx = height.toFloat().coerceAtLeast(1f),
    )
    cameraInitializedForSize = width > 0 && height > 0
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

      if (!cameraInitializedForSize) {
        camera = AirportMapProjection.cameraForRegion(
          preset = MapRegionPreset.TURKMENISTAN,
          stations = stations,
          widthPx = w,
          heightPx = h,
        )
        cameraInitializedForSize = true
      }

      val isDarkLayer = layerStyle == MapLayerStyle.CARTO_DARK || layerStyle == MapLayerStyle.ESRI_SATELLITE
      val landBg = if (isDarkLayer) Color(0x0D, 0x15, 0x20) else Color(0xEC, 0xEF, 0xEA)
      val waterBg = if (isDarkLayer) Color(0x13, 0x24, 0x38) else Color(0xBF, 0xD9, 0xEE)

      g2.color = landBg
      g2.fillRect(0, 0, width, height)

      // Fallback simplified seas under tiles while tiles stream in or offline
      AirportMapProjection.waterPolygons.forEach { poly ->
        if (poly.points.size >= 3) {
          val path = Path2D.Float()
          poly.points.forEachIndexed { idx, (lat, lon) ->
            val p = AirportMapProjection.projectMercator(lat, lon, camera, w, h)
            if (idx == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
          }
          path.closePath()
          g2.color = waterBg
          g2.fill(path)
        }
      }

      // Real Slippy Map raster tiles
      val tiles = AirportMapProjection.visibleTiles(camera, w, h)
      tiles.forEach { tile ->
        val img = if (fetchTiles) {
          DesktopMapTileCache.getOrFetch(layerStyle, tile.z, tile.x, tile.y) {
            repaint()
          }
        } else {
          null
        }
        if (img != null) {
          val dx = tile.drawX.toInt()
          val dy = tile.drawY.toInt()
          val sizePx = kotlin.math.ceil(tile.tileSizePx.toDouble()).toInt() + 1
          g2.drawImage(img, dx, dy, sizePx, sizePx, null)
        }
      }

      // Subtle aviation coordinate grid
      g2.color = Color(120, 145, 175, 48)
      g2.stroke = BasicStroke(1f)
      listOf(25.0, 35.0, 40.0, 45.0, 55.0).forEach { lat ->
        val p1 = AirportMapProjection.projectMercator(lat, -20.0, camera, w, h)
        val p2 = AirportMapProjection.projectMercator(lat, 90.0, camera, w, h)
        g2.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
      }
      listOf(30.0, 40.0, 50.0, 60.0, 70.0).forEach { lon ->
        val p1 = AirportMapProjection.projectMercator(15.0, lon, camera, w, h)
        val p2 = AirportMapProjection.projectMercator(65.0, lon, camera, w, h)
        g2.drawLine(p1.x.toInt(), p1.y.toInt(), p2.x.toInt(), p2.y.toInt())
      }

      // Flight range halo around selected airport
      val selectedStation = stations.firstOrNull { it.icao == selectedIcao }
      if (selectedStation != null) {
        val sp = AirportMapProjection.projectMercator(selectedStation.latitude, selectedStation.longitude, camera, w, h)
        g2.color = Color(palette.metarAmber.red, palette.metarAmber.green, palette.metarAmber.blue, 95)
        g2.stroke = BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(6f, 5f), 0f)
        for (r in listOf(42, 84)) {
          g2.drawOval((sp.x - r).toInt(), (sp.y - r).toInt(), r * 2, r * 2)
        }
      }

      // Paint Airport Pins & collect clickable hitboxes
      pinHitBoxes.clear()
      val ordered = stations.sortedBy { if (it.icao == selectedIcao) 1 else 0 }
      ordered.forEach { st ->
        val p = AirportMapProjection.projectMercator(st.latitude, st.longitude, camera, w, h)
        if (p.x < -80 || p.x > w + 80 || p.y < -60 || p.y > h + 60) return@forEach

        val wx = weatherByIcao[st.icao]
        val cat = AirportMapProjection.evaluateFlightCategory(wx)
        val catColor = when (cat) {
          FlightCategory.VFR -> palette.headwindPositive
          FlightCategory.MVFR -> palette.metarAmber
          FlightCategory.IFR -> palette.tailwindNegative
          FlightCategory.UNKNOWN -> palette.textMuted
        }
        val isSel = st.icao == selectedIcao
        val isFav = st.icao in favoriteIcaos

        // Draw wind barb / direction vector if live wind exists
        if (wx != null && wx.wind.directionDeg != null && wx.wind.speedKt > 0) {
          val rad = Math.toRadians(wx.wind.directionDeg.toDouble())
          val len = (18.0 + (wx.wind.speedKt.coerceAtMost(40) * 0.45))
          val ex = p.x + (sin(rad) * len).toFloat()
          val ey = p.y - (cos(rad) * len).toFloat()
          g2.color = palette.windArrow
          g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
          g2.drawLine(p.x.toInt(), p.y.toInt(), ex.toInt(), ey.toInt())
        }

        val labelText = buildString {
          if (isFav) append("★ ")
          append(st.icao)
          if (wx != null && wx.temperatureC != null) {
            append(" ${wx.temperatureC}°")
          }
        }
        g2.font = Font("Segoe UI", Font.BOLD, if (isSel) 12 else 11)
        val fm = g2.fontMetrics
        val badgeW = fm.stringWidth(labelText) + 22
        val badgeH = 24
        val bx = (p.x - badgeW / 2f).toInt()
        val by = (p.y - badgeH - 8f).toInt()

        val pill = RoundRectangle2D.Float(bx.toFloat(), by.toFloat(), badgeW.toFloat(), badgeH.toFloat(), 12f, 12f)
        g2.color = if (isSel) palette.metarAmber else Color(
          palette.panelSurface.red,
          palette.panelSurface.green,
          palette.panelSurface.blue,
          236,
        )
        g2.fill(pill)

        g2.color = if (isSel) Color.WHITE else catColor
        g2.stroke = BasicStroke(if (isSel) 2f else 1.5f)
        g2.draw(pill)

        // Category dot inside pill
        g2.color = if (isSel) Color.WHITE else catColor
        g2.fillOval(bx + 7, by + badgeH / 2 - 4, 8, 8)

        // Pin Label
        g2.color = if (isSel) Color.WHITE else palette.textPrimary
        g2.drawString(labelText, bx + 18, by + fm.ascent + (badgeH - fm.height) / 2)

        // Anchor dot
        g2.color = if (isSel) palette.metarAmber else catColor
        g2.fillOval((p.x - 5).toInt(), (p.y - 5).toInt(), 10, 10)
        g2.color = Color.WHITE
        g2.stroke = BasicStroke(1.5f)
        g2.drawOval((p.x - 5).toInt(), (p.y - 5).toInt(), 10, 10)

        pinHitBoxes.add(
          st.icao to Rectangle(bx - 4, by - 4, badgeW + 8, badgeH + 16),
        )
      }

      // Flight Category legend pill (bottom-right)
      paintLegendPill(g2, w.toInt(), h.toInt())
    } finally {
      g2.dispose()
    }
  }

  private fun paintLegendPill(g2: Graphics2D, w: Int, h: Int) {
    val legendItems = listOf(
      "VFR" to palette.headwindPositive,
      "MVFR" to palette.metarAmber,
      "IFR" to palette.tailwindNegative,
      "Н/Д" to palette.textMuted,
    )
    g2.font = Font("Segoe UI", Font.BOLD, 10)
    val fm = g2.fontMetrics
    val totalW = legendItems.sumOf { fm.stringWidth(it.first) + 22 } + 10
    val boxH = 24
    val x0 = (w - totalW - 12).coerceAtLeast(8)
    val y0 = (h - boxH - 10).coerceAtLeast(8)

    g2.color = Color(palette.panelSurface.red, palette.panelSurface.green, palette.panelSurface.blue, 225)
    g2.fillRoundRect(x0, y0, totalW, boxH, 12, 12)
    g2.color = palette.border
    g2.stroke = BasicStroke(1f)
    g2.drawRoundRect(x0, y0, totalW, boxH, 12, 12)

    var cx = x0 + 10
    legendItems.forEach { (lbl, c) ->
      g2.color = c
      g2.fillOval(cx, y0 + 8, 8, 8)
      g2.color = palette.textPrimary
      g2.drawString(lbl, cx + 11, y0 + fm.ascent + 5)
      cx += fm.stringWidth(lbl) + 22
    }
  }
}
