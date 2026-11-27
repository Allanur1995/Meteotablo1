package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.UnitConverter
import com.example.domain.WeatherExportPayload
import com.example.domain.WeatherReportFormatter
import com.example.domain.WindTrendAnalyzer
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import com.example.model.UnitSystem
import com.example.model.WindSpeedUnit
import com.example.model.WindTrendDirection
import com.example.model.WindTrendSummary
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.geom.Path2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import kotlin.math.abs
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

enum class DesktopCenterView {
  BOARD,
  MAP,
  TRENDS,
  MY_AIRPORTS,
}

class MeteoDesktopFrame(
  private val controller: DesktopController,
) : JFrame("${AuthorBrand.APP_TITLE} — Авиационная метеостанция") {

  private var suppressingEvents = false
  private var currentCenterView = DesktopCenterView.BOARD

  // Toolbar controls
  private val rootPanel = JPanel(BorderLayout(8, 8))
  private val toolbarPanel = JPanel(BorderLayout(8, 6))
  private val toolsStripPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
  private val titleButton = JButton(AuthorBrand.APP_TITLE)
  private val stationCombo = JComboBox<String>()
  private val favoriteButton = JButton("☆")
  private val metarUtcLabel = JLabel("METAR --:--Z")
  private val sourceModeCombo = JComboBox(DataSourceMode.entries.map { it.shortLabel }.toTypedArray())
  private val autoRefreshBadgeButton = JButton("АВТО 15м")
  private val refreshButton = JButton("Обновить")
  private val nightButton = JButton("Ночь")

  // Secondary Aviation Tools Strip
  private val unitSystemButton = JButton("Imperial")
  private val windUnitButton = JButton("kt")
  private val pressureUnitButton = JButton("гПа")
  private val myAirportsButton = JButton("Мои")
  private val mapButton = JButton("Карта")
  private val trendsButton = JButton("Графики 24ч")
  private val dualRoseButton = JButton("ВПП Роза")
  private val crosswindButton = JButton("Бок. ветер")
  private val decoderButton = JButton("Декодер")
  private val pressureConvButton = JButton("QNH/QFE")
  private val tableButton = JButton("Таблица")
  private val shareButton = JButton("Поделиться")
  private val exportButton = JButton("Экспорт")
  private val alertsButton = JButton("Оповещ.")
  private val logButton = JButton("Лог")
  private val presetsButton = JButton("Профили")
  private val serverButton = JButton("Сервер")

  // Top ICAO Search Bar + Manual Coordinates Entry
  private val searchBarPanel = JPanel(BorderLayout(8, 4))
  private val searchField = JTextField()
  private val searchClearBtn = JButton("×")
  private val searchSubmitBtn = JButton("Найти METAR")
  private val coordToggleBtn = JButton("Lat / Lon")
  private val coordEntryPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))
  private val latField = JTextField(8)
  private val lonField = JTextField(8)
  private val coordFetchBtn = JButton("Загрузить по коорд.")
  private val detectedLocationLabel = JLabel("")
  private val recentSearchesPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
  private val recentSearchesChipsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
  private val recentSearchesClearBtn = JButton("×")
  private val searchSuggestionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))

  // Dedicated My Airports panel (moved off the main board)
  private val myAirportsViewPanel = JPanel(BorderLayout(8, 8))
  private val myAirportsBackBtn = JButton("← На табло")
  private val myAirportsOpenMapBtn = JButton("Карта")
  private val myAirportsToggleCurrentBtn = JButton("★ В мои")
  private val myAirportsAddIcaoField = JTextField(6)
  private val myAirportsAddFavBtn = JButton("+ ICAO")
  private val myAirportsChipsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 8))
  private var myAirportsChipsScroll: JScrollPane? = null

  // Weather Alert Banner
  private val alertBannerPanel = JPanel(BorderLayout(8, 4))
  private val alertBannerLabel = JLabel()
  private val alertBannerOpenBtn = JButton("Открыть")
  private val alertBannerOkBtn = JButton("OK")

  // Status Banner
  private val bannerPanel = JPanel(BorderLayout(8, 4))
  private val bannerLabel = JLabel()
  private val bannerOkBtn = JButton("OK")

  // Center Deck: Board vs Airport Map View vs 24h Wind & Pressure Trends
  private val centerCardLayout = CardLayout()
  private val centerDeckPanel = JPanel(centerCardLayout)
  private val boardPanel = JPanel(GridBagLayout())
  private val mapPanel = AirportMapDesktopPanel(
    onBackToBoard = { setCenterView(DesktopCenterView.BOARD) },
    onOpenStationOnBoard = { icao ->
      controller.selectStation(icao)
      setCenterView(DesktopCenterView.BOARD)
    },
    onRefreshAllStations = { controller.refreshMapWeather() },
    onRefreshSingleStation = { icao -> controller.fetchStationWeatherForMap(icao) },
    onToggleFavorite = { icao -> controller.toggleFavorite(icao) },
  )
  private val trendsPanel = WindPressure24hChartPanel(
    onBackToBoard = { setCenterView(DesktopCenterView.BOARD) },
    onSelectAirport = { icao -> controller.selectD3TrendAirport(icao) },
    onRefreshHistory = { icao -> controller.load24hHistoryForIcao(icao, force = true) },
    onSelectUnitSystem = { sys -> controller.applyUnitSystem(sys) },
    onOpenExport = { DesktopDialogs.showExportDialog(this, controller) },
  )
  private val leftRunwayHeader = JLabel("", SwingConstants.CENTER)
  private val leftRunwayCombo = JComboBox<String>()
  private val leftWindRose = RunwayWindRosePanel { controller.toggleWindUnit() }
  private val leftRvrValue = JLabel("--", SwingConstants.CENTER)
  private val leftStatsPanel = JPanel(GridLayout(2, 2, 6, 4))
  private val leftHeadwindField = JTextField("--")
  private val leftCrosswindField = JTextField("--")

  private val rightRunwayHeader = JLabel("", SwingConstants.CENTER)
  private val rightRunwaySpacer = JLabel("", SwingConstants.CENTER)
  private val rightWindRose = RunwayWindRosePanel { controller.toggleWindUnit() }
  private val rightRvrValue = JLabel("--", SwingConstants.CENTER)
  private val rightStatsPanel = JPanel(GridLayout(2, 2, 6, 4))
  private val rightHeadwindField = JTextField("--")
  private val rightCrosswindField = JTextField("--")

  // Center weather fields
  private val centerPanel = JPanel(BorderLayout(6, 6))
  private val localClockLabel = JLabel("--:--Z", SwingConstants.CENTER)
  private val flightCatBadge = JLabel("Н/Д", SwingConstants.CENTER)
  private val stationSubtitleLabel = JLabel("", SwingConstants.CENTER)
  private val visField = createReadonlyField()
  private val cloudsField = createReadonlyField()
  private val ceilingField = createReadonlyField()
  private val weatherField = createReadonlyField()
  private val tempField = createReadonlyField()
  private val dewField = createReadonlyField()
  private val qnhField = createReadonlyField()
  private val qfeField = createReadonlyField()
  private val sunField = createReadonlyField()
  private val sourceField = createReadonlyField()
  private val windTrendPanel = WindTrendSparklinePanel()

  // Bottom METAR/TAF footer
  private val footerPanel = JPanel(GridLayout(2, 1, 4, 5))
  private val tafLabel = JLabel("TAF: --")
  private val metarLabel = JLabel("METAR: --")
  private val ownerFooterPanel = JPanel(BorderLayout())
  private val ownerLeftLabel = JLabel("${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · ${AuthorBrand.SUBTITLE}")
  private val ownerRightLabel = JLabel("QNH / QFE · METAR / TAF · О программе")

  init {
    defaultCloseOperation = EXIT_ON_CLOSE
    minimumSize = Dimension(980, 680)
    setSize(1280, 800)
    setLocationRelativeTo(null)

    buildUi()
    wireActions()

    controller.addListener { state ->
      applyState(state)
    }

    addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent?) {
        controller.shutdown()
      }
    })
  }

  private fun buildUi() {
    rootPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    contentPane = rootPanel

    // Toolbar Top Row: Brand/Station left, Sync/Theme right
    val leftTool = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2)).apply {
      isOpaque = false
      titleButton.font = Font("Segoe UI", Font.BOLD, 14)
      titleButton.toolTipText = "Выбор аэропорта и ввод METAR"
      stationCombo.font = Font("Segoe UI", Font.BOLD, 12)
      favoriteButton.font = Font("Segoe UI", Font.BOLD, 14)
      favoriteButton.toolTipText = "Добавить/удалить из избранного"
      add(titleButton)
      add(stationCombo)
      add(favoriteButton)
    }

    val rightTool = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 2)).apply {
      isOpaque = false
      metarUtcLabel.font = Font("Consolas", Font.BOLD, 13)
      metarUtcLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      sourceModeCombo.font = Font("Segoe UI", Font.BOLD, 11)
      sourceModeCombo.toolTipText = "Источник данных: ведомственный метеосервер и/или Интернет-сайты"
      autoRefreshBadgeButton.font = Font("Consolas", Font.BOLD, 11)
      autoRefreshBadgeButton.toolTipText = "Автообновление 5 / 15 / 30 мин (нажмите для настройки)"
      add(metarUtcLabel)
      add(sourceModeCombo)
      add(autoRefreshBadgeButton)
      add(refreshButton)
      add(nightButton)
    }

    val topToolbarRow = JPanel(BorderLayout(8, 2)).apply {
      isOpaque = false
      add(leftTool, BorderLayout.WEST)
      add(rightTool, BorderLayout.EAST)
    }

    toolsStripPanel.isOpaque = false
    toolsStripPanel.add(unitSystemButton)
    toolsStripPanel.add(windUnitButton)
    toolsStripPanel.add(pressureUnitButton)
    toolsStripPanel.add(myAirportsButton)
    toolsStripPanel.add(mapButton)
    toolsStripPanel.add(trendsButton)
    toolsStripPanel.add(dualRoseButton)
    toolsStripPanel.add(crosswindButton)
    toolsStripPanel.add(decoderButton)
    toolsStripPanel.add(pressureConvButton)
    toolsStripPanel.add(tableButton)
    toolsStripPanel.add(shareButton)
    toolsStripPanel.add(exportButton)
    toolsStripPanel.add(alertsButton)
    toolsStripPanel.add(logButton)
    toolsStripPanel.add(presetsButton)
    toolsStripPanel.add(serverButton)

    toolbarPanel.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    toolbarPanel.add(topToolbarRow, BorderLayout.NORTH)
    toolbarPanel.add(toolsStripPanel, BorderLayout.SOUTH)

    // Top ICAO Search Bar + Manual Coordinates Entry
    val searchPromptLabel = JLabel("ICAO / Поиск:").apply {
      font = Font("Segoe UI", Font.BOLD, 12)
    }
    searchField.font = Font("Segoe UI", Font.BOLD, 13)
    searchField.toolTipText = "Введите 4-буквенный код ICAO (напр. UTAA, OMDB, KJFK) или название города"
    searchClearBtn.font = Font("Segoe UI", Font.BOLD, 13)
    searchClearBtn.isVisible = false
    searchSubmitBtn.font = Font("Segoe UI", Font.BOLD, 12)
    coordToggleBtn.font = Font("Segoe UI", Font.BOLD, 11)
    coordToggleBtn.toolTipText = "Ввести широту и долготу вручную для удалённых аэродромов"

    val searchInputWrap = JPanel(BorderLayout(6, 0)).apply {
      isOpaque = false
      add(searchPromptLabel, BorderLayout.WEST)
      add(searchField, BorderLayout.CENTER)
      add(
        JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
          isOpaque = false
          add(searchClearBtn)
          add(searchSubmitBtn)
          add(coordToggleBtn)
        },
        BorderLayout.EAST,
      )
    }

    latField.font = Font("Consolas", Font.BOLD, 12)
    lonField.font = Font("Consolas", Font.BOLD, 12)
    latField.text = String.format(Locale.US, "%.4f", controller.state.station.latitude)
    lonField.text = String.format(Locale.US, "%.4f", controller.state.station.longitude)
    detectedLocationLabel.font = Font("Segoe UI", Font.PLAIN, 11)
    coordEntryPanel.isOpaque = false
    coordEntryPanel.isVisible = false
    coordEntryPanel.add(JLabel("Широта (Lat):"))
    coordEntryPanel.add(latField)
    coordEntryPanel.add(JLabel("Долгота (Lon):"))
    coordEntryPanel.add(lonField)
    coordEntryPanel.add(coordFetchBtn)
    coordEntryPanel.add(detectedLocationLabel)

    recentSearchesPanel.isOpaque = false
    recentSearchesPanel.isVisible = false
    recentSearchesChipsPanel.isOpaque = false
    recentSearchesClearBtn.font = Font("Segoe UI", Font.BOLD, 11)
    recentSearchesClearBtn.toolTipText = "Очистить недавние запросы ICAO"
    recentSearchesPanel.add(JLabel("🕒 Недавние:").apply { font = Font("Segoe UI", Font.BOLD, 11) })
    recentSearchesPanel.add(recentSearchesChipsPanel)
    recentSearchesPanel.add(recentSearchesClearBtn)

    val searchSouthStack = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(coordEntryPanel)
      add(recentSearchesPanel)
      add(searchSuggestionsPanel)
    }
    searchSuggestionsPanel.isOpaque = false
    searchSuggestionsPanel.isVisible = false
    searchBarPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
    searchBarPanel.add(searchInputWrap, BorderLayout.CENTER)
    searchBarPanel.add(searchSouthStack, BorderLayout.SOUTH)

    // Dedicated My Airports View Panel
    val myAirportsHeaderLeft = JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
      isOpaque = false
      add(myAirportsBackBtn)
      add(JLabel("★ Мои аэропорты").apply { font = Font("Segoe UI", Font.BOLD, 15) })
    }
    val myAirportsHeaderRight = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2)).apply {
      isOpaque = false
      myAirportsAddIcaoField.font = Font("Consolas", Font.BOLD, 12)
      myAirportsAddIcaoField.toolTipText = "Введите 4-буквенный ICAO (напр. UTAA)"
      add(JLabel("ICAO:"))
      add(myAirportsAddIcaoField)
      add(myAirportsAddFavBtn)
      add(myAirportsToggleCurrentBtn)
      add(myAirportsOpenMapBtn)
    }
    val myAirportsTopBar = JPanel(BorderLayout(8, 4)).apply {
      isOpaque = false
      add(myAirportsHeaderLeft, BorderLayout.WEST)
      add(myAirportsHeaderRight, BorderLayout.EAST)
    }
    myAirportsChipsScroll = JScrollPane(myAirportsChipsPanel)
    myAirportsViewPanel.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
    myAirportsViewPanel.add(myAirportsTopBar, BorderLayout.NORTH)
    myAirportsViewPanel.add(myAirportsChipsScroll!!, BorderLayout.CENTER)

    // Weather Alert Banner
    alertBannerPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
    alertBannerLabel.font = Font("Segoe UI", Font.BOLD, 12)
    val alertActions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
      isOpaque = false
      add(alertBannerOpenBtn)
      add(alertBannerOkBtn)
    }
    alertBannerPanel.add(alertBannerLabel, BorderLayout.CENTER)
    alertBannerPanel.add(alertActions, BorderLayout.EAST)
    alertBannerPanel.isVisible = false

    // Banner
    bannerPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
    bannerLabel.font = Font("Segoe UI", Font.BOLD, 12)
    bannerPanel.add(bannerLabel, BorderLayout.CENTER)
    bannerPanel.add(bannerOkBtn, BorderLayout.EAST)
    bannerPanel.isVisible = false

    val northStack = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(toolbarPanel)
      add(Box.createVerticalStrut(4))
      add(searchBarPanel)
      add(Box.createVerticalStrut(3))
      add(alertBannerPanel)
      add(bannerPanel)
    }
    rootPanel.add(northStack, BorderLayout.NORTH)

    // Left Runway Column
    val leftCol = buildRunwayColumn(
      headerLabel = leftRunwayHeader,
      selector = leftRunwayCombo,
      windRose = leftWindRose,
      rvrValue = leftRvrValue,
      headwindField = leftHeadwindField,
      crosswindField = leftCrosswindField,
      statsPanel = leftStatsPanel,
    )

    // Right Runway Column
    val rightCol = buildRunwayColumn(
      headerLabel = rightRunwayHeader,
      selector = rightRunwaySpacer,
      windRose = rightWindRose,
      rvrValue = rightRvrValue,
      headwindField = rightHeadwindField,
      crosswindField = rightCrosswindField,
      statsPanel = rightStatsPanel,
    )

    // Center Weather Column
    val clockHeader = JPanel(BorderLayout(4, 4)).apply {
      isOpaque = false
      localClockLabel.font = Font("Consolas", Font.BOLD, 32)
      flightCatBadge.font = Font("Segoe UI", Font.BOLD, 12)
      flightCatBadge.isOpaque = true
      flightCatBadge.border = BorderFactory.createEmptyBorder(2, 8, 2, 8)
      stationSubtitleLabel.font = Font("Segoe UI", Font.PLAIN, 12)
      val clockRow = JPanel(FlowLayout(FlowLayout.CENTER, 10, 0)).apply {
        isOpaque = false
        add(localClockLabel)
        add(flightCatBadge)
      }
      add(clockRow, BorderLayout.NORTH)
      add(stationSubtitleLabel, BorderLayout.SOUTH)
    }
    val telemetryGrid = JPanel(GridLayout(10, 2, 6, 4)).apply {
      isOpaque = false
      border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
      add(JLabel("Видимость:")); add(visField)
      add(JLabel("Облачность:")); add(cloudsField)
      add(JLabel("ВНГО:")); add(ceilingField)
      add(JLabel("Явления:")); add(weatherField)
      add(JLabel("Температура:")); add(tempField)
      add(JLabel("Точка росы:")); add(dewField)
      add(JLabel("QNH:")); add(qnhField)
      add(JLabel("QFE:")); add(qfeField)
      add(JLabel("Восх. / Зак. (UTC):")); add(sunField)
      add(JLabel("Источник:")); add(sourceField)
    }
    centerPanel.border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
    centerPanel.add(clockHeader, BorderLayout.NORTH)
    centerPanel.add(telemetryGrid, BorderLayout.CENTER)
    centerPanel.add(windTrendPanel, BorderLayout.SOUTH)

    val gbc = GridBagConstraints().apply {
      fill = GridBagConstraints.BOTH
      weighty = 1.0
      insets = Insets(4, 4, 4, 4)
    }
    gbc.gridx = 0; gbc.weightx = 0.34
    boardPanel.add(leftCol, gbc)
    gbc.gridx = 1; gbc.weightx = 0.32
    boardPanel.add(centerPanel, gbc)
    gbc.gridx = 2; gbc.weightx = 0.34
    boardPanel.add(rightCol, gbc)

    centerDeckPanel.isOpaque = false
    centerDeckPanel.add(boardPanel, DesktopCenterView.BOARD.name)
    centerDeckPanel.add(mapPanel, DesktopCenterView.MAP.name)
    centerDeckPanel.add(trendsPanel, DesktopCenterView.TRENDS.name)
    centerDeckPanel.add(myAirportsViewPanel, DesktopCenterView.MY_AIRPORTS.name)
    centerCardLayout.show(centerDeckPanel, DesktopCenterView.BOARD.name)
    rootPanel.add(centerDeckPanel, BorderLayout.CENTER)

    // Footer (enlarged readable METAR/TAF font)
    tafLabel.font = Font("Consolas", Font.BOLD, 13)
    metarLabel.font = Font("Consolas", Font.BOLD, 14)
    footerPanel.border = BorderFactory.createEmptyBorder(7, 10, 7, 10)
    footerPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    footerPanel.toolTipText = "Нажмите для открытия таблицы METAR или расшифровки"
    footerPanel.add(tafLabel)
    footerPanel.add(metarLabel)

    ownerLeftLabel.font = Font("Segoe UI", Font.PLAIN, 11)
    ownerRightLabel.font = Font("Segoe UI", Font.PLAIN, 11)
    ownerFooterPanel.isOpaque = false
    ownerFooterPanel.border = BorderFactory.createEmptyBorder(3, 4, 0, 4)
    ownerFooterPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    ownerFooterPanel.toolTipText = "О программе"
    ownerFooterPanel.add(ownerLeftLabel, BorderLayout.WEST)
    ownerFooterPanel.add(ownerRightLabel, BorderLayout.EAST)

    val southStack = JPanel(BorderLayout()).apply {
      isOpaque = false
      add(footerPanel, BorderLayout.CENTER)
      add(ownerFooterPanel, BorderLayout.SOUTH)
    }
    rootPanel.add(southStack, BorderLayout.SOUTH)
  }

  private fun buildRunwayColumn(
    headerLabel: JLabel,
    selector: java.awt.Component,
    windRose: RunwayWindRosePanel,
    rvrValue: JLabel,
    headwindField: JTextField,
    crosswindField: JTextField,
    statsPanel: JPanel,
  ): JPanel {
    val col = JPanel(BorderLayout(6, 6)).apply {
      border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
    }
    headerLabel.font = Font("Segoe UI", Font.BOLD, 18)
    val topBar = JPanel(BorderLayout(4, 2)).apply {
      isOpaque = false
      add(headerLabel, BorderLayout.CENTER)
      add(selector, BorderLayout.EAST)
    }
    col.add(topBar, BorderLayout.NORTH)
    col.add(windRose, BorderLayout.CENTER)

    configureReadonlyBox(headwindField)
    configureReadonlyBox(crosswindField)
    rvrValue.font = Font("Segoe UI", Font.BOLD, 12)
    statsPanel.isOpaque = false
    statsPanel.add(JLabel("Встр./Попут.:"))
    statsPanel.add(headwindField)
    statsPanel.add(JLabel("Боковой:"))
    statsPanel.add(crosswindField)

    val bottom = JPanel(BorderLayout(4, 4)).apply {
      isOpaque = false
      add(statsPanel, BorderLayout.CENTER)
      add(rvrValue, BorderLayout.SOUTH)
    }
    col.add(bottom, BorderLayout.SOUTH)
    return col
  }

  private fun wireActions() {
    titleButton.addActionListener {
      DesktopDialogs.showStationAndServerDialog(this, controller, initialTab = 0)
    }
    metarUtcLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        DesktopDialogs.showStationAndServerDialog(this@MeteoDesktopFrame, controller, initialTab = 1)
      }
    })
    stationCombo.addActionListener {
      if (suppressingEvents) return@addActionListener
      val item = stationCombo.selectedItem as? String ?: return@addActionListener
      val icao = item.substringBefore(" ").trim()
      if (icao.length == 4 && icao != controller.state.station.icao) {
        controller.selectStation(icao)
      }
    }
    sourceModeCombo.addActionListener {
      if (suppressingEvents) return@addActionListener
      val selected = DataSourceMode.entries.getOrElse(sourceModeCombo.selectedIndex) {
        DataSourceMode.AUTO_IP_FIRST
      }
      if (selected != controller.state.serverConfig.resolvedMode()) {
        controller.setDataSourceMode(selected, fetchNow = true)
      }
    }
    autoRefreshBadgeButton.addActionListener {
      DesktopDialogs.showStationAndServerDialog(this, controller, initialTab = 2)
    }
    leftRunwayCombo.addActionListener {
      if (suppressingEvents) return@addActionListener
      val idx = leftRunwayCombo.selectedIndex
      if (idx >= 0 && idx != controller.state.selectedRunwayPairIndex) {
        controller.selectRunwayPair(idx)
      }
    }
    favoriteButton.addActionListener { controller.toggleFavorite() }
    refreshButton.addActionListener { controller.refresh() }
    nightButton.addActionListener { controller.toggleNightMode() }

    unitSystemButton.addActionListener {
      val currentSys = UnitConverter.resolveUnitSystem(controller.state.windUnit, controller.state.pressureUnit)
      val nextSys = if (currentSys == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC
      controller.applyUnitSystem(nextSys)
    }
    windUnitButton.addActionListener { controller.toggleWindUnit() }
    pressureUnitButton.addActionListener { controller.togglePressureUnit() }
    myAirportsButton.addActionListener {
      setCenterView(
        if (currentCenterView == DesktopCenterView.MY_AIRPORTS) DesktopCenterView.BOARD
        else DesktopCenterView.MY_AIRPORTS,
      )
    }
    myAirportsBackBtn.addActionListener { setCenterView(DesktopCenterView.BOARD) }
    myAirportsOpenMapBtn.addActionListener { setCenterView(DesktopCenterView.MAP) }
    myAirportsToggleCurrentBtn.addActionListener { controller.toggleFavorite() }
    myAirportsAddFavBtn.addActionListener {
      val code = myAirportsAddIcaoField.text.trim().uppercase()
      if (code.length == 4 && code.all { it.isLetter() }) {
        controller.addFavorite(code, selectAfterAdd = true)
        myAirportsAddIcaoField.text = ""
      }
    }
    myAirportsAddIcaoField.addActionListener { myAirportsAddFavBtn.doClick() }

    mapButton.addActionListener {
      setCenterView(if (currentCenterView == DesktopCenterView.MAP) DesktopCenterView.BOARD else DesktopCenterView.MAP)
    }
    trendsButton.addActionListener {
      if (currentCenterView == DesktopCenterView.TRENDS) {
        setCenterView(DesktopCenterView.BOARD)
      } else {
        setCenterView(DesktopCenterView.TRENDS)
        val trendIcao = controller.state.selectedD3TrendIcao.ifBlank { controller.state.station.icao }
        controller.load24hHistoryForIcao(trendIcao, force = false)
      }
    }
    dualRoseButton.addActionListener { DesktopDialogs.showDualWindRoseDialog(this, controller) }
    crosswindButton.addActionListener { DesktopDialogs.showCrosswindCalculatorDialog(this, controller) }
    decoderButton.addActionListener { DesktopDialogs.showDecoderDialog(this, controller) }
    pressureConvButton.addActionListener { DesktopDialogs.showPressureConverterDialog(this, controller) }
    tableButton.addActionListener { DesktopDialogs.showMetarTableDialog(this, controller) }
    shareButton.addActionListener {
      val st = controller.state
      val payload = WeatherExportPayload(
        station = st.station,
        weather = st.weather,
        primaryRunwayCalc = st.primaryRunwayCalc,
        reciprocalRunwayCalc = st.reciprocalRunwayCalc,
        windUnit = st.windUnit,
        pressureUnit = st.pressureUnit,
        windTrend = st.windTrend,
        qnhTrend24h = st.qnhTrend24h,
        weatherHistory24h = st.weatherHistory24h,
      )
      val summary = WeatherReportFormatter.buildTextSummary(payload)
      Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(summary), null)
      controller.showStatusMessage("Сводка ${st.station.icao} скопирована в буфер для отправки")
    }
    exportButton.addActionListener { DesktopDialogs.showExportDialog(this, controller) }
    alertsButton.addActionListener { DesktopDialogs.showAlertsDialog(this, controller) }
    logButton.addActionListener { DesktopDialogs.showLogDialog(this, controller) }
    presetsButton.addActionListener { showPresetsPopup() }
    serverButton.addActionListener { DesktopDialogs.showStationAndServerDialog(this, controller, initialTab = 2) }

    coordToggleBtn.addActionListener {
      coordEntryPanel.isVisible = !coordEntryPanel.isVisible
      if (coordEntryPanel.isVisible) {
        latField.text = String.format(Locale.US, "%.4f", controller.state.station.latitude)
        lonField.text = String.format(Locale.US, "%.4f", controller.state.station.longitude)
      }
      searchBarPanel.revalidate()
      searchBarPanel.repaint()
    }
    coordFetchBtn.addActionListener {
      val lat = latField.text.trim().replace(',', '.').toDoubleOrNull()
      val lon = lonField.text.trim().replace(',', '.').toDoubleOrNull()
      if (lat == null || lon == null) {
        controller.fetchWeatherByCoordinates(999.0, 999.0)
      } else {
        controller.fetchWeatherByCoordinates(lat, lon)
      }
    }

    alertBannerOpenBtn.addActionListener {
      controller.state.latestWeatherAlert?.icao?.let { controller.openAlertStation(it) }
    }
    alertBannerOkBtn.addActionListener { controller.dismissLatestWeatherAlert() }
    bannerOkBtn.addActionListener { controller.dismissBanner() }

    searchField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) = refreshSearchSuggestions()
      override fun removeUpdate(e: DocumentEvent?) = refreshSearchSuggestions()
      override fun changedUpdate(e: DocumentEvent?) = refreshSearchSuggestions()
    })
    searchField.addActionListener { submitSearchQuery() }
    searchSubmitBtn.addActionListener { submitSearchQuery() }
    searchClearBtn.addActionListener {
      searchField.text = ""
      refreshSearchSuggestions()
    }
    recentSearchesClearBtn.addActionListener {
      controller.clearRecentSearches()
    }

    qnhField.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    qnhField.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        controller.togglePressureUnit()
      }
    })
    footerPanel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        DesktopDialogs.showMetarTableDialog(this@MeteoDesktopFrame, controller)
      }
    })
    ownerFooterPanel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent?) {
        DesktopDialogs.showStationAndServerDialog(this@MeteoDesktopFrame, controller, initialTab = 3)
      }
    })
  }

  fun setCenterView(view: DesktopCenterView) {
    currentCenterView = view
    centerCardLayout.show(centerDeckPanel, view.name)
    applyButtonTheme(controller.state)
    when (view) {
      DesktopCenterView.BOARD -> Unit
      DesktopCenterView.MAP -> mapPanel.applyState(controller.state)
      DesktopCenterView.TRENDS -> trendsPanel.applyState(controller.state)
      DesktopCenterView.MY_AIRPORTS -> rebuildMyAirportsBar(controller.state)
    }
  }

  fun setMapViewVisible(visible: Boolean) {
    setCenterView(if (visible) DesktopCenterView.MAP else DesktopCenterView.BOARD)
  }

  private fun submitSearchQuery() {
    val raw = searchField.text.trim().uppercase()
    val firstMatch = AirportCatalog.search(raw, controller.state.favoriteIcaos).firstOrNull()?.icao
    val target = when {
      raw.length == 4 && raw.all { it.isLetter() } -> raw
      firstMatch != null -> firstMatch
      else -> null
    }
    if (target != null) {
      controller.selectStation(target)
      searchField.text = ""
      refreshSearchSuggestions()
      if (currentCenterView == DesktopCenterView.MAP) {
        mapPanel.selectPin(target)
      }
    }
  }

  private fun refreshSearchSuggestions() {
    val query = searchField.text.trim().uppercase()
    val pal = controller.state.palette
    searchClearBtn.isVisible = query.isNotEmpty()
    searchSuggestionsPanel.removeAll()
    if (query.isEmpty()) {
      searchSuggestionsPanel.isVisible = false
      searchBarPanel.revalidate()
      searchBarPanel.repaint()
      return
    }
    val matches = AirportCatalog.search(query, controller.state.favoriteIcaos).take(8)
    matches.forEach { st ->
      val chip = JButton("${st.icao} · ${st.city}").apply {
        font = Font("Segoe UI", Font.BOLD, 11)
        DesktopThemeHelper.styleButton(
          btn = this,
          pal = pal,
          padding = Insets(3, 8, 3, 8),
        )
        addActionListener {
          controller.selectStation(st.icao)
          searchField.text = ""
          refreshSearchSuggestions()
          if (currentCenterView == DesktopCenterView.MAP) {
            mapPanel.selectPin(st.icao)
          }
        }
      }
      searchSuggestionsPanel.add(chip)
    }
    searchSuggestionsPanel.isVisible = matches.isNotEmpty()
    searchBarPanel.revalidate()
    searchBarPanel.repaint()
  }

  private fun rebuildMyAirportsBar(state: DesktopUiState) {
    val pal = state.palette
    myAirportsViewPanel.background = pal.panelSurface
    myAirportsViewPanel.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(10, 12, 10, 12),
    )
    myAirportsChipsPanel.background = pal.panelSurface
    myAirportsChipsScroll?.background = pal.panelSurface
    myAirportsChipsScroll?.viewport?.background = pal.panelSurface
    myAirportsChipsScroll?.border = BorderFactory.createLineBorder(pal.border, 1)
    myAirportsAddIcaoField.background = pal.fieldBackground
    myAirportsAddIcaoField.foreground = pal.textPrimary
    myAirportsAddIcaoField.caretColor = pal.textPrimary
    myAirportsAddIcaoField.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(pal.border, 1),
      BorderFactory.createEmptyBorder(3, 6, 3, 6),
    )
    myAirportsToggleCurrentBtn.text = if (state.isCurrentStationFavorite) "★ Убрать ${state.station.icao}" else "☆ В мои ${state.station.icao}"
    applyLabelTheme(myAirportsViewPanel, pal)

    myAirportsChipsPanel.removeAll()
    val favs = state.favoriteIcaos
    if (favs.isEmpty()) {
      myAirportsChipsPanel.add(
        JLabel("Список «Мои аэропорты» пуст. Введите код ICAO справа и нажмите «+ ICAO» или ☆ на панели.").apply {
          font = Font("Segoe UI", Font.PLAIN, 12)
          foreground = pal.textMuted
        },
      )
    } else {
      favs.forEach { icao ->
        val selected = icao.equals(state.station.icao, ignoreCase = true)
        val city = AirportCatalog.findKnown(icao)?.city
        val label = if (city != null) "★ $icao · $city" else "★ $icao"
        val chipGroup = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0)).apply {
          isOpaque = false
        }
        val chip = JButton(label).apply {
          font = Font("Segoe UI", Font.BOLD, 12)
          if (selected) {
            putClientProperty("preserveForeground", true)
            foreground = pal.accentCyan
          } else {
            putClientProperty("preserveForeground", false)
          }
          DesktopThemeHelper.styleButton(
            btn = this,
            pal = pal,
            isActive = selected,
            accentBorder = if (selected) pal.accentCyan else null,
            padding = Insets(6, 12, 6, 12),
          )
          addActionListener {
            controller.selectStation(icao)
            setCenterView(DesktopCenterView.BOARD)
          }
        }
        val rmBtn = JButton("×").apply {
          font = Font("Segoe UI", Font.BOLD, 12)
          toolTipText = "Удалить $icao из избранного"
          DesktopThemeHelper.styleButton(
            btn = this,
            pal = pal,
            padding = Insets(6, 7, 6, 7),
          )
          addActionListener { controller.removeFavorite(icao) }
        }
        chipGroup.add(chip)
        chipGroup.add(rmBtn)
        myAirportsChipsPanel.add(chipGroup)
      }
    }
    myAirportsViewPanel.revalidate()
    myAirportsViewPanel.repaint()
  }

  private fun rebuildRecentSearchesBar(state: DesktopUiState) {
    val pal = state.palette
    val items = state.recentSearches.take(5)
    recentSearchesChipsPanel.removeAll()
    recentSearchesPanel.isVisible = items.isNotEmpty()
    items.forEach { icao ->
      val selected = icao.equals(state.station.icao, ignoreCase = true)
      val city = AirportCatalog.findKnown(icao)?.city
      val label = if (city != null) "$icao · $city" else icao
      val chip = JButton(label).apply {
        font = Font("Segoe UI", Font.BOLD, 11)
        if (selected) {
          putClientProperty("preserveForeground", true)
          foreground = pal.metarAmber
        } else {
          putClientProperty("preserveForeground", false)
        }
        DesktopThemeHelper.styleButton(
          btn = this,
          pal = pal,
          isActive = selected,
          accentBorder = if (selected) pal.metarAmber else null,
          padding = Insets(2, 8, 2, 8),
        )
        addActionListener {
          controller.selectStation(icao)
          if (currentCenterView == DesktopCenterView.TRENDS) {
            controller.selectD3TrendAirport(icao)
          } else if (currentCenterView == DesktopCenterView.MAP) {
            mapPanel.selectPin(icao)
          }
        }
      }
      recentSearchesChipsPanel.add(chip)
    }
    recentSearchesPanel.revalidate()
    recentSearchesPanel.repaint()
  }

  private fun applyState(state: DesktopUiState) {
    suppressingEvents = true
    try {
      val pal = state.palette
      rootPanel.background = pal.bezelSurface
      toolbarPanel.background = pal.panelSurface
      toolbarPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(2, 0, 0, 0, pal.accentCyan),
        BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(pal.border, 1),
          BorderFactory.createEmptyBorder(6, 10, 6, 10),
        ),
      )
      searchBarPanel.background = pal.panelSurface
      searchBarPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(5, 10, 5, 10),
      )
      listOf(searchField, latField, lonField).forEach { tf ->
        tf.background = pal.fieldBackground
        tf.foreground = pal.textPrimary
        tf.caretColor = pal.textPrimary
        tf.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(pal.border, 1),
          BorderFactory.createEmptyBorder(4, 8, 4, 8),
        )
      }
      detectedLocationLabel.text = state.detectedLocationSummary?.let { "Гео: $it" }.orEmpty()
      detectedLocationLabel.foreground = pal.accentCyan
      applyLabelTheme(searchBarPanel, pal)
      DesktopThemeHelper.styleComboBox(stationCombo, pal)
      DesktopThemeHelper.styleComboBox(sourceModeCombo, pal)
      DesktopThemeHelper.styleComboBox(leftRunwayCombo, pal)
      rebuildRecentSearchesBar(state)
      rebuildMyAirportsBar(state)

      boardPanel.background = pal.bezelSurface
      centerPanel.background = pal.panelSurface
      centerPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(2, 0, 0, 0, pal.metarAmber),
        BorderFactory.createLineBorder(pal.border, 1),
      )
      footerPanel.background = pal.panelSurface
      footerPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(2, 0, 0, 0, pal.accentCyan),
        BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(pal.border, 1),
          BorderFactory.createEmptyBorder(7, 10, 7, 10),
        ),
      )

      val latestAlert = state.latestWeatherAlert
      alertBannerPanel.background = pal.fieldBackground
      val alertAccent = if (latestAlert?.severity == com.example.model.WeatherAlertSeverity.CRITICAL) {
        Color(0xE5, 0x39, 0x35)
      } else {
        pal.metarAmber
      }
      alertBannerPanel.border = BorderFactory.createLineBorder(alertAccent, 1)
      alertBannerLabel.foreground = alertAccent
      alertBannerPanel.isVisible = latestAlert != null
      if (latestAlert != null) {
        alertBannerLabel.text = "⚠ ${latestAlert.icao} (${latestAlert.observationTimeUtc}): ${latestAlert.summary}"
      }

      bannerPanel.background = pal.fieldBackground
      bannerPanel.border = BorderFactory.createLineBorder(pal.metarAmber, 1)
      bannerLabel.foreground = pal.metarAmber
      bannerPanel.isVisible = !state.statusBanner.isNullOrBlank()
      bannerLabel.text = state.statusBanner.orEmpty()

      // Rebuild station selector items keeping current + Turkmenistan presets + favorites
      val stationItems = buildList {
        val added = mutableSetOf<String>()
        AirportCatalog.presets.forEach { p ->
          if (added.add(p.icao)) add("${p.icao} — ${p.city}")
        }
        state.favoriteIcaos.forEach { fav ->
          if (added.add(fav)) add(fav)
        }
        if (added.add(state.station.icao)) {
          add("${state.station.icao} — ${state.station.city}")
        }
      }
      stationCombo.removeAllItems()
      stationItems.forEach { stationCombo.addItem(it) }
      val targetPrefix = state.station.icao
      for (i in 0 until stationCombo.itemCount) {
        if (stationCombo.getItemAt(i).startsWith(targetPrefix)) {
          stationCombo.selectedIndex = i
          break
        }
      }

      favoriteButton.text = if (state.isCurrentStationFavorite) "★" else "☆"
      metarUtcLabel.text = "METAR ${state.weather.observationTimeUtc}"
      metarUtcLabel.foreground = pal.metarAmber
      val modeIdx = state.serverConfig.resolvedMode().ordinal
      if (modeIdx in 0 until sourceModeCombo.itemCount) {
        sourceModeCombo.selectedIndex = modeIdx
      }
      autoRefreshBadgeButton.text = if (state.autoRefreshEnabled) {
        "АВТО ${state.autoRefreshIntervalMinutes}м"
      } else {
        "РУЧН."
      }
      refreshButton.text = if (state.isLoading) "Загрузка..." else "Обновить"
      refreshButton.isEnabled = !state.isLoading
      nightButton.text = if (state.isNightMode) "День" else "Ночь"

      val curUnitSys = UnitConverter.resolveUnitSystem(state.windUnit, state.pressureUnit)
      unitSystemButton.text = when (curUnitSys) {
        UnitSystem.METRIC -> "Metric (м/с)"
        UnitSystem.IMPERIAL -> "Imperial (kt)"
        else -> "Ед.: Смеш."
      }
      windUnitButton.text = state.windUnit.label
      pressureUnitButton.text = state.pressureUnit.label
      alertsButton.text = if (state.unreadAlertCount > 0) "Тревога (${state.unreadAlertCount})" else "Оповещ."
      presetsButton.text = if (state.savedPresets.isEmpty()) "Профили" else "Профили (${state.savedPresets.size})"
      applyButtonTheme(state)

      // Runway selector
      leftRunwayCombo.removeAllItems()
      state.station.runways.forEach { pair ->
        leftRunwayCombo.addItem("${pair.primaryDesignator}/${pair.reciprocalDesignator}")
      }
      if (state.selectedRunwayPairIndex in 0 until leftRunwayCombo.itemCount) {
        leftRunwayCombo.selectedIndex = state.selectedRunwayPairIndex
      }

      // Runway Panels
      updateRunwayPanel(
        header = leftRunwayHeader,
        windRose = leftWindRose,
        headwindField = leftHeadwindField,
        crosswindField = leftCrosswindField,
        rvrLabel = leftRvrValue,
        calc = state.primaryRunwayCalc,
        state = state,
      )
      updateRunwayPanel(
        header = rightRunwayHeader,
        windRose = rightWindRose,
        headwindField = rightHeadwindField,
        crosswindField = rightCrosswindField,
        rvrLabel = rightRvrValue,
        calc = state.reciprocalRunwayCalc,
        state = state,
      )
      rightRunwaySpacer.text = "${state.station.icao} · ${state.station.elevationMeters} м"
      rightRunwaySpacer.foreground = pal.textSecondary

      // Center Telemetry
      val w = state.weather
      localClockLabel.text = state.liveLocalClock
      localClockLabel.foreground = pal.textPrimary
      val cat = AirportMapProjection.evaluateFlightCategory(w)
      val catColor = when (cat) {
        FlightCategory.VFR -> pal.headwindPositive
        FlightCategory.MVFR -> pal.metarAmber
        FlightCategory.IFR -> pal.tailwindNegative
        FlightCategory.UNKNOWN -> pal.textMuted
      }
      flightCatBadge.text = cat.code
      flightCatBadge.background = Color(catColor.red, catColor.green, catColor.blue, 36)
      flightCatBadge.foreground = catColor
      flightCatBadge.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(catColor, 1),
        BorderFactory.createEmptyBorder(2, 8, 2, 8),
      )
      stationSubtitleLabel.text = "${state.station.icao} · ${state.station.city} (UTC)"
      stationSubtitleLabel.foreground = pal.textSecondary

      visField.text = when {
        w.isCavok -> "CAVOK (> 10 км)"
        w.visibilityMeters != null && w.visibilityMeters >= 9999 -> "≥ 10 км"
        w.visibilityMeters != null -> "${w.visibilityMeters} м"
        else -> "--"
      }
      cloudsField.text = if (w.clouds.isEmpty()) {
        if (w.isCavok) "Нет значимой" else "--"
      } else {
        w.clouds.joinToString(" ") { "${it.cover} ${it.baseFeet} ft" }
      }
      ceilingField.text = w.ceilingFeet?.let { "$it ft (${w.ceilingCode ?: ""})" } ?: "--"
      weatherField.text = w.weatherPhenomena
      tempField.text = w.temperatureC?.let { "$it °C" } ?: "--"
      dewField.text = w.dewPointC?.let { "$it °C" } ?: "--"
      qnhField.text = when (state.pressureUnit) {
        PressureUnit.HPA -> w.qnhHpa?.let { "$it гПа" } ?: "--"
        PressureUnit.INHG -> w.qnhInHg?.let { String.format(Locale.forLanguageTag("ru-RU"), "%.2f дм", it) } ?: "--"
      }
      qfeField.text = w.qfeMmHg?.let { "$it мм / ${w.qfeHpa ?: "--"} гПа" } ?: "--"
      sunField.text = "${w.sunriseLocal} / ${w.sunsetLocal}"
      sourceField.text = w.sourceLabel
      windTrendPanel.updateData(state.windTrend, state.windUnit, pal)

      listOf(
        visField, cloudsField, ceilingField, weatherField,
        tempField, dewField, qnhField, qfeField, sunField, sourceField,
        leftHeadwindField, leftCrosswindField, rightHeadwindField, rightCrosswindField,
      ).forEach { tf ->
        tf.background = pal.fieldBackground
        tf.foreground = pal.textPrimary
        tf.border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(pal.border, 1),
          BorderFactory.createEmptyBorder(2, 6, 2, 6),
        )
      }
      applyLabelTheme(centerPanel, pal)
      applyLabelTheme(boardPanel, pal)

      tafLabel.text = w.rawTaf
      tafLabel.foreground = pal.textSecondary
      metarLabel.text = w.rawMetar
      metarLabel.foreground = pal.textPrimary
      ownerLeftLabel.foreground = pal.textMuted
      ownerRightLabel.foreground = pal.textMuted

      mapPanel.applyState(state)
      trendsPanel.applyState(state)
    } finally {
      suppressingEvents = false
    }
  }

  private fun applyButtonTheme(state: DesktopUiState) {
    val pal = state.palette
    val standardButtons = listOf(
      titleButton, favoriteButton, autoRefreshBadgeButton, refreshButton, nightButton,
      unitSystemButton, windUnitButton, pressureUnitButton, myAirportsButton, mapButton, trendsButton,
      dualRoseButton, crosswindButton, decoderButton, pressureConvButton, tableButton, shareButton, exportButton,
      alertsButton, logButton, presetsButton, serverButton,
      coordToggleBtn, coordFetchBtn,
      searchClearBtn, recentSearchesClearBtn, alertBannerOpenBtn, alertBannerOkBtn, bannerOkBtn,
      myAirportsBackBtn, myAirportsOpenMapBtn, myAirportsToggleCurrentBtn, myAirportsAddFavBtn,
    )
    standardButtons.forEach { btn ->
      btn.putClientProperty("preserveForeground", false)
      DesktopThemeHelper.styleButton(
        btn = btn,
        pal = pal,
        padding = Insets(4, 8, 4, 8),
      )
    }
    favoriteButton.putClientProperty("preserveForeground", true)
    favoriteButton.foreground = if (state.isCurrentStationFavorite) pal.metarAmber else pal.textMuted
    autoRefreshBadgeButton.putClientProperty("preserveForeground", true)
    autoRefreshBadgeButton.foreground = if (state.autoRefreshEnabled) pal.accentCyan else pal.textMuted
    alertsButton.putClientProperty("preserveForeground", true)
    alertsButton.foreground = if (state.unreadAlertCount > 0) pal.tailwindNegative else pal.textPrimary

    if (currentCenterView == DesktopCenterView.MAP) {
      DesktopThemeHelper.styleButton(mapButton, pal, isActive = true, accentBorder = pal.metarAmber, padding = Insets(4, 8, 4, 8))
    }
    if (currentCenterView == DesktopCenterView.TRENDS) {
      DesktopThemeHelper.styleButton(trendsButton, pal, isActive = true, accentBorder = pal.accentCyan, padding = Insets(4, 8, 4, 8))
    }
    if (currentCenterView == DesktopCenterView.MY_AIRPORTS) {
      DesktopThemeHelper.styleButton(myAirportsButton, pal, isActive = true, accentBorder = pal.accentCyan, padding = Insets(4, 8, 4, 8))
    }
    if (coordEntryPanel.isVisible) {
      coordToggleBtn.putClientProperty("preserveForeground", true)
      coordToggleBtn.foreground = pal.accentCyan
      DesktopThemeHelper.styleButton(coordToggleBtn, pal, isActive = true, padding = Insets(4, 8, 4, 8))
    }
    DesktopThemeHelper.styleAccentButton(
      btn = searchSubmitBtn,
      bg = pal.metarAmber,
      fg = Color.WHITE,
      padding = Insets(5, 12, 5, 12),
    )
  }

  private fun updateRunwayPanel(
    header: JLabel,
    windRose: RunwayWindRosePanel,
    headwindField: JTextField,
    crosswindField: JTextField,
    rvrLabel: JLabel,
    calc: RunwayCalculation,
    state: DesktopUiState,
  ) {
    val pal = state.palette
    header.text = "ВПП ${calc.designator} (${String.format(Locale.US, "%03d°", calc.headingDeg)})"
    header.foreground = pal.textPrimary
    windRose.updateData(calc, state.weather.wind, state.windUnit, pal, state.metarFetchSeq)

    val headVal = UnitConverter.convertWindFromKnots(calc.headwindKt, state.windUnit)
    val crossVal = UnitConverter.convertWindFromKnots(calc.crosswindKt, state.windUnit)
    headwindField.text = String.format(Locale.US, "%.1f %s", headVal, state.windUnit.label)
    crosswindField.text = String.format(Locale.US, "%.1f %s", crossVal, state.windUnit.label)
    rvrLabel.text = "RVR: ${calc.rvrMeters?.let { "$it м" } ?: "--"}"
    rvrLabel.foreground = pal.textSecondary

    (windRose.parent as? JPanel)?.apply {
      background = pal.panelSurface
      border = BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(2, 0, 0, 0, pal.accentCyan),
        BorderFactory.createLineBorder(pal.border, 1),
      )
    }
  }

  private fun applyLabelTheme(container: java.awt.Container, pal: DesktopPalette) {
    container.components.forEach { comp ->
      if (comp is JLabel &&
        comp !== localClockLabel &&
        comp !== metarUtcLabel &&
        comp !== bannerLabel &&
        comp !== flightCatBadge &&
        comp !== leftRunwayHeader &&
        comp !== rightRunwayHeader &&
        comp !== detectedLocationLabel
      ) {
        comp.foreground = pal.textSecondary
      } else if (comp is java.awt.Container && comp !== mapPanel && comp !== trendsPanel) {
        applyLabelTheme(comp, pal)
      }
    }
  }

  private fun createReadonlyField(): JTextField = JTextField("--").apply {
    configureReadonlyBox(this)
  }

  private fun configureReadonlyBox(tf: JTextField) {
    tf.isEditable = false
    tf.horizontalAlignment = SwingConstants.CENTER
    tf.font = Font("Segoe UI", Font.BOLD, 12)
  }

  private fun showPresetsPopup() {
    val cur = controller.state
    val pair = cur.activeRunwayPair
    val menu = JPopupMenu()
    menu.add(
      JMenuItem("+ Сохранить (${cur.station.icao} · ВПП ${pair.primaryDesignator}/${pair.reciprocalDesignator})").apply {
        addActionListener { controller.saveCurrentSetup() }
      },
    )
    if (cur.savedPresets.isNotEmpty()) {
      menu.addSeparator()
      cur.savedPresets.forEach { preset ->
        menu.add(
          JMenuItem("★ ${preset.name}").apply {
            addActionListener { controller.applySavedPreset(preset.id) }
          },
        )
      }
    }
    menu.addSeparator()
    menu.add(
      JMenuItem("Управление профилями…").apply {
        addActionListener { DesktopDialogs.showStationAndServerDialog(this@MeteoDesktopFrame, controller, initialTab = 0) }
      },
    )
    menu.show(presetsButton, 0, presetsButton.height)
  }
}

class WindTrendSparklinePanel : JPanel() {
  private var trend: WindTrendSummary = WindTrendAnalyzer.analyze("UTAM", emptyList())
  private var windUnit: WindSpeedUnit = WindSpeedUnit.KT
  private var palette: DesktopPalette = DayDesktopPalette

  init {
    preferredSize = Dimension(240, 74)
    minimumSize = Dimension(180, 68)
    isOpaque = true
  }

  fun updateData(
    newTrend: WindTrendSummary,
    newUnit: WindSpeedUnit,
    newPalette: DesktopPalette,
  ) {
    trend = newTrend
    windUnit = newUnit
    palette = newPalette
    background = newPalette.fieldBackground
    border = BorderFactory.createLineBorder(newPalette.border, 1)
    repaint()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

      val w = width
      val h = height
      if (w < 40 || h < 30) return

      val accent = when (trend.direction) {
        WindTrendDirection.INCREASING -> Color(0xE5, 0x39, 0x35)
        WindTrendDirection.DECREASING -> Color(0x2E, 0x7D, 0x32)
        WindTrendDirection.STEADY -> palette.metarAmber
        WindTrendDirection.INSUFFICIENT -> palette.textMuted
      }

      val deltaPrefix = when {
        trend.deltaKt > 0 -> "+"
        trend.deltaKt < 0 -> "-"
        else -> ""
      }
      val deltaStr = UnitConverter.formatWindSpeedValue(abs(trend.deltaKt), windUnit)
      val badge = when (trend.direction) {
        WindTrendDirection.INSUFFICIENT -> "${trend.direction.arrow} ${trend.direction.label}"
        WindTrendDirection.STEADY -> "${trend.direction.arrow} ${trend.direction.label}"
        else -> "${trend.direction.arrow} ${trend.direction.label} $deltaPrefix$deltaStr ${windUnit.label}"
      }

      g2.font = Font("Segoe UI", Font.BOLD, 11)
      g2.color = palette.textSecondary
      g2.drawString("Тренд ветра", 8, 15)

      val badgeFm = g2.fontMetrics
      val badgeW = badgeFm.stringWidth(badge)
      g2.color = accent
      g2.drawString(badge, (w - badgeW - 8).coerceAtLeast(90), 15)

      val minValStr = UnitConverter.formatWindSpeedValue(trend.minSpeedKt, windUnit)
      val maxValStr = UnitConverter.formatWindSpeedValue(trend.maxSpeedKt, windUnit)
      val footer = when {
        trend.entries.isEmpty() -> "Ожидание METAR"
        trend.entries.size == 1 -> "1 замер (${trend.entries.first().observationTimeUtc})"
        else -> "$minValStr–$maxValStr ${windUnit.label} · ${trend.entries.size} зам."
      }
      g2.font = Font("Segoe UI", Font.PLAIN, 10)
      g2.color = palette.textMuted
      g2.drawString(footer, 8, h - 5)

      val plotLeft = 10f
      val plotRight = (w - 10f).coerceAtLeast(plotLeft + 10f)
      val plotTop = 22f
      val plotBottom = (h - 18f).coerceAtLeast(plotTop + 10f)
      val plotW = plotRight - plotLeft
      val plotH = plotBottom - plotTop

      g2.color = Color(palette.border.red, palette.border.green, palette.border.blue, 120)
      g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, floatArrayOf(4f, 4f), 0f)
      val midY = (plotTop + plotBottom) / 2f
      g2.drawLine(plotLeft.toInt(), midY.toInt(), plotRight.toInt(), midY.toInt())

      val entries = trend.entries
      if (entries.isEmpty()) return

      val minSpeed = entries.minOf { it.speedKt }.toFloat()
      val maxSpeed = entries.maxOf { maxOf(it.speedKt, it.gustKt ?: it.speedKt) }.toFloat()
      val span = (maxSpeed - minSpeed).coerceAtLeast(2f)

      val xs = mutableListOf<Float>()
      val ys = mutableListOf<Float>()
      if (entries.size == 1) {
        xs.add(plotLeft)
        ys.add(midY)
        xs.add(plotRight)
        ys.add(midY)
      } else {
        entries.forEachIndexed { idx, entry ->
          val x = plotLeft + (idx.toFloat() / (entries.size - 1).toFloat()) * plotW
          val norm = ((entry.speedKt - minSpeed) / span).coerceIn(0f, 1f)
          val y = plotTop + plotH * (1f - norm)
          xs.add(x)
          ys.add(y)
        }
      }

      val fillPath = Path2D.Float().apply {
        moveTo(xs.first(), ys.first())
        for (i in 1 until xs.size) lineTo(xs[i], ys[i])
        lineTo(xs.last(), plotBottom)
        lineTo(xs.first(), plotBottom)
        closePath()
      }
      g2.paint = GradientPaint(
        0f,
        plotTop,
        Color(accent.red, accent.green, accent.blue, 70),
        0f,
        plotBottom,
        Color(accent.red, accent.green, accent.blue, 5),
      )
      g2.fill(fillPath)

      val strokePath = Path2D.Float().apply {
        moveTo(xs.first(), ys.first())
        for (i in 1 until xs.size) lineTo(xs[i], ys[i])
      }
      g2.color = accent
      g2.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      g2.draw(strokePath)

      if (entries.size >= 2) {
        for (i in xs.indices) {
          val r = if (i == xs.lastIndex) 4 else 2
          g2.color = if (i == xs.lastIndex) accent else palette.panelSurface
          g2.fillOval((xs[i] - r).toInt(), (ys[i] - r).toInt(), r * 2, r * 2)
          if (i != xs.lastIndex) {
            g2.color = accent
            g2.stroke = BasicStroke(1.2f)
            g2.drawOval((xs[i] - r).toInt(), (ys[i] - r).toInt(), r * 2, r * 2)
          }
        }
      } else {
        g2.color = accent
        g2.fillOval((xs.last() - 3).toInt(), (ys.last() - 3).toInt(), 6, 6)
      }
    } finally {
      g2.dispose()
    }
  }
}
