package com.example.windows

import com.example.data.AirportCatalog
import com.example.data.AutoRefreshInterval
import com.example.data.DataSourceMode
import com.example.data.ServerConfig
import com.example.domain.AviationWeatherParser
import com.example.domain.DecodedTokenSeverity
import com.example.domain.MetarTafDecoder
import com.example.domain.UnitConverter
import com.example.domain.WeatherExportPayload
import com.example.domain.WeatherReportFormatter
import com.example.model.AirportStation
import com.example.model.AuthorBrand
import com.example.model.DecodedWeather
import com.example.model.PressureUnit
import com.example.model.UnitSystem
import com.example.model.WindData
import com.example.model.WindSpeedUnit
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
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
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object DesktopDialogs {

  fun showStationAndServerDialog(
    owner: Frame,
    controller: DesktopController,
    initialTab: Int = 0,
  ) {
    val state = controller.state
    val palette = state.palette
    val dialog = JDialog(owner, "Станция, METAR, Сервер и Автообновление", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(720, 560)
    dialog.setLocationRelativeTo(owner)

    val tabs = JTabbedPane()

    // Tab 1: Stations + Favorites + Coordinates
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

    val coordRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
      background = palette.panelSurface
      val latField = JTextField(String.format(Locale.US, "%.4f", state.station.latitude), 9)
      val lonField = JTextField(String.format(Locale.US, "%.4f", state.station.longitude), 9)
      val fetchCoordBtn = JButton("Загрузить по координатам").apply {
        addActionListener {
          val lat = latField.text.trim().replace(',', '.').toDoubleOrNull()
          val lon = lonField.text.trim().replace(',', '.').toDoubleOrNull()
          if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            JOptionPane.showMessageDialog(
              dialog,
              "Введите корректные координаты: широта -90..90, долгота -180..180",
              "Ошибка координат",
              JOptionPane.WARNING_MESSAGE,
            )
          } else {
            controller.fetchWeatherByCoordinates(lat, lon)
            dialog.dispose()
          }
        }
      }
      add(JLabel("Широта:"))
      add(latField)
      add(JLabel("Долгота:"))
      add(lonField)
      add(fetchCoordBtn)
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
      add(coordRow)
      add(savePresetRow)
      if (state.savedPresets.isNotEmpty()) {
        add(presetsListPanel)
      }
    }

    stationPanel.add(northSection, BorderLayout.NORTH)
    stationPanel.add(JScrollPane(presetsPanel), BorderLayout.CENTER)
    tabs.addTab("Станции / Коорд.", stationPanel)

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

    // Tab 3: Custom Server, Auto-Refresh & Internet Sites Config
    val serverPanel = JPanel(GridBagLayout()).apply {
      border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
      background = palette.panelSurface
    }
    val cfg = state.serverConfig
    var resetToDefaultInternal = false
    val modeCombo = JComboBox(DataSourceMode.entries.map { it.displayName }.toTypedArray()).apply {
      selectedIndex = cfg.resolvedMode().ordinal
    }
    val autoAuthStatusLabel = JLabel("Ведомственный метеосервер · Авто-вход: активен (реквизиты скрыты)").apply {
      font = Font("Segoe UI", Font.BOLD, 12)
      putClientProperty("preserveForeground", true)
      foreground = palette.headwindPositive
    }
    val awcCheck = JCheckBox("aviationweather.gov (JSON API)", cfg.useAviationWeatherGov).apply {
      isOpaque = false
    }
    val tgftpCheck = JCheckBox("NOAA (tgftp.nws.noaa.gov)", cfg.useNoaaTgftp).apply {
      isOpaque = false
    }
    val vatsimCheck = JCheckBox("VATSIM (metar.vatsim.net)", cfg.useVatsimMetar).apply {
      isOpaque = false
    }
    val autoRefreshCheck = JCheckBox("Автообновление табло включено", state.autoRefreshEnabled).apply {
      isOpaque = false
    }
    val intervalCombo = JComboBox(AutoRefreshInterval.entries.map { it.label }.toTypedArray()).apply {
      selectedIndex = AutoRefreshInterval.entries.indexOf(
        AutoRefreshInterval.fromMinutes(state.autoRefreshIntervalMinutes),
      ).coerceAtLeast(0)
    }

    val diagnosticArea = JTextArea(4, 42).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font("Consolas", Font.PLAIN, 11)
      background = palette.fieldBackground
      putClientProperty("preserveForeground", true)
      foreground = palette.textSecondary
      border = BorderFactory.createEmptyBorder(6, 8, 6, 8)
      text = "Поддерживается автоматическая авторизация для всех 7 аэропортов Туркменистана: UTAM, UTAK, UTAA, UTAN, UTAT, UTAV, UTAE."
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
    serverPanel.add(JLabel("Авторизация:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(autoAuthStatusLabel, gbc)

    gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
    serverPanel.add(JLabel("Автообновление:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(
      JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
        isOpaque = false
        add(autoRefreshCheck)
        add(intervalCombo)
      },
      gbc,
    )

    gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0
    serverPanel.add(JLabel("Интернет-сайты:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    serverPanel.add(awcCheck, gbc)
    gbc.gridx = 1; gbc.gridy = 4
    serverPanel.add(tgftpCheck, gbc)
    gbc.gridx = 1; gbc.gridy = 5
    serverPanel.add(vatsimCheck, gbc)

    gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.weightx = 1.0
    serverPanel.add(diagnosticScroll, gbc)

    val serverButtons = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
      isOpaque = false
      val testServerBtn = JButton("Проверить сервер (${state.station.icao})")
      testServerBtn.addActionListener {
        testServerBtn.isEnabled = false
        testServerBtn.text = "Проверка (${state.station.icao})…"
        diagnosticArea.foreground = palette.textSecondary
        diagnosticArea.text = "Выполняется автоматическое подключение к ведомственному метеосерверу…"
        val activeUrl = if (resetToDefaultInternal) ServerConfig.DEFAULT_SERVER_URL else cfg.url
        val activeUser = if (resetToDefaultInternal) ServerConfig.DEFAULT_USERNAME else cfg.username
        val activePass = if (resetToDefaultInternal) ServerConfig.DEFAULT_PASSWORD else cfg.password
        val probeCfg = ServerConfig(
          url = activeUrl,
          username = activeUser,
          password = activePass,
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
          testServerBtn.isEnabled = true
          testServerBtn.text = "Проверить сервер (${state.station.icao})"
        }
      }
      val resetBtn = JButton("Авто по умолч.").apply {
        addActionListener {
          resetToDefaultInternal = true
          modeCombo.selectedIndex = DataSourceMode.AUTO_IP_FIRST.ordinal
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
          val interval = AutoRefreshInterval.entries.getOrElse(intervalCombo.selectedIndex) {
            AutoRefreshInterval.FIFTEEN_MIN
          }
          controller.setAutoRefreshEnabled(autoRefreshCheck.isSelected)
          controller.setAutoRefreshInterval(interval.minutes)
          val activeUrl = if (resetToDefaultInternal) ServerConfig.DEFAULT_SERVER_URL else cfg.url
          val activeUser = if (resetToDefaultInternal) ServerConfig.DEFAULT_USERNAME else cfg.username
          val activePass = if (resetToDefaultInternal) ServerConfig.DEFAULT_PASSWORD else cfg.password
          controller.updateServerConfig(
            ServerConfig(
              url = activeUrl,
              username = activeUser,
              password = activePass,
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
      add(testServerBtn)
      add(resetBtn)
      add(saveBtn)
    }
    gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.EAST
    serverPanel.add(serverButtons, gbc)

    tabs.addTab("Сервер и Авто", serverPanel)

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
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, palette)
    dialog.isVisible = true
  }

  fun showMetarTableDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val dialog = JDialog(owner, "Таблица METAR, пороги тревоги и Роза ветров", false)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(980, 640)
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
    val decoderBtn = JButton("Декодер").apply {
      addActionListener { showDecoderDialog(owner, controller) }
    }
    val thresholdsBtn = JButton("Пороги").apply {
      addActionListener { showAlertsDialog(owner, controller) }
    }
    val copyBtn = JButton("Копировать").apply {
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
        add(decoderBtn)
        add(thresholdsBtn)
        add(copyBtn)
        add(editBtn)
      },
      BorderLayout.EAST,
    )

    val topStack = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(headerBar)
      add(JScrollPane(favoritesBar).apply {
        preferredSize = Dimension(920, 46)
        border = BorderFactory.createTitledBorder("Мои аэропорты")
      })
    }

    val windRosePanel = WindRoseDetailPanel(onToggleUnit = { controller.toggleWindUnit() })
    val tableModel = object : DefaultTableModel(arrayOf("Параметр", "Значение", "Детали", "Код"), 0) {
      override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    var windBreached = false
    var visBreached = false

    val table = JTable(tableModel).apply {
      rowHeight = 28
      font = Font("Segoe UI", Font.PLAIN, 12.5f.toInt())
      tableHeader.font = Font("Segoe UI", Font.BOLD, 12)
      columnModel.getColumn(0).preferredWidth = 150
      columnModel.getColumn(1).preferredWidth = 170
      columnModel.getColumn(2).preferredWidth = 220
      columnModel.getColumn(3).preferredWidth = 90
      setDefaultRenderer(
        Any::class.java,
        object : DefaultTableCellRenderer() {
          override fun getTableCellRendererComponent(
            t: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
          ): Component {
            val comp = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column)
            val pal = controller.state.palette
            val param = t.getValueAt(row, 0)?.toString().orEmpty()
            val isRowAlert = (param == "Ветер" && windBreached) || (param == "Видимость" && visBreached)
            if (!isSelected) {
              background = if (isRowAlert) Color(217, 119, 6, 48) else pal.fieldBackground
              foreground = if (isRowAlert) pal.tailwindNegative else pal.textPrimary
            }
            return comp
          }
        },
      )
    }

    val rawArea = JTextArea(4, 60).apply {
      isEditable = false
      lineWrap = true
      wrapStyleWord = true
      font = Font("Consolas", Font.BOLD, 13)
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

      val w = st.weather
      val windPeakKt = maxOf(w.wind.speedKt, w.wind.gustKt ?: w.wind.speedKt)
      val visMeters = when {
        w.isCavok -> 10000
        else -> w.visibilityMeters
      }
      windBreached = st.alertConfig.tableAlertsEnabled && windPeakKt >= st.alertConfig.tableWindAlertKt
      visBreached = st.alertConfig.tableAlertsEnabled &&
        visMeters != null && visMeters <= st.alertConfig.tableVisibilityAlertMeters

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

      windRosePanel.updateData(st.weather.wind, st.windUnit, pal, st.metarFetchSeq)
      tableModel.rowCount = 0
      buildTableRows(st.station, st.weather, st.windUnit, st.pressureUnit).forEach { row ->
        tableModel.addRow(row)
      }
      table.repaint()
      rawArea.background = pal.fieldBackground
      rawArea.foreground = pal.textPrimary
      rawArea.text = "${st.weather.rawMetar}\n${st.weather.rawTaf}"
      DesktopThemeHelper.applyThemeTree(root, pal)
      table.repaint()
    }

    controller.addListener(listener)
    dialog.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        controller.removeListener(listener)
      }
    })

    dialog.contentPane = root
    dialog.isVisible = true
  }

  fun showDecoderDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val st = controller.state
    val pal = st.palette
    val dialog = JDialog(owner, "Открытый декодер METAR / TAF · ${st.station.icao}", false)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(820, 600)
    dialog.setLocationRelativeTo(owner)

    val metarRes = MetarTafDecoder.decodeMetar(st.weather.rawMetar, st.station)
    val tafRes = MetarTafDecoder.decodeTaf(st.weather.rawTaf, st.station)

    val tabs = JTabbedPane()

    fun buildDecodedPanel(res: com.example.domain.DecodedMessageResult): JPanel {
      val p = JPanel(BorderLayout(8, 8)).apply {
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        background = pal.panelSurface
      }
      val summaryArea = JTextArea("${res.rawText}\n\nСводка: ${res.plainSummaryParagraph}").apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font("Consolas", Font.BOLD, 12.5f.toInt())
        background = pal.fieldBackground
        foreground = pal.textPrimary
        border = BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(pal.border, 1),
          BorderFactory.createEmptyBorder(8, 8, 8, 8),
        )
      }
      val model = object : DefaultTableModel(arrayOf("Код", "Категория", "Значение", "Подробная расшифровка"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
      }
      res.items.forEach { item ->
        model.addRow(arrayOf(item.rawCode, item.category, item.shortValue, item.fullExplanation))
      }
      val table = JTable(model).apply {
        rowHeight = 28
        font = Font("Segoe UI", Font.PLAIN, 12)
        tableHeader.font = Font("Segoe UI", Font.BOLD, 12)
        columnModel.getColumn(0).preferredWidth = 110
        columnModel.getColumn(1).preferredWidth = 115
        columnModel.getColumn(2).preferredWidth = 135
        columnModel.getColumn(3).preferredWidth = 390
        setDefaultRenderer(
          Any::class.java,
          object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
              t: JTable,
              value: Any?,
              isSelected: Boolean,
              hasFocus: Boolean,
              row: Int,
              column: Int,
            ): Component {
              val c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column)
              val sev = res.items.getOrNull(row)?.severity ?: DecodedTokenSeverity.NORMAL
              if (!isSelected) {
                background = pal.fieldBackground
                foreground = when (sev) {
                  DecodedTokenSeverity.HAZARD -> pal.tailwindNegative
                  DecodedTokenSeverity.CAUTION -> pal.metarAmber
                  DecodedTokenSeverity.NORMAL -> pal.textPrimary
                }
              }
              return c
            }
          },
        )
      }
      p.add(JScrollPane(summaryArea).apply { preferredSize = Dimension(780, 96) }, BorderLayout.NORTH)
      p.add(JScrollPane(table), BorderLayout.CENTER)
      return p
    }

    tabs.addTab("METAR (${metarRes.items.size} групп)", buildDecodedPanel(metarRes))
    tabs.addTab("TAF (${tafRes.items.size} групп)", buildDecodedPanel(tafRes))

    val copyFullBtn = JButton("Копировать расшифровку").apply {
      addActionListener {
        val text = MetarTafDecoder.formatPlainTextReport(st.weather.rawMetar, st.weather.rawTaf, st.station)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
      }
    }

    dialog.contentPane.layout = BorderLayout(8, 8)
    dialog.contentPane.add(tabs, BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
        add(copyFullBtn)
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, pal)
    dialog.isVisible = true
  }

  fun showPressureConverterDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val st = controller.state
    val pal = st.palette
    val dialog = JDialog(owner, "Конвертер давления QNH / QFE (hPa ↔ inHg ↔ mmHg)", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(580, 420)
    dialog.setLocationRelativeTo(owner)

    val initialQnh = st.weather.qnhHpa?.toDouble() ?: 1013.25
    val qnhHpaField = JTextField(String.format(Locale.US, "%.2f", initialQnh), 10)
    val qnhInHgField = JTextField(String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(initialQnh)), 10)
    val elevField = JTextField(st.station.elevationMeters.toString(), 8)
    val qfeResultLabel = JLabel("", SwingConstants.LEFT).apply {
      font = Font("Consolas", Font.BOLD, 14)
      putClientProperty("preserveForeground", true)
      foreground = pal.accentCyan
    }
    val isaDiffLabel = JLabel("", SwingConstants.LEFT).apply {
      font = Font("Consolas", Font.PLAIN, 12)
      putClientProperty("preserveForeground", true)
      foreground = pal.textSecondary
    }
    var updating = false
    var autoSyncMetar = true

    fun updateDerived(qnhHpa: Double) {
      val elevM = elevField.text.trim().toIntOrNull() ?: controller.state.station.elevationMeters
      val qfeHpa = UnitConverter.computeQfeFromQnhHpa(qnhHpa, elevM)
      val qfeInHg = UnitConverter.convertHpaToInHg(qfeHpa)
      val qfeMm = UnitConverter.convertHpaToMmHg(qfeHpa.roundToInt())
      val qnhMm = UnitConverter.convertHpaToMmHg(qnhHpa.roundToInt())
      qfeResultLabel.text = String.format(
        Locale.US,
        "QFE (%d м): %.1f hPa · %.2f inHg · %d мм рт.ст.",
        elevM,
        qfeHpa,
        qfeInHg,
        qfeMm,
      )
      val deltaIsa = qnhHpa - 1013.25
      val sign = if (deltaIsa >= 0) "+" else ""
      isaDiffLabel.text = String.format(
        Locale.US,
        "QNH: %d мм рт.ст. · Отклонение от ISA (1013.25): %s%.2f hPa",
        qnhMm,
        sign,
        deltaIsa,
      )
    }

    fun syncFromHpaInput(markManual: Boolean = false) {
      if (updating) return
      val hpa = qnhHpaField.text.trim().replace(',', '.').toDoubleOrNull() ?: return
      if (markManual) autoSyncMetar = false
      updating = true
      qnhInHgField.text = String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(hpa))
      updateDerived(hpa)
      updating = false
    }

    fun syncFromInHgInput(markManual: Boolean = false) {
      if (updating) return
      val inhg = qnhInHgField.text.trim().replace(',', '.').toDoubleOrNull() ?: return
      if (markManual) autoSyncMetar = false
      val hpa = UnitConverter.convertInHgToHpa(inhg)
      updating = true
      qnhHpaField.text = String.format(Locale.US, "%.2f", hpa)
      updateDerived(hpa)
      updating = false
    }

    qnhHpaField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) = syncFromHpaInput(markManual = true)
      override fun removeUpdate(e: DocumentEvent?) = syncFromHpaInput(markManual = true)
      override fun changedUpdate(e: DocumentEvent?) = syncFromHpaInput(markManual = true)
    })
    qnhInHgField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) = syncFromInHgInput(markManual = true)
      override fun removeUpdate(e: DocumentEvent?) = syncFromInHgInput(markManual = true)
      override fun changedUpdate(e: DocumentEvent?) = syncFromInHgInput(markManual = true)
    })
    elevField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) {
        val hpa = qnhHpaField.text.trim().replace(',', '.').toDoubleOrNull() ?: return
        updateDerived(hpa)
      }
      override fun removeUpdate(e: DocumentEvent?) {
        val hpa = qnhHpaField.text.trim().replace(',', '.').toDoubleOrNull() ?: return
        updateDerived(hpa)
      }
      override fun changedUpdate(e: DocumentEvent?) {
        val hpa = qnhHpaField.text.trim().replace(',', '.').toDoubleOrNull() ?: return
        updateDerived(hpa)
      }
    })
    qnhHpaField.addActionListener { syncFromHpaInput(markManual = true) }
    qnhInHgField.addActionListener { syncFromInHgInput(markManual = true) }

    val panel = JPanel(GridBagLayout()).apply {
      border = BorderFactory.createEmptyBorder(14, 14, 14, 14)
      background = pal.panelSurface
    }
    val gbc = GridBagConstraints().apply {
      insets = Insets(6, 6, 6, 6)
      anchor = GridBagConstraints.WEST
      fill = GridBagConstraints.HORIZONTAL
    }
    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
    panel.add(JLabel("QNH (hPa / гПа):"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    panel.add(qnhHpaField, gbc)
    gbc.gridx = 2; gbc.weightx = 0.0
    panel.add(JButton("Из METAR").apply {
      addActionListener {
        autoSyncMetar = true
        val curQnh = controller.state.weather.qnhHpa?.toDouble() ?: 1013.25
        updating = true
        qnhHpaField.text = String.format(Locale.US, "%.2f", curQnh)
        qnhInHgField.text = String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(curQnh))
        elevField.text = controller.state.station.elevationMeters.toString()
        updateDerived(curQnh)
        updating = false
      }
    }, gbc)

    gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
    panel.add(JLabel("Altimeter (inHg):"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    panel.add(qnhInHgField, gbc)
    gbc.gridx = 2; gbc.weightx = 0.0
    panel.add(JButton("→ hPa").apply {
      addActionListener { syncFromInHgInput(markManual = true) }
    }, gbc)

    gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
    panel.add(JLabel("Превышение (м):"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    panel.add(elevField, gbc)
    gbc.gridx = 2; gbc.weightx = 0.0
    panel.add(JButton("Пересчёт QFE").apply {
      addActionListener { syncFromHpaInput(markManual = false) }
    }, gbc)

    gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3
    panel.add(qfeResultLabel, gbc)
    gbc.gridy = 4
    panel.add(isaDiffLabel, gbc)

    gbc.gridy = 5
    panel.add(
      JPanel(FlowLayout(FlowLayout.LEFT, 8, 2)).apply {
        isOpaque = false
        add(JLabel("Единицы на табло:"))
        add(JButton("hPa (гПа)").apply {
          addActionListener { controller.setPressureUnit(PressureUnit.HPA) }
        })
        add(JButton("inHg (дюймы)").apply {
          addActionListener { controller.setPressureUnit(PressureUnit.INHG) }
        })
      },
      gbc,
    )

    updateDerived(initialQnh)

    val listener: (DesktopUiState) -> Unit = { nextState ->
      if (autoSyncMetar) {
        val nextQnh = nextState.weather.qnhHpa?.toDouble()
        if (nextQnh != null) {
          updating = true
          qnhHpaField.text = String.format(Locale.US, "%.2f", nextQnh)
          qnhInHgField.text = String.format(Locale.US, "%.2f", UnitConverter.convertHpaToInHg(nextQnh))
          updateDerived(nextQnh)
          updating = false
        }
      }
      DesktopThemeHelper.applyThemeTree(dialog.contentPane, nextState.palette)
    }
    controller.addListener(listener)
    dialog.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        controller.removeListener(listener)
      }
    })

    dialog.contentPane.layout = BorderLayout()
    dialog.contentPane.add(panel, BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, pal)
    dialog.isVisible = true
  }

  fun showCrosswindCalculatorDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val st = controller.state
    val pal = st.palette
    val dialog = JDialog(owner, "Калькулятор бокового ветра · ${st.station.icao}", false)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(580, 390)
    dialog.setLocationRelativeTo(owner)

    val defaultHeading = st.primaryRunwayCalc.headingDeg
    val headingField = JTextField(String.format(Locale.US, "%03d", defaultHeading), 6)
    val windDirField = JTextField(
      st.weather.wind.directionDeg?.let { String.format(Locale.US, "%03d", it) } ?: "270",
      6,
    )
    val windSpeedField = JTextField(
      UnitConverter.formatWindSpeedValue(st.weather.wind.speedKt, st.windUnit).toString(),
      6,
    )
    val unitBtn = JButton(st.windUnit.label)
    var useCustomWind = false

    val headwindResultLabel = JLabel("", SwingConstants.LEFT).apply {
      font = Font("Consolas", Font.BOLD, 15)
      putClientProperty("preserveForeground", true)
    }
    val crosswindResultLabel = JLabel("", SwingConstants.LEFT).apply {
      font = Font("Consolas", Font.BOLD, 15)
      putClientProperty("preserveForeground", true)
    }
    val limitBannerLabel = JLabel("", SwingConstants.LEFT).apply {
      font = Font("Segoe UI", Font.BOLD, 12)
      putClientProperty("preserveForeground", true)
    }

    fun normalizeHeading(deg: Int): Int {
      val m = ((deg % 360) + 360) % 360
      return if (m == 0) 360 else m
    }

    fun recalculate(curState: DesktopUiState) {
      val activePal = curState.palette
      val rwyHdg = normalizeHeading(headingField.text.trim().toIntOrNull() ?: defaultHeading)
      val windDir = windDirField.text.trim().toIntOrNull()?.let { normalizeHeading(it) }
        ?: (curState.weather.wind.directionDeg ?: 270)
      val windDispSpeed = windSpeedField.text.trim().replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 200.0) ?: 0.0
      val speedKt = UnitConverter.convertWind(windDispSpeed, curState.windUnit, WindSpeedUnit.KT).roundToInt()

      val windData = if (!useCustomWind && (curState.weather.wind.isCalm || curState.weather.wind.isVariable)) {
        curState.weather.wind
      } else {
        WindData(
          directionDeg = windDir,
          speedKt = speedKt,
          gustKt = if (!useCustomWind) curState.weather.wind.gustKt else null,
          speedMps = UnitConverter.convertKnotsToMps(speedKt.toDouble()),
          isVariable = false,
          isCalm = speedKt == 0,
        )
      }

      val calc = AviationWeatherParser.calculateRunwayComponents(
        designator = String.format(Locale.US, "%02d", ((rwyHdg + 4) / 10).coerceIn(1, 36)),
        runwayHeadingDeg = rwyHdg,
        wind = windData,
        frictionCoeff = null,
        rvrMeters = null,
      )
      val headVal = UnitConverter.convertWindFromKnots(abs(calc.headwindKt), curState.windUnit)
      val crossVal = UnitConverter.convertWindFromKnots(calc.crosswindKt, curState.windUnit)
      val isTailwind = calc.headwindKt < -0.05
      val headPrefix = when {
        windData.isCalm -> "Штиль"
        windData.isVariable -> "VRB (поперечный)"
        isTailwind -> "Попутный (Tailwind): -"
        else -> "Встречный (Headwind): +"
      }
      headwindResultLabel.text = if (windData.isCalm || windData.isVariable) {
        headPrefix
      } else {
        String.format(Locale.US, "%s%.1f %s (ВПП %03d° / ветер %03d°)", headPrefix, headVal, curState.windUnit.label, rwyHdg, windDir)
      }
      headwindResultLabel.foreground = when {
        windData.isCalm || windData.isVariable -> activePal.textSecondary
        isTailwind -> activePal.tailwindNegative
        else -> activePal.headwindPositive
      }

      val sinVal = if (!windData.isCalm && !windData.isVariable && windData.directionDeg != null) {
        sin((windDir - rwyHdg) * PI / 180.0)
      } else 0.0
      val sideText = when {
        windData.isCalm -> ""
        windData.isVariable -> " (перем.)"
        sinVal > 0.02 -> " справа"
        sinVal < -0.02 -> " слева"
        else -> " по оси"
      }
      val exceeded = curState.alertConfig.isCrosswindExceeded(calc.crosswindKt, null)
      crosswindResultLabel.text = String.format(
        Locale.US,
        "Боковой (Crosswind)%s: %.1f %s",
        sideText,
        crossVal,
        curState.windUnit.label,
      )
      crosswindResultLabel.foreground = when {
        exceeded || calc.crosswindKt >= 20.0 -> activePal.tailwindNegative
        calc.crosswindKt >= 12.0 -> activePal.metarAmber
        else -> activePal.accentCyan
      }

      val limitDisp = UnitConverter.convertWindFromKnots(curState.alertConfig.maxCrosswindKt.toDouble(), curState.windUnit)
      val acName = curState.alertConfig.resolvedAircraftName()
      if (exceeded) {
        limitBannerLabel.text = String.format(Locale.US, "⚠ ПРЕВЫШЕНИЕ для %s: лимит %.0f %s", acName, limitDisp, curState.windUnit.label)
        limitBannerLabel.foreground = activePal.tailwindNegative
      } else {
        limitBannerLabel.text = String.format(Locale.US, "Ограничение %s: %.0f %s — в допуске", acName, limitDisp, curState.windUnit.label)
        limitBannerLabel.foreground = activePal.headwindPositive
      }
    }

    val panel = JPanel(GridBagLayout()).apply {
      border = BorderFactory.createEmptyBorder(14, 14, 14, 14)
    }
    val gbc = GridBagConstraints().apply {
      insets = Insets(6, 6, 6, 6)
      anchor = GridBagConstraints.WEST
      fill = GridBagConstraints.HORIZONTAL
    }

    gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
    panel.add(JLabel("Курс ВПП (°):"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    panel.add(headingField, gbc)
    gbc.gridx = 2; gbc.weightx = 0.0
    panel.add(
      JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        isOpaque = false
        add(JButton("-10°").apply {
          addActionListener {
            val cur = headingField.text.trim().toIntOrNull() ?: defaultHeading
            headingField.text = String.format(Locale.US, "%03d", normalizeHeading(cur - 10))
          }
        })
        add(JButton("+10°").apply {
          addActionListener {
            val cur = headingField.text.trim().toIntOrNull() ?: defaultHeading
            headingField.text = String.format(Locale.US, "%03d", normalizeHeading(cur + 10))
          }
        })
        add(JButton("180°").apply {
          addActionListener {
            val cur = headingField.text.trim().toIntOrNull() ?: defaultHeading
            headingField.text = String.format(Locale.US, "%03d", normalizeHeading(cur + 180))
          }
        })
      },
      gbc,
    )

    gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
    panel.add(JLabel("Направление ветра (°):"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    panel.add(windDirField, gbc)
    gbc.gridx = 2; gbc.weightx = 0.0
    panel.add(
      JButton("Из METAR").apply {
        addActionListener {
          useCustomWind = false
          val cur = controller.state
          windDirField.text = cur.weather.wind.directionDeg?.let { String.format(Locale.US, "%03d", it) } ?: "270"
          windSpeedField.text = UnitConverter.formatWindSpeedValue(cur.weather.wind.speedKt, cur.windUnit).toString()
          recalculate(cur)
        }
      },
      gbc,
    )

    gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
    panel.add(JLabel("Скорость ветра:"), gbc)
    gbc.gridx = 1; gbc.weightx = 1.0
    panel.add(windSpeedField, gbc)
    gbc.gridx = 2; gbc.weightx = 0.0
    unitBtn.addActionListener { controller.toggleWindUnit() }
    panel.add(unitBtn, gbc)

    gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3
    panel.add(headwindResultLabel, gbc)
    gbc.gridy = 4
    panel.add(crosswindResultLabel, gbc)
    gbc.gridy = 5
    panel.add(limitBannerLabel, gbc)

    val liveDocListener = object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) {
        useCustomWind = true
        recalculate(controller.state)
      }
      override fun removeUpdate(e: DocumentEvent?) {
        useCustomWind = true
        recalculate(controller.state)
      }
      override fun changedUpdate(e: DocumentEvent?) {
        useCustomWind = true
        recalculate(controller.state)
      }
    }
    headingField.document.addDocumentListener(object : DocumentListener {
      override fun insertUpdate(e: DocumentEvent?) = recalculate(controller.state)
      override fun removeUpdate(e: DocumentEvent?) = recalculate(controller.state)
      override fun changedUpdate(e: DocumentEvent?) = recalculate(controller.state)
    })
    windDirField.document.addDocumentListener(liveDocListener)
    windSpeedField.document.addDocumentListener(liveDocListener)

    val stateListener: (DesktopUiState) -> Unit = { nextState ->
      unitBtn.text = nextState.windUnit.label
      if (!useCustomWind) {
        windDirField.text = nextState.weather.wind.directionDeg?.let { String.format(Locale.US, "%03d", it) } ?: "270"
        windSpeedField.text = UnitConverter.formatWindSpeedValue(nextState.weather.wind.speedKt, nextState.windUnit).toString()
      }
      recalculate(nextState)
      DesktopThemeHelper.applyThemeTree(dialog.contentPane, nextState.palette)
    }
    controller.addListener(stateListener)
    dialog.addWindowListener(object : WindowAdapter() {
      override fun windowClosed(e: WindowEvent?) {
        controller.removeListener(stateListener)
      }
    })

    recalculate(st)
    dialog.contentPane.layout = BorderLayout()
    dialog.contentPane.add(panel, BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, pal)
    dialog.isVisible = true
  }

  fun showDualWindRoseDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val st = controller.state
    val pal = st.palette
    val dialog = JDialog(owner, "Двойная Роза Ветров ВПП · ${st.station.icao}", false)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(860, 560)
    dialog.setLocationRelativeTo(owner)

    val pairSelector = JComboBox(st.station.runways.map { "${it.primaryDesignator} / ${it.reciprocalDesignator}" }.toTypedArray()).apply {
      selectedIndex = st.selectedRunwayPairIndex.coerceIn(0, st.station.runways.lastIndex)
    }
    val leftRose = RunwayWindRosePanel { controller.toggleWindUnit() }
    val rightRose = RunwayWindRosePanel { controller.toggleWindUnit() }
    val summaryLabel = JLabel("", SwingConstants.CENTER).apply {
      font = Font("Consolas", Font.BOLD, 13)
    }

    val centerGrid = JPanel(GridLayout(1, 2, 12, 8)).apply {
      border = BorderFactory.createEmptyBorder(8, 12, 8, 12)
      add(leftRose)
      add(rightRose)
    }

    fun refreshRoses(stateSnap: DesktopUiState) {
      val idx = pairSelector.selectedIndex.coerceIn(0, stateSnap.station.runways.lastIndex)
      val pair = stateSnap.station.runways[idx]
      val primary = com.example.domain.AviationWeatherParser.calculateRunwayComponents(
        designator = pair.primaryDesignator,
        runwayHeadingDeg = pair.primaryHeadingDeg,
        wind = stateSnap.weather.wind,
        frictionCoeff = stateSnap.weather.frictionCoeff,
        rvrMeters = stateSnap.weather.rvrPrimaryMeters,
      )
      val reciprocal = com.example.domain.AviationWeatherParser.calculateRunwayComponents(
        designator = pair.reciprocalDesignator,
        runwayHeadingDeg = pair.reciprocalHeadingDeg,
        wind = stateSnap.weather.wind,
        frictionCoeff = stateSnap.weather.frictionCoeff,
        rvrMeters = stateSnap.weather.rvrReciprocalMeters,
      )
      leftRose.updateData(primary, stateSnap.weather.wind, stateSnap.windUnit, pal, stateSnap.metarFetchSeq)
      rightRose.updateData(reciprocal, stateSnap.weather.wind, stateSnap.windUnit, pal, stateSnap.metarFetchSeq)

      val h1 = UnitConverter.convertWindFromKnots(primary.headwindKt, stateSnap.windUnit)
      val c1 = UnitConverter.convertWindFromKnots(primary.crosswindKt, stateSnap.windUnit)
      val h2 = UnitConverter.convertWindFromKnots(reciprocal.headwindKt, stateSnap.windUnit)
      val c2 = UnitConverter.convertWindFromKnots(reciprocal.crosswindKt, stateSnap.windUnit)
      summaryLabel.text = String.format(
        Locale.US,
        "ВПП %s: Встр %.1f / Бок %.1f %s   |   ВПП %s: Встр %.1f / Бок %.1f %s",
        primary.designator, h1, c1, stateSnap.windUnit.label,
        reciprocal.designator, h2, c2, stateSnap.windUnit.label,
      )
    }

    pairSelector.addActionListener {
      controller.selectRunwayPair(pairSelector.selectedIndex)
      refreshRoses(controller.state)
    }
    refreshRoses(st)

    val topBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 6)).apply {
      add(JLabel("Полоса ВПП:"))
      add(pairSelector)
      add(JButton("Сменить ед. ветра").apply {
        addActionListener {
          controller.toggleWindUnit()
          refreshRoses(controller.state)
        }
      })
    }

    dialog.contentPane.layout = BorderLayout(6, 6)
    dialog.contentPane.add(topBar, BorderLayout.NORTH)
    dialog.contentPane.add(centerGrid, BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(BorderLayout(6, 6)).apply {
        border = BorderFactory.createEmptyBorder(4, 10, 8, 10)
        add(summaryLabel, BorderLayout.CENTER)
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } }, BorderLayout.EAST)
      },
      BorderLayout.SOUTH,
    )
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, pal, skipComponents = setOf(leftRose, rightRose))
    dialog.isVisible = true
  }

  fun showExportDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val st = controller.state
    val pal = st.palette
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
    val summaryText = WeatherReportFormatter.buildTextSummary(payload)
    val baseName = WeatherReportFormatter.suggestedFileBaseName(st.station, st.weather)

    val dialog = JDialog(owner, "Экспорт метеосводки и трендов · ${st.station.icao}", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(720, 520)
    dialog.setLocationRelativeTo(owner)

    val previewArea = JTextArea(summaryText).apply {
      isEditable = false
      font = Font("Consolas", Font.PLAIN, 12)
      background = pal.fieldBackground
      foreground = pal.textPrimary
      border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }

    val saveTxtBtn = JButton("Сохранить .TXT").apply {
      addActionListener {
        val chooser = JFileChooser().apply {
          selectedFile = File("$baseName.txt")
          fileFilter = FileNameExtensionFilter("Текстовый файл (*.txt)", "txt")
        }
        if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
          val file = chooser.selectedFile.let {
            if (it.name.endsWith(".txt", ignoreCase = true)) it else File("${it.absolutePath}.txt")
          }
          file.writeText(summaryText, StandardCharsets.UTF_8)
          JOptionPane.showMessageDialog(dialog, "Сводка сохранена:\n${file.absolutePath}")
        }
      }
    }

    val saveHtmlBtn = JButton("Сохранить отчёт .HTML").apply {
      addActionListener {
        val chooser = JFileChooser().apply {
          selectedFile = File("$baseName.html")
          fileFilter = FileNameExtensionFilter("HTML отчёт для печати / PDF (*.html)", "html")
        }
        if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
          val file = chooser.selectedFile.let {
            if (it.name.endsWith(".html", ignoreCase = true)) it else File("${it.absolutePath}.html")
          }
          val html = WeatherReportFormatter.buildPrintableHtml(payload)
          file.writeText(html, StandardCharsets.UTF_8)
          JOptionPane.showMessageDialog(dialog, "HTML-отчёт сохранён:\n${file.absolutePath}")
        }
      }
    }

    val copyBtn = JButton("Копировать текст").apply {
      addActionListener {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(summaryText), null)
      }
    }

    dialog.contentPane.layout = BorderLayout(8, 8)
    dialog.contentPane.add(JScrollPane(previewArea), BorderLayout.CENTER)
    dialog.contentPane.add(
      JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8)).apply {
        add(copyBtn)
        add(saveTxtBtn)
        add(saveHtmlBtn)
        add(JButton("Закрыть").apply { addActionListener { dialog.dispose() } })
      },
      BorderLayout.SOUTH,
    )
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, pal)
    dialog.isVisible = true
  }

  fun showLogDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val pal = controller.state.palette
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
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, pal)
    dialog.isVisible = true
  }

  fun showAlertsDialog(
    owner: Frame,
    controller: DesktopController,
  ) {
    val dialog = JDialog(owner, "Оповещения, пороги таблицы и минимумы VFR", true)
    dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
    dialog.setSize(740, 560)
    dialog.setLocationRelativeTo(owner)

    val state = controller.state
    val cfg = state.alertConfig
    val enabledCheck = JCheckBox("Включить оповещения (${state.favoriteIcaos.joinToString(", ")})", cfg.enabled)
    val windCombo = JComboBox(arrayOf("±5 kt", "±8 kt", "±12 kt")).apply {
      selectedIndex = when (cfg.windChangeThresholdKt) {
        5 -> 0
        12 -> 2
        else -> 1
      }
    }
    val visCombo = JComboBox(arrayOf("< 1500 м / 500 ft", "< 3000 м / 1000 ft", "< 5000 м / 1500 ft")).apply {
      selectedIndex = when (cfg.visibilityThresholdMeters) {
        1500 -> 0
        5000 -> 2
        else -> 1
      }
    }

    val tableAlertCheck = JCheckBox("Подсветка в таблице METAR", cfg.tableAlertsEnabled)
    val tableWindCombo = JComboBox(arrayOf("≥ 15 kt", "≥ 20 kt", "≥ 25 kt", "≥ 30 kt")).apply {
      selectedIndex = when (cfg.tableWindAlertKt) {
        15 -> 0
        20 -> 1
        30 -> 3
        else -> 2
      }
    }
    val tableVisCombo = JComboBox(arrayOf("≤ 1500 м", "≤ 3000 м", "≤ 5000 м")).apply {
      selectedIndex = when (cfg.tableVisibilityAlertMeters) {
        1500 -> 0
        5000 -> 2
        else -> 1
      }
    }

    val vfrCheck = JCheckBox("Оповещения VFR (ПВП минимумы)", cfg.vfrAlertsEnabled)
    val vfrVisCombo = JComboBox(arrayOf("3000 м", "5000 м", "8000 м")).apply {
      selectedIndex = when (cfg.vfrMinVisibilityMeters) {
        3000 -> 0
        8000 -> 2
        else -> 1
      }
    }
    val vfrCeilCombo = JComboBox(arrayOf("1000 ft", "1500 ft", "2500 ft")).apply {
      selectedIndex = when (cfg.vfrMinCeilingFeet) {
        1000 -> 0
        2500 -> 2
        else -> 1
      }
    }
    val vfrWindCombo = JComboBox(arrayOf("15 kt", "20 kt", "25 kt")).apply {
      selectedIndex = when (cfg.vfrMaxWindKt) {
        15 -> 0
        25 -> 2
        else -> 1
      }
    }
    val vfrAllCheck = JCheckBox("Все избранные аэропорты VFR", cfg.vfrMonitorAllFavorites)

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
      val tWind = when (tableWindCombo.selectedIndex) {
        0 -> 15
        1 -> 20
        3 -> 30
        else -> 25
      }
      val tVis = when (tableVisCombo.selectedIndex) {
        0 -> 1500
        2 -> 5000
        else -> 3000
      }
      val vVis = when (vfrVisCombo.selectedIndex) {
        0 -> 3000
        2 -> 8000
        else -> 5000
      }
      val vCeil = when (vfrCeilCombo.selectedIndex) {
        0 -> 1000
        2 -> 2500
        else -> 1500
      }
      val vWind = when (vfrWindCombo.selectedIndex) {
        0 -> 15
        2 -> 25
        else -> 20
      }
      controller.updateAlertConfig(
        controller.state.alertConfig.copy(
          enabled = enabledCheck.isSelected,
          windChangeThresholdKt = windKt,
          visibilityThresholdMeters = visM,
          ceilingThresholdMeters = ceilM,
          tableAlertsEnabled = tableAlertCheck.isSelected,
          tableWindAlertKt = tWind,
          tableVisibilityAlertMeters = tVis,
          vfrAlertsEnabled = vfrCheck.isSelected,
          vfrMinVisibilityMeters = vVis,
          vfrMinCeilingFeet = vCeil,
          vfrMaxWindKt = vWind,
          vfrMonitorAllFavorites = vfrAllCheck.isSelected,
        ),
      )
    }

    enabledCheck.addActionListener { applyAlertPrefs() }
    windCombo.addActionListener { applyAlertPrefs() }
    visCombo.addActionListener { applyAlertPrefs() }
    tableAlertCheck.addActionListener { applyAlertPrefs() }
    tableWindCombo.addActionListener { applyAlertPrefs() }
    tableVisCombo.addActionListener { applyAlertPrefs() }
    vfrCheck.addActionListener { applyAlertPrefs() }
    vfrVisCombo.addActionListener { applyAlertPrefs() }
    vfrCeilCombo.addActionListener { applyAlertPrefs() }
    vfrWindCombo.addActionListener { applyAlertPrefs() }
    vfrAllCheck.addActionListener { applyAlertPrefs() }

    val topPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
      add(enabledCheck)
      add(
        JPanel(FlowLayout(FlowLayout.LEFT, 8, 3)).apply {
          add(JLabel("Сдвиг ветра:"))
          add(windCombo)
          add(JLabel("Порог видимости:"))
          add(visCombo)
        },
      )
      add(
        JPanel(FlowLayout(FlowLayout.LEFT, 8, 3)).apply {
          add(tableAlertCheck)
          add(JLabel("Ветер:"))
          add(tableWindCombo)
          add(JLabel("Вид.:"))
          add(tableVisCombo)
        },
      )
      add(
        JPanel(FlowLayout(FlowLayout.LEFT, 8, 3)).apply {
          add(vfrCheck)
          add(vfrVisCombo)
          add(vfrCeilCombo)
          add(vfrWindCombo)
          add(vfrAllCheck)
        },
      )
    }

    val listPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
    }
    if (state.weatherAlerts.isEmpty()) {
      listPanel.add(JLabel("Пока нет зафиксированных изменений погоды или нарушений VFR."))
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
        JButton("Проверить VFR / Избранные").apply {
          addActionListener {
            controller.checkVfrMinimumsNow()
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
    DesktopThemeHelper.applyThemeTree(dialog.contentPane, state.palette)
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
