package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.domain.AirportMapProjection
import com.example.domain.FlightCategory
import com.example.domain.UnitConverter
import com.example.domain.WindTrendAnalyzer
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
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
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class MeteoDesktopFrame(
  private val controller: DesktopController,
) : JFrame("${AuthorBrand.APP_TITLE} — Авиационная метеостанция · ${AuthorBrand.AUTHOR_NAME}") {

  private var suppressingEvents = false
  private var isMapVisible = false

  // Toolbar controls
  private val rootPanel = JPanel(BorderLayout(8, 8))
  private val toolbarPanel = JPanel(BorderLayout(8, 4))
  private val titleButton = JButton(AuthorBrand.APP_TITLE)
  private val stationCombo = JComboBox<String>()
  private val favoriteButton = JButton("☆")
  private val metarUtcLabel = JLabel("METAR --:--Z")
  private val sourceModeCombo = JComboBox(DataSourceMode.entries.map { it.shortLabel }.toTypedArray())
  private val refreshButton = JButton("Обновить")
  private val nightButton = JButton("Ночь")
  private val windUnitButton = JButton("kt")
  private val pressureUnitButton = JButton("гПа")
  private val mapButton = JButton("Карта")
  private val tableButton = JButton("Таблица")
  private val alertsButton = JButton("Оповещ.")
  private val logButton = JButton("Лог")
  private val presetsButton = JButton("Профили")
  private val serverButton = JButton("Сервер")

  // Top ICAO Search Bar
  private val searchBarPanel = JPanel(BorderLayout(8, 4))
  private val searchField = JTextField()
  private val searchClearBtn = JButton("×")
  private val searchSubmitBtn = JButton("Найти METAR")
  private val searchSuggestionsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 2))

  // Weather Alert Banner
  private val alertBannerPanel = JPanel(BorderLayout(8, 4))
  private val alertBannerLabel = JLabel()
  private val alertBannerOpenBtn = JButton("Открыть")
  private val alertBannerOkBtn = JButton("OK")

  // Status Banner
  private val bannerPanel = JPanel(BorderLayout(8, 4))
  private val bannerLabel = JLabel()
  private val bannerOkBtn = JButton("OK")

  // Center Deck: Board vs Airport Map View
  private val centerCardLayout = CardLayout()
  private val centerDeckPanel = JPanel(centerCardLayout)
  private val boardPanel = JPanel(GridBagLayout())
  private val mapPanel = AirportMapDesktopPanel(
    onBackToBoard = { setMapViewVisible(false) },
    onOpenStationOnBoard = { icao ->
      controller.selectStation(icao)
      setMapViewVisible(false)
    },
    onRefreshAllStations = { controller.refreshMapWeather() },
    onRefreshSingleStation = { icao -> controller.fetchStationWeatherForMap(icao) },
    onToggleFavorite = { icao -> controller.toggleFavorite(icao) },
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
  private val localClockLabel = JLabel("--:--", SwingConstants.CENTER)
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
  private val footerPanel = JPanel(GridLayout(2, 1, 4, 4))
  private val tafLabel = JLabel("TAF: --")
  private val metarLabel = JLabel("METAR: --")
  private val ownerFooterPanel = JPanel(BorderLayout())
  private val ownerLeftLabel = JLabel("${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · ${AuthorBrand.SUBTITLE}")
  private val ownerRightLabel = JLabel("${AuthorBrand.COPYRIGHT_LINE} · ${AuthorBrand.AUTHOR_EMAIL}")

  init {
    defaultCloseOperation = EXIT_ON_CLOSE
    minimumSize = Dimension(960, 620)
    setSize(1220, 740)
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

    // Toolbar Left
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

    // Toolbar Right
    val rightTool = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 2)).apply {
      isOpaque = false
      metarUtcLabel.font = Font("Segoe UI", Font.BOLD, 12)
      metarUtcLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      sourceModeCombo.font = Font("Segoe UI", Font.BOLD, 11)
      sourceModeCombo.toolTipText = "Источник данных: IP-сервер и/или Интернет-сайты"
      add(metarUtcLabel)
      add(sourceModeCombo)
      add(refreshButton)
      add(nightButton)
      add(windUnitButton)
      add(pressureUnitButton)
      add(mapButton)
      add(tableButton)
      add(alertsButton)
      add(logButton)
      add(presetsButton)
      add(serverButton)
    }

    toolbarPanel.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    toolbarPanel.add(leftTool, BorderLayout.WEST)
    toolbarPanel.add(rightTool, BorderLayout.CENTER)

    // Top ICAO Search Bar
    val searchPromptLabel = JLabel("ICAO:").apply {
      font = Font("Segoe UI", Font.BOLD, 12)
    }
    searchField.font = Font("Segoe UI", Font.BOLD, 13)
    searchField.toolTipText = "Введите 4-буквенный код ICAO (напр. UTAA, OMDB, KJFK) или название города"
    searchClearBtn.font = Font("Segoe UI", Font.BOLD, 13)
    searchClearBtn.isVisible = false
    searchSubmitBtn.font = Font("Segoe UI", Font.BOLD, 12)
    val searchInputWrap = JPanel(BorderLayout(6, 0)).apply {
      isOpaque = false
      add(searchPromptLabel, BorderLayout.WEST)
      add(searchField, BorderLayout.CENTER)
      add(
        JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
          isOpaque = false
          add(searchClearBtn)
          add(searchSubmitBtn)
        },
        BorderLayout.EAST,
      )
    }
    searchSuggestionsPanel.isOpaque = false
    searchSuggestionsPanel.isVisible = false
    searchBarPanel.border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
    searchBarPanel.add(searchInputWrap, BorderLayout.CENTER)
    searchBarPanel.add(searchSuggestionsPanel, BorderLayout.SOUTH)

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
      add(Box.createVerticalStrut(4))
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
      localClockLabel.font = Font("Consolas", Font.BOLD, 30)
      flightCatBadge.font = Font("Segoe UI", Font.BOLD, 11)
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
      add(JLabel("Восх. / Зак.:")); add(sunField)
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
    centerDeckPanel.add(boardPanel, "BOARD")
    centerDeckPanel.add(mapPanel, "MAP")
    centerCardLayout.show(centerDeckPanel, "BOARD")
    rootPanel.add(centerDeckPanel, BorderLayout.CENTER)

    // Footer
    tafLabel.font = Font("Consolas", Font.PLAIN, 12)
    metarLabel.font = Font("Consolas", Font.BOLD, 12)
    footerPanel.border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    footerPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    footerPanel.toolTipText = "Нажмите для открытия таблицы METAR"
    footerPanel.add(tafLabel)
    footerPanel.add(metarLabel)

    ownerLeftLabel.font = Font("Segoe UI", Font.PLAIN, 11)
    ownerRightLabel.font = Font("Segoe UI", Font.PLAIN, 11)
    ownerFooterPanel.isOpaque = false
    ownerFooterPanel.border = BorderFactory.createEmptyBorder(3, 4, 0, 4)
    ownerFooterPanel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    ownerFooterPanel.toolTipText = "Информация о приложении и авторе"
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
    windUnitButton.addActionListener { controller.toggleWindUnit() }
    pressureUnitButton.addActionListener { controller.togglePressureUnit() }
    mapButton.addActionListener { setMapViewVisible(!isMapVisible) }
    tableButton.addActionListener { DesktopDialogs.showMetarTableDialog(this, controller) }
    alertsButton.addActionListener { DesktopDialogs.showAlertsDialog(this, controller) }
    logButton.addActionListener { DesktopDialogs.showLogDialog(this, controller) }
    presetsButton.addActionListener { showPresetsPopup() }
    serverButton.addActionListener { DesktopDialogs.showStationAndServerDialog(this, controller, initialTab = 2) }
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

  fun setMapViewVisible(visible: Boolean) {
    isMapVisible = visible
    centerCardLayout.show(centerDeckPanel, if (visible) "MAP" else "BOARD")
    applyButtonTheme(controller.state)
    if (visible) {
      mapPanel.applyState(controller.state)
    }
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
      if (isMapVisible) {
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
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        background = pal.fieldBackground
        foreground = pal.textPrimary
        border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(pal.border, 1),
          BorderFactory.createEmptyBorder(3, 8, 3, 8),
        )
        addActionListener {
          controller.selectStation(st.icao)
          searchField.text = ""
          refreshSearchSuggestions()
          if (isMapVisible) {
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

  private fun applyState(state: DesktopUiState) {
    suppressingEvents = true
    try {
      val pal = state.palette
      rootPanel.background = pal.bezelSurface
      toolbarPanel.background = pal.panelSurface
      toolbarPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(6, 10, 6, 10),
      )
      searchBarPanel.background = pal.panelSurface
      searchBarPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(5, 10, 5, 10),
      )
      searchField.background = pal.fieldBackground
      searchField.foreground = pal.textPrimary
      searchField.caretColor = pal.textPrimary
      searchField.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(4, 8, 4, 8),
      )
      applyLabelTheme(searchBarPanel, pal)

      boardPanel.background = pal.bezelSurface
      centerPanel.background = pal.panelSurface
      centerPanel.border = BorderFactory.createLineBorder(pal.border, 1)
      footerPanel.background = pal.panelSurface
      footerPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(6, 10, 6, 10),
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
      refreshButton.text = if (state.isLoading) "Загрузка..." else "Обновить"
      refreshButton.isEnabled = !state.isLoading
      nightButton.text = if (state.isNightMode) "День" else "Ночь"
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
      stationSubtitleLabel.text = "${state.station.icao} · ${state.station.city} (${state.station.timezoneLabel})"
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
    } finally {
      suppressingEvents = false
    }
  }

  private fun applyButtonTheme(state: DesktopUiState) {
    val pal = state.palette
    val standardButtons = listOf(
      titleButton, favoriteButton, refreshButton, nightButton,
      windUnitButton, pressureUnitButton, mapButton, tableButton,
      alertsButton, logButton, presetsButton, serverButton,
      searchClearBtn, alertBannerOpenBtn, alertBannerOkBtn, bannerOkBtn,
    )
    standardButtons.forEach { btn ->
      btn.isFocusPainted = false
      btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      btn.background = pal.buttonBackground
      btn.foreground = pal.textPrimary
      btn.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(4, 9, 4, 9),
      )
    }
    favoriteButton.foreground = if (state.isCurrentStationFavorite) pal.metarAmber else pal.textMuted
    alertsButton.foreground = if (state.unreadAlertCount > 0) pal.tailwindNegative else pal.textPrimary
    if (isMapVisible) {
      mapButton.background = pal.buttonActiveBackground
      mapButton.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.metarAmber, 2),
        BorderFactory.createEmptyBorder(4, 9, 4, 9),
      )
    }
    searchSubmitBtn.isFocusPainted = false
    searchSubmitBtn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    searchSubmitBtn.background = pal.metarAmber
    searchSubmitBtn.foreground = Color.WHITE
    searchSubmitBtn.border = BorderFactory.createEmptyBorder(5, 12, 5, 12)
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
    windRose.updateData(calc, state.weather.wind, state.windUnit, pal)

    val headVal = UnitConverter.convertWindFromKnots(calc.headwindKt, state.windUnit)
    val crossVal = UnitConverter.convertWindFromKnots(calc.crosswindKt, state.windUnit)
    headwindField.text = String.format(Locale.US, "%.1f %s", headVal, state.windUnit.label)
    crosswindField.text = String.format(Locale.US, "%.1f %s", crossVal, state.windUnit.label)
    rvrLabel.text = "RVR: ${calc.rvrMeters?.let { "$it м" } ?: "--"}"
    rvrLabel.foreground = pal.textSecondary

    (windRose.parent as? JPanel)?.apply {
      background = pal.panelSurface
      border = BorderFactory.createLineBorder(pal.border, 1)
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
        comp !== rightRunwayHeader
      ) {
        comp.foreground = pal.textSecondary
      } else if (comp is java.awt.Container && comp !== mapPanel) {
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
