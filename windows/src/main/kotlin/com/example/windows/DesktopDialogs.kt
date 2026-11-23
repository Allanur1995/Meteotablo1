package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.domain.UnitConverter
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.WindSpeedUnit
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.table.DefaultTableModel

object DesktopDialogs {

  fun showStationAndServerDialog(
    owner: Frame,
    controller: DesktopController,
    initialTab: Int = 0,
  ) {
    val state = controller.state
    val palette = state.palette
    val dialog = JDialog(owner, "Станция, METAR и сервер", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(680, 520)
    dialog.setLocationRelativeTo(owner)

    val tabs = JTabbedPane()

    // Tab 1: Stations + Favorites
    val stationPanel = JPanel(BorderLayout(8, 8)).apply {
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
      background = palette.panelSurface
    }
    val customIcaoRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
      background = palette.panelSurface
      val icaoField = JTextField(state.station.icao, 8)
      val applyIcaoBtn = JButton("Открыть ICAO").apply {
        addActionListener {
          val code = icaoField.text.trim().uppercase()
          if (code.length == 4) {
            controller.selectStation(code)
            dialog.dispose()
          }
        }
      }
      val addFavBtn = JButton("★ В избранное").apply {
        addActionListener {
          val code = icaoField.text.trim().uppercase()
          if (code.length == 4) {
            controller.addFavorite(code, selectAfterAdd = true)
            dialog.dispose()
          }
        }
      }
      add(JLabel("Код ICAO:"))
      add(icaoField)
      add(applyIcaoBtn)
      add(addFavBtn)
    }

    val presetsPanel = JPanel(GridLayout(0, 2, 6, 6)).apply {
      background = palette.panelSurface
      AirportCatalog.presets.forEach { preset ->
        val isCurrent = preset.icao == state.station.icao
        val isFav = preset.icao in state.favoriteIcaos
        val btn = JButton(
          "${if (isFav) "★ " else ""}${preset.icao} — ${preset.city} (${preset.name})",
        ).apply {
          horizontalAlignment = SwingConstants.LEFT
          font = Font("Segoe UI", if (isCurrent) Font.BOLD else Font.PLAIN, 12)
          addActionListener {
            controller.selectStation(preset.icao)
            dialog.dispose()
          }
        }
        add(btn)
      }
    }
    val presetsListPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply {
      background = palette.panelSurface
      state.savedPresets.forEach { preset ->
        val applyBtn = JButton("★ ${preset.name}").apply {
          font = Font("Segoe UI", Font.PLAIN, 11)
          margin = Insets(2, 6, 2, 6)
          addActionListener {
            controller.applySavedPreset(preset.id)
            dialog.dispose()
          }
        }
        val delBtn = JButton("×").apply {
          font = Font("Segoe UI", Font.BOLD, 11)
          margin = Insets(2, 4, 2, 4)
          toolTipText = "Удалить профиль"
          addActionListener {
            controller.deleteSavedPreset(preset.id)
            dialog.dispose()
            showStationAndServerDialog(owner, controller, initialTab = 0)
          }
        }
        add(applyBtn)
        add(delBtn)
      }
    }

    val activePair = state.activeRunwayPair
    val presetNameField = JTextField(14)
    val savePresetBtn = JButton("Сохранить ВПП (${state.station.icao} ${activePair.primaryDesignator}/${activePair.reciprocalDesignator})").apply {
      addActionListener {
        controller.saveCurrentSetup(presetNameField.text.trim().takeIf { it.isNotBlank() })
        dialog.dispose()
        showStationAndServerDialog(owner, controller, initialTab = 0)
      }
    }

    val savePresetRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
      background = palette.panelSurface
      add(JLabel("Название профиля:"))
      add(presetNameField)
      add(savePresetBtn)
    }

    val northSection = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      background = palette.panelSurface
      add(customIcaoRow)
      add(savePresetRow)
      if (state.savedPresets.isNotEmpty()) {
        add(presetsListPanel)
      }
    }

    stationPanel.add(northSection, BorderLayout.NORTH)
    stationPanel.add(JScrollPane(presetsPanel), BorderLayout.CENTER)
    tabs.addTab("Станции", stationPanel)

    // Tab 2: Manual METAR / TAF
    val metarPanel = JPanel(BorderLayout(8, 8)).apply {
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    val metarArea = JTextArea(state.weather.rawMetar, 4, 48).apply {
      lineWrap = true
      wrapStyleWord = true
      font = Font("Consolas", Font.PLAIN, 13)
    }
    val tafArea = JTextArea(state.weather.rawTaf, 5, 48).apply {
      lineWrap = true
      wrapStyleWord = true
      font = Font("Consolas", Font.PLAIN, 13)
    }
    val metarFields = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(JLabel("Сырой METAR:"))
      add(Box.createVerticalStrut(4))
      add(JScrollPane(metarArea))
      add(Box.createVerticalStrut(10))
      add(JLabel("Сырой TAF:"))
      add(Box.createVerticalStrut(4))
      add(JScrollPane(tafArea))
    }
    val applyMetarBtn = JButton("Применить METAR / TAF").apply {
      addActionListener {
        controller.applyCustomMetar(metarArea.text, tafArea.text)
        dialog.dispose()
      }
    }
    metarPanel.add(metarFields, BorderLayout.CENTER)
    metarPanel.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(applyMetarBtn) },
      BorderLayout.SOUTH,
    )
    tabs.addTab("METAR / TAF", metarPanel)

    // Tab 3: Custom Server & Internet Sites Config
    val serverPanel = JPanel(GridBagLayout()).apply {
      border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
      background = palette.panelSurface
    }
    val cfg = state.serverConfig
    val modeCombo = JComboBox(DataSourceMode.entries.map { it.displayName }.toTypedArray()).apply {
      selectedIndex = cfg.resolvedMode().ordinal
    }
    val urlField = JTextField(cfg.url, 32)
    val userField = JTextField(cfg.username, 20)
    val passField = JPasswordField(cfg.password, 20)
    val awcCheck = JCheckBox("aviationweather.gov (JSON API)", cfg.useAviationWeatherGov).apply {
      isOpaque = false
    }
    val tgftpCheck = JCheckBox("NOAA (tgftp.nws.noaa.gov)", cfg.useNoaaTgftp).apply {
      isOpaque = false
    }
    val vatsimCheck = JCheckBox("VATSIM (metar.vatsim.net)", cfg.useVatsimMetar).apply {
      isOpaque = false
    }
    val diagnosticArea = JTextArea(4, 42).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font("Consolas", Font.PLAIN, 11)
      background = palette.fieldBackground
      foreground = palette.textSecondary
      border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
      text = "Нажмите «Проверить IP-сервер (${state.station.icao})», чтобы проверить соединение, авторизацию и парсинг METAR."
    }
    val diagnosticScroll = JScrollPane(diagnosticArea).apply {
      preferredSize = Dimension(520, 82)
      border = BorderFactory.createLineBorder(palette.border, 1)
    }

    val gbc = GridBagConstraints().apply {
      insets = Insets(4, 6, 4, 6)
      anchor = GridBagConstraints.WEST
      fill = GridBagConstraints.HORIZONTAL
    }
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
    serverPanel.add(JLabel("Режим источника:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(modeCombo, gbc)

    gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
    serverPanel.add(JLabel("URL / IP сервера:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(urlField, gbc)

    gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
    serverPanel.add(JLabel("Логин:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(userField, gbc)

    gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0
    serverPanel.add(JLabel("Пароль:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(passField, gbc)

    gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0
    serverPanel.add(JLabel("Интернет-сайты:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(awcCheck, gbc)
    gbc.gridx = 1; gbc.gridy = 5
    serverPanel.add(tgftpCheck, gbc)
    gbc.gridx = 1; gbc.gridy = 6
    serverPanel.add(vatsimCheck, gbc)

    gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; gbc.weightx = 1.0
    serverPanel.add(diagnosticScroll, gbc)

    val serverButtons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
      isOpaque = false
      val testIpBtn = JButton("Проверить IP-сервер (${state.station.icao})")
      testIpBtn.addActionListener {
        testIpBtn.isEnabled = false
        testIpBtn.text = "Проверка (${state.station.icao})…"
        diagnosticArea.foreground = palette.textSecondary
        diagnosticArea.text = "Подключение к ${urlField.text}…"
        val probeCfg = ServerConfig(
          url = urlField.text,
          username = userField.text.trim(),
          password = String(passField.password),
          sourceMode = DataSourceMode.IP_ONLY,
          useCustomServer = true,
          fallbackToNoaa = false,
        )
        controller.testIpServerConnection(state.station.icao, probeCfg) { report ->
          diagnosticArea.foreground = if (report.metarFound) palette.headwindPositive else palette.tailwindNegative
          diagnosticArea.text = buildString {
            appendLine(report.summary)
            report.logs.takeLast(5).forEach { appendLine(it) }
          }.trim()
          testIpBtn.isEnabled = true
          testIpBtn.text = "Проверить IP-сервер (${state.station.icao})"
        }
      }
      val resetBtn = JButton("По умолчанию (217.174.229.206)").apply {
        addActionListener {
          modeCombo.selectedIndex = DataSourceMode.AUTO_IP_FIRST.ordinal
          urlField.text = ServerConfig.DEFAULT_SERVER_URL
          userField.text = ServerConfig.DEFAULT_USERNAME
          passField.text = ServerConfig.DEFAULT_PASSWORD
          awcCheck.isSelected = true
          tgftpCheck.isSelected = true
          vatsimCheck.isSelected = true
        }
      }
      val saveBtn = JButton("Сохранить и обновить").apply {
        addActionListener {
          val selectedMode = DataSourceMode.entries.getOrElse(modeCombo.selectedIndex) {
            DataSourceMode.AUTO_IP_FIRST
          }
          controller.updateServerConfig(
            ServerConfig(
              url = urlField.text,
              username = userField.text,
              password = String(passField.password),
              useCustomServer = selectedMode != DataSourceMode.INTERNET_ONLY,
              fallbackToNoaa = selectedMode != DataSourceMode.IP_ONLY,
              sourceMode = selectedMode,
              useAviationWeatherGov = awcCheck.isSelected,
              useNoaaTgftp = tgftpCheck.isSelected,
              useVatsimMetar = vatsimCheck.isSelected,
            ),
            fetchNow = true,
          )
          dialog.dispose()
        }
      }
      add(testIpBtn)
      add(resetBtn)
      add(saveBtn)
    }
    gbc.gridx = 0; gbc.gridy = 8; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.EAST
    serverPanel.add(serverButtons, gbc)

    tabs.addTab("Сервер", serverPanel)

    // Tab 4: About / Author Attribution
    val aboutPanel = JPanel(BorderLayout(8, 8)).apply {
      border = BorderFactory.createEmptyBorder(14, 14, 14, 14)
    }
    val aboutInfo = JTextArea(
      buildString {
        appendLine("${AuthorBrand.APP_TITLE} v${AuthorBrand.APP_VERSION} (${AuthorBrand.SUBTITLE})")
        appendLine()
        appendLine("Автор / Разработчик: ${AuthorBrand.AUTHOR_NAME} (${AuthorBrand.AUTHOR_HANDLE})")
        appendLine("Email: ${AuthorBrand.AUTHOR_EMAIL}")
        appendLine("GitHub: ${AuthorBrand.AUTHOR_URL}")
        appendLine("Копирайт: ${AuthorBrand.COPYRIGHT_LINE}")
        appendLine("Идентификатор: ${AuthorBrand.ANDROID_PACKAGE_ID}")
        appendLine()
        append(AuthorBrand.OWNERSHIP_NOTICE)
      },
    ).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font("Segoe UI", Font.PLAIN, 13)
      border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
    }
    aboutPanel.add(JScrollPane(aboutInfo), BorderLayout.CENTER)
    tabs.addTab("Автор", aboutPanel)

    tabs.selectedIndex = initialTab.coerceIn(0, 3)

    dialog.contentPane.layout = BorderLayout()
    dialog.contentPane.add(tabs, BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )
    dialog.isVisible = true
  }

  fun showMetarTableDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val dialog = JDialog(owner, "Таблица METAR и Роза ветров", false)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(940, 620)
    dialog.setLocationRelativeTo(owner)

    val root = JPanel(BorderLayout(8, 8)).apply {
      border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    val favoritesBar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
    val addIcaoField = JTextField(6)
    val addFavBtn = JButton("+ Добавить").apply {
      addActionListener {
        val code = addIcaoField.text.trim().uppercase()
        if (code.length == 4 && code.all { it.isLetter() }) {
          controller.addFavorite(code, selectAfterAdd = true)
          addIcaoField.text = ""
        }
      }
    }

    val titleLabel = JLabel().apply { font = Font("Segoe UI", Font.BOLD, 15) }
    val windToggleBtn = JButton().apply { addActionListener { controller.toggleWindUnit() } }
    val pressureToggleBtn = JButton().apply { addActionListener { controller.togglePressureUnit() } }
    val copyBtn = JButton("Копировать METAR").apply {
      addActionListener {
        val sel = StringSelection(controller.state.weather.rawMetar)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, null)
      }
    }
    val editBtn = JButton("Изменить METAR").apply {
      addActionListener {
        showStationAndServerDialog(owner, controller, initialTab = 1)
      }
    }

    val headerBar = JPanel(BorderLayout(6, 4))
    headerBar.add(titleLabel, BorderLayout.WEST)
    headerBar.add(
      JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
        add(windToggleBtn)
        add(pressureToggleBtn)
        add(copyBtn)
        add(editBtn)
      },
      BorderLayout.EAST,
    )

    val topStack = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(headerBar)
      add(JScrollPane(favoritesBar).apply {
        preferredSize = Dimension(900, 44)
        border = BorderFactory.createTitledBorder("Избранные аэропорты")
      })
    }

    val windRosePanel = WindRoseDetailPanel(onToggleUnit = { controller.toggleWindUnit() })
    val tableModel = object : DefaultTableModel(arrayOf("Параметр", "Значение", "Детали", "Код"), 0) {
      override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    val table = JTable(tableModel).apply {
      rowHeight = 26
      font = Font("Segoe UI", Font.PLAIN, 12)
      tableHeader.font = Font("Segoe UI", Font.BOLD, 12)
      columnModel.getColumn(0).preferredWidth = 140
      columnModel.getColumn(1).preferredWidth = 150
      columnModel.getColumn(2).preferredWidth = 210
      columnModel.getColumn(3).preferredWidth = 90
    }

    val rawArea = JTextArea(4, 60).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font("Consolas", Font.PLAIN, 12)
    }

    val centerSplit = JPanel(BorderLayout(10, 8)).apply {
      add(windRosePanel, BorderLayout.WEST)
      add(JScrollPane(table), BorderLayout.CENTER)
      add(JScrollPane(rawArea), BorderLayout.SOUTH)
    }

    root.add(topStack, BorderLayout.NORTH)
    root.add(centerSplit, BorderLayout.CENTER)
    root.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )

    val listener: (DesktopUiState) -> Unit = { st ->
      val pal = st.palette
      root.background = pal.bezelSurface
      titleLabel.text = "Таблица METAR · ${st.station.icao} (${st.station.name})"
      windToggleBtn.text = "Ветер: ${st.windUnit.label}"
      pressureToggleBtn.text = "Давление: ${st.pressureUnit.label}"

      favoritesBar.removeAll()
      st.favoriteIcaos.forEach { icao ->
        val isSel = icao == st.station.icao
        val chipBtn = JButton(if (isSel) "● $icao" else icao).apply {
          font = Font("Segoe UI", if (isSel) Font.BOLD else Font.PLAIN, 11)
          margin = Insets(2, 6, 2, 6)
          addActionListener { controller.selectStation(icao) }
        }
        val rmBtn = JButton("×").apply {
          font = Font("Segoe UI", Font.BOLD, 11)
          margin = Insets(2, 4, 2, 4)
          toolTipText = "Удалить $icao из избранного"
          addActionListener { controller.removeFavorite(icao) }
        }
        favoritesBar.add(chipBtn)
        favoritesBar.add(rmBtn)
      }
      favoritesBar.add(JLabel("  ICAO:"))
      favoritesBar.add(addIcaoField)
      favoritesBar.add(addFavBtn)
      favoritesBar.revalidate()
      favoritesBar.repaint()

      windRosePanel.updateData(st.weather.wind, st.windUnit, pal)
      tableModel.rowCount = 0
      buildTableRows(st.station, st.weather, st.windUnit, st.pressureUnit).forEach { row ->
        tableModel.addRow(row)
      }
      rawArea.text = "${st.weather.rawMetar}\n${st.weather.rawTaf}"
    }

    controller.addListener(listener)
    dialog.addWindowListener(object : java.awt.event.WindowAdapter() {
      override fun windowClosed(e: java.awt.event.WindowEvent?) {
        controller.removeListener(listener)
      }
    })

    dialog.contentPane = root
    dialog.isVisible = true
  }

  fun showLogDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val dialog = JDialog(owner, "Журнал событий (Лог)", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(680, 420)
    dialog.setLocationRelativeTo(owner)

    val logsText = controller.state.logs.joinToString("\n") { entry ->
      "[${entry.timestamp}] [${entry.level}] ${entry.message}"
    }
    val area = JTextArea(logsText).apply {
      isEditable = false
      font = Font("Consolas", Font.PLAIN, 12)
      lineWrap = true
      wrapStyleWord = true
    }

    dialog.contentPane.layout = BorderLayout(8, 8)
    dialog.contentPane.add(JScrollPane(area), BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )
    dialog.isVisible = true
  }

  fun showAlertsDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val dialog = JDialog(owner, "Метеооповещения избранных аэродромов", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(680, 480)
    dialog.setLocationRelativeTo(owner)

    val state = controller.state
    val cfg = state.alertConfig
    val enabledCheck = JCheckBox("Включить оповещения об изменениях погоды (${state.favoriteIcaos.joinToString(", ")})", cfg.enabled)
    val windCombo = JComboBox(arrayOf("±5 kt", "±8 kt", "±12 kt")).apply {
      selectedIndex = when (cfg.windChangeThresholdKt) {
        5 -> 0
        12 -> 2
        else -> 1
      }
    }
    val visCombo = JComboBox(arrayOf("< 1500 м / ВНГО 500 ft", "< 3000 м / ВНГО 1000 ft", "< 5000 м / ВНГО 1500 ft")).apply {
      selectedIndex = when (cfg.visibilityThresholdMeters) {
        1500 -> 0
        5000 -> 2
        else -> 1
      }
    }

    fun applyAlertPrefs() {
      val windKt = when (windCombo.selectedIndex) {
        0 -> 5
        2 -> 12
        else -> 8
      }
      val (visM, ceilM) = when (visCombo.selectedIndex) {
        0 -> 1500 to 150
        2 -> 5000 to 450
        else -> 3000 to 300
      }
      controller.updateAlertConfig(
        cfg.copy(
          enabled = enabledCheck.isSelected,
          windChangeThresholdKt = windKt,
          visibilityThresholdMeters = visM,
          ceilingThresholdMeters = ceilM,
        ),
      )
    }

    enabledCheck.addActionListener { applyAlertPrefs() }
    windCombo.addActionListener { applyAlertPrefs() }
    visCombo.addActionListener { applyAlertPrefs() }

    val topPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
      add(enabledCheck)
      val threshRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
        add(JLabel("Сдвиг ветра:"))
        add(windCombo)
        add(JLabel("Порог видимости:"))
        add(visCombo)
      }
      add(threshRow)
    }

    val listPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    }
    if (state.weatherAlerts.isEmpty()) {
      listPanel.add(JLabel("Пока нет зафиксированных изменений погоды для избранных аэродромов."))
    } else {
      state.weatherAlerts.forEach { alert ->
        val card = JPanel(BorderLayout(6, 4)).apply {
          border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
              if (alert.severity == com.example.model.WeatherAlertSeverity.CRITICAL) Color(0xC6, 0x28, 0x28)
              else Color(0xC9, 0x7A, 0x00),
              1,
            ),
            BorderFactory.createEmptyBorder(6, 8, 6, 8),
          )
          maximumSize = Dimension(Int.MAX_VALUE, 90)
        }
        val titleLbl = JLabel("${alert.icao} (${alert.airportName}) · ${alert.severity.label} · ${alert.observationTimeUtc}").apply {
          font = Font("Segoe UI", Font.BOLD, 12)
        }
        val bodyLbl = JLabel("<html><b>${alert.summary}</b><br/><code>${alert.rawMetar}</code></html>").apply {
          font = Font("Segoe UI", Font.PLAIN, 11)
        }
        val openBtn = JButton("Открыть").apply {
          addActionListener {
            controller.openAlertStation(alert.icao)
            dialog.dispose()
          }
        }
        card.add(titleLbl, BorderLayout.NORTH)
        card.add(bodyLbl, BorderLayout.CENTER)
        card.add(openBtn, BorderLayout.EAST)
        listPanel.add(card)
        listPanel.add(Box.createVerticalStrut(6))
      }
    }

    val footerRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 6)).apply {
      add(
        JButton("Проверить избранные сейчас").apply {
          addActionListener {
            controller.checkFavoritesWeatherNow()
            dialog.dispose()
          }
        },
      )
      if (state.weatherAlerts.isNotEmpty()) {
        add(
          JButton("Очистить").apply {
            addActionListener {
              controller.clearWeatherAlerts()
              dialog.dispose()
            }
          },
        )
        add(
          JButton("Прочитано").apply {
            addActionListener {
              controller.markAllAlertsRead()
              dialog.dispose()
            }
          },
        )
      }
      add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
    }

    dialog.contentPane.layout = BorderLayout(8, 8)
    dialog.contentPane.add(topPanel, BorderLayout.NORTH)
    dialog.contentPane.add(JScrollPane(listPanel), BorderLayout.CENTER)
    dialog.contentPane.add(footerRow, BorderLayout.SOUTH)
    dialog.isVisible = true
  }

  private fun buildTableRows(
    station: AirportStation,
    weather: DecodedWeather,
    windUnit: WindSpeedUnit,
    pressureUnit: PressureUnit,
  ): List<Array<String>> {
    val w = weather.wind
    val dirStr = when {
      w.isCalm -> "Штиль"
      w.isVariable -> "Переменный (VRB)"
      w.directionDeg != null -> String.format(Locale.US, "%03d°", w.directionDeg)
      else -> "--"
    }
    val spd = UnitConverter.formatWindSpeedValue(w.speedKt, windUnit)
    val gust = w.gustKt?.let { " порывы ${UnitConverter.formatWindSpeedValue(it, windUnit)} ${windUnit.label}" }.orEmpty()
    val visText = when {
      weather.isCavok -> "CAVOK (> 10 км)"
      weather.visibilityMeters != null && weather.visibilityMeters >= 9999 -> "≥ 10 км (9999 м)"
      weather.visibilityMeters != null -> "${weather.visibilityMeters} м"
      else -> "--"
    }
    val cloudText = if (weather.clouds.isEmpty()) {
      if (weather.isCavok) "Нет значимой" else "--"
    } else {
      weather.clouds.joinToString(", ") { "${it.cover} ${it.baseFeet} ft" }
    }
    val qnhPrimary = when (pressureUnit) {
      PressureUnit.HPA -> weather.qnhHpa?.let { "$it гПа" } ?: "--"
      PressureUnit.INHG -> weather.qnhInHg?.let { String.format(Locale.forLanguageTag("ru-RU"), "%.2f дм", it) } ?: "--"
    }
    val qfeText = weather.qfeMmHg?.let { "$it мм рт.ст. (${weather.qfeHpa ?: "--"} гПа)" } ?: "--"
    val tempText = weather.temperatureC?.let { "$it°C" } ?: "--"
    val dewText = weather.dewPointC?.let { "$it°C" } ?: "--"

    return listOf(
      arrayOf("Время (UTC / Мест.)", "${weather.observationTimeUtc} / ${weather.localObsTime}", station.timezoneLabel, station.icao),
      arrayOf("Ветер", "$dirStr, $spd ${windUnit.label}$gust", if (w.variableFromDeg != null) "${w.variableFromDeg}°..${w.variableToDeg}°" else "", ""),
      arrayOf("Видимость", visText, weather.weatherPhenomena, ""),
      arrayOf("Облачность / ВНГО", cloudText, weather.ceilingFeet?.let { "ВНГО: $it ft" } ?: "ВНГО: --", weather.ceilingCode ?: ""),
      arrayOf("Температура / Роса", "$tempText / $dewText", "Превышение: ${station.elevationMeters} м", ""),
      arrayOf("QNH / QFE", qnhPrimary, "QFE: $qfeText", weather.qnhHpa?.let { "Q$it" } ?: ""),
      arrayOf("Солнце (Восх. / Зак.)", "${weather.sunriseLocal} / ${weather.sunsetLocal}", weather.specialConditions, weather.sourceLabel),
    )
  }
}
