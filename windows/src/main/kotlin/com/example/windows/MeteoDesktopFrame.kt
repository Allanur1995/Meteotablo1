package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.PressureUnit
import com.example.model.RunwayCalculation
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
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

class MeteoDesktopFrame(
  private val controller: DesktopController,
) : JFrame("${AuthorBrand.APP_TITLE} — Авиационная метеостанция · ${AuthorBrand.AUTHOR_NAME}") {

  private var suppressingEvents = false

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
  private val tableButton = JButton("Таблица")
  private val alertsButton = JButton("Оповещ.")
  private val logButton = JButton("Лог")
  private val presetsButton = JButton("Профили")
  private val serverButton = JButton("Сервер")

  // Weather Alert Banner
  private val alertBannerPanel = JPanel(BorderLayout(8, 4))
  private val alertBannerLabel = JLabel()
  private val alertBannerOpenBtn = JButton("Открыть")
  private val alertBannerOkBtn = JButton("OK")

  // Status Banner
  private val bannerPanel = JPanel(BorderLayout(8, 4))
  private val bannerLabel = JLabel()
  private val bannerOkBtn = JButton("OK")

  // Main 3-column board
  private val boardPanel = JPanel(GridBagLayout())
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

  // Bottom METAR/TAF footer
  private val footerPanel = JPanel(GridLayout(2, 1, 4, 4))
  private val tafLabel = JLabel("TAF: --")
  private val metarLabel = JLabel("METAR: --")
  private val ownerFooterPanel = JPanel(BorderLayout())
  private val ownerLeftLabel = JLabel("${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} · ${AuthorBrand.SUBTITLE}")
  private val ownerRightLabel = JLabel("${AuthorBrand.COPYRIGHT_LINE} · ${AuthorBrand.AUTHOR_EMAIL}")

  init {
    defaultCloseOperation = EXIT_ON_CLOSE
    minimumSize = Dimension(920, 580)
    setSize(1140, 680)
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
      add(tableButton)
      add(alertsButton)
      add(logButton)
      add(presetsButton)
      add(serverButton)
    }

    toolbarPanel.border = BorderFactory.createEmptyBorder(4, 6, 4, 6)
    toolbarPanel.add(leftTool, BorderLayout.WEST)
    toolbarPanel.add(rightTool, BorderLayout.CENTER)

    // Weather Alert Banner
    alertBannerPanel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
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
    bannerPanel.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
    bannerLabel.font = Font("Segoe UI", Font.BOLD, 12)
    bannerPanel.add(bannerLabel, BorderLayout.CENTER)
    bannerPanel.add(bannerOkBtn, BorderLayout.EAST)
    bannerPanel.isVisible = false

    val northStack = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      isOpaque = false
      add(toolbarPanel)
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
    val clockHeader = JPanel(GridLayout(2, 1)).apply {
      isOpaque = false
      localClockLabel.font = Font("Consolas", Font.BOLD, 30)
      stationSubtitleLabel.font = Font("Segoe UI", Font.PLAIN, 12)
      add(localClockLabel)
      add(stationSubtitleLabel)
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
    centerPanel.border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
    centerPanel.add(clockHeader, BorderLayout.NORTH)
    centerPanel.add(telemetryGrid, BorderLayout.CENTER)

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

    rootPanel.add(boardPanel, BorderLayout.CENTER)

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

  private fun applyState(state: DesktopUiState) {
    suppressingEvents = true
    try {
      val pal = state.palette
      rootPanel.background = pal.bezelSurface
      toolbarPanel.background = pal.panelSurface
      toolbarPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(4, 6, 4, 6),
      )
      boardPanel.background = pal.bezelSurface
      centerPanel.background = pal.panelSurface
      centerPanel.border = BorderFactory.createLineBorder(pal.border, 1)
      footerPanel.background = pal.panelSurface
      footerPanel.border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(pal.border, 1),
        BorderFactory.createEmptyBorder(6, 8, 6, 8),
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
      favoriteButton.foreground = if (state.isCurrentStationFavorite) pal.metarAmber else pal.textMuted
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
      alertsButton.foreground = if (state.unreadAlertCount > 0) Color(0xC6, 0x28, 0x28) else Color.BLACK
      presetsButton.text = if (state.savedPresets.isEmpty()) "Профили" else "Профили (${state.savedPresets.size})"

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
        w.clouds.joinToString(" ") { "${it.cover}${it.baseMeters}м" }
      }
      ceilingField.text = w.ceilingMeters?.let { "$it м (${w.ceilingCode ?: ""})" } ?: "--"
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

      listOf(
        visField, cloudsField, ceilingField, weatherField,
        tempField, dewField, qnhField, qfeField, sunField, sourceField,
        leftHeadwindField, leftCrosswindField, rightHeadwindField, rightCrosswindField,
      ).forEach { tf ->
        tf.background = pal.fieldBackground
        tf.foreground = pal.textPrimary
        tf.border = BorderFactory.createLineBorder(pal.border, 1)
      }
      applyLabelTheme(centerPanel, pal)
      applyLabelTheme(boardPanel, pal)

      tafLabel.text = w.rawTaf
      tafLabel.foreground = pal.textSecondary
      metarLabel.text = w.rawMetar
      metarLabel.foreground = pal.textPrimary
      ownerLeftLabel.foreground = pal.textMuted
      ownerRightLabel.foreground = pal.textMuted
    } finally {
      suppressingEvents = false
    }
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
      if (comp is JLabel && comp !== localClockLabel && comp !== metarUtcLabel && comp !== bannerLabel) {
        if (comp !== leftRunwayHeader && comp !== rightRunwayHeader) {
          comp.foreground = pal.textSecondary
        }
      } else if (comp is java.awt.Container) {
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
