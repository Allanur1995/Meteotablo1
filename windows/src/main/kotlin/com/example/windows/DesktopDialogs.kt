package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.domain.UnitConverter
import com.example.model.AirportStation
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
    stationPanel.add(customIcaoRow, BorderLayout.NORTH)
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
    }
    val cfg = state.serverConfig
    val modeCombo = JComboBox(DataSourceMode.entries.map { it.displayName }.toTypedArray()).apply {
      selectedIndex = cfg.resolvedMode().ordinal
    }
    val urlField = JTextField(cfg.url, 32)
    val userField = JTextField(cfg.username, 20)
    val passField = JPasswordField(cfg.password, 20)
    val awcCheck = JCheckBox("aviationweather.gov (JSON API)", cfg.useAviationWeatherGov)
    val tgftpCheck = JCheckBox("NOAA (tgftp.nws.noaa.gov)", cfg.useNoaaTgftp)
    val vatsimCheck = JCheckBox("VATSIM (metar.vatsim.net)", cfg.useVatsimMetar)

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

    val serverButtons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
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
      add(resetBtn)
      add(saveBtn)
    }
    gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.EAST
    serverPanel.add(serverButtons, gbc)

    tabs.addTab("Сервер", serverPanel)
    tabs.selectedIndex = initialTab.coerceIn(0, 2)

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
      weather.clouds.joinToString(", ") { "${it.cover} ${it.baseFeet} ft (${it.baseMeters} м)" }
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
      arrayOf("Облачность / ВНГО", cloudText, weather.ceilingMeters?.let { "ВНГО: $it м" } ?: "ВНГО: --", weather.ceilingCode ?: ""),
      arrayOf("Температура / Роса", "$tempText / $dewText", "Превышение: ${station.elevationMeters} м", ""),
      arrayOf("QNH / QFE", qnhPrimary, "QFE: $qfeText", weather.qnhHpa?.let { "Q$it" } ?: ""),
      arrayOf("Солнце (Восх. / Зак.)", "${weather.sunriseLocal} / ${weather.sunsetLocal}", weather.specialConditions, weather.sourceLabel),
    )
  }
}
