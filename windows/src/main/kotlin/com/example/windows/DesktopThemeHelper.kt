package com.example.windows

import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.TitledBorder
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicComboBoxUI
import javax.swing.plaf.basic.BasicMenuItemUI
import javax.swing.plaf.basic.BasicTabbedPaneUI
import javax.swing.table.DefaultTableCellRenderer

object DesktopThemeHelper {

  fun styleButton(
    btn: JButton,
    pal: DesktopPalette,
    isActive: Boolean = false,
    accentBorder: Color? = null,
    padding: Insets = Insets(4, 9, 4, 9),
  ) {
    if (btn.ui !is BasicButtonUI) {
      btn.setUI(BasicButtonUI())
    }
    btn.isOpaque = true
    btn.isContentAreaFilled = true
    btn.isFocusPainted = false
    btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    btn.background = if (isActive) pal.buttonActiveBackground else pal.buttonBackground
    if (btn.getClientProperty("preserveForeground") != true) {
      btn.foreground = pal.textPrimary
    }
    val borderColor = accentBorder ?: if (isActive) pal.borderStrong else pal.border
    val thickness = if (isActive || accentBorder != null) 2 else 1
    btn.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(borderColor, thickness),
      BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right),
    )
  }

  fun styleAccentButton(
    btn: JButton,
    bg: Color,
    fg: Color = Color.WHITE,
    padding: Insets = Insets(5, 12, 5, 12),
  ) {
    if (btn.ui !is BasicButtonUI) {
      btn.setUI(BasicButtonUI())
    }
    btn.isOpaque = true
    btn.isContentAreaFilled = true
    btn.isFocusPainted = false
    btn.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    btn.background = bg
    btn.foreground = fg
    btn.border = BorderFactory.createEmptyBorder(padding.top, padding.left, padding.bottom, padding.right)
  }

  @Suppress("UNCHECKED_CAST")
  fun <T> styleComboBox(combo: JComboBox<T>, pal: DesktopPalette) {
    if (combo.ui !is BasicComboBoxUI) {
      combo.setUI(object : BasicComboBoxUI() {
        override fun createArrowButton(): JButton {
          return JButton("▾").apply {
            setUI(BasicButtonUI())
            isOpaque = true
            isContentAreaFilled = true
            isFocusPainted = false
            background = pal.buttonBackground
            foreground = pal.textPrimary
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, pal.border)
          }
        }
      })
    }
    combo.isOpaque = true
    combo.background = pal.fieldBackground
    combo.foreground = pal.textPrimary
    combo.border = BorderFactory.createLineBorder(pal.border, 1)
    combo.renderer = object : DefaultListCellRenderer() {
      override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
      ): Component {
        val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        comp.background = if (isSelected) pal.buttonActiveBackground else pal.fieldBackground
        comp.foreground = pal.textPrimary
        return comp
      }
    }
    for (i in 0 until combo.componentCount) {
      val child = combo.getComponent(i)
      if (child is JButton) {
        if (child.ui !is BasicButtonUI) child.setUI(BasicButtonUI())
        child.isOpaque = true
        child.isContentAreaFilled = true
        child.background = pal.buttonBackground
        child.foreground = pal.textPrimary
        child.border = BorderFactory.createMatteBorder(0, 1, 0, 0, pal.border)
      }
    }
  }

  fun styleTabbedPane(tabs: JTabbedPane, pal: DesktopPalette) {
    tabs.isOpaque = true
    tabs.background = pal.panelSurface
    tabs.foreground = pal.textPrimary
    tabs.putClientProperty("pal_panelSurface", pal.panelSurface)
    tabs.putClientProperty("pal_tabBg", pal.secondaryFieldBackground)
    tabs.putClientProperty("pal_tabSelectedBg", pal.buttonActiveBackground)
    tabs.putClientProperty("pal_border", pal.borderStrong)
    tabs.putClientProperty("pal_textPrimary", pal.textPrimary)
    tabs.putClientProperty("pal_textSecondary", pal.textSecondary)
    tabs.putClientProperty("pal_accent", pal.metarAmber)

    if (tabs.getClientProperty("customTabbedUiInstalled") != true) {
      tabs.putClientProperty("customTabbedUiInstalled", true)
      tabs.setUI(object : BasicTabbedPaneUI() {
        override fun installDefaults() {
          super.installDefaults()
          tabAreaInsets = Insets(4, 6, 0, 6)
          selectedTabPadInsets = Insets(2, 4, 2, 4)
        }

        override fun paintTabArea(g: Graphics, tabPlacement: Int, selectedIndex: Int) {
          val bg = (tabPane.getClientProperty("pal_panelSurface") as? Color) ?: tabPane.background
          g.color = bg
          g.fillRect(0, 0, tabPane.width, calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight))
          super.paintTabArea(g, tabPlacement, selectedIndex)
        }

        override fun paintTabBackground(
          g: Graphics,
          tabPlacement: Int,
          tabIndex: Int,
          x: Int,
          y: Int,
          w: Int,
          h: Int,
          isSelected: Boolean,
        ) {
          val g2 = g.create() as Graphics2D
          try {
            val selBg = (tabPane.getClientProperty("pal_tabSelectedBg") as? Color) ?: pal.buttonActiveBackground
            val normBg = (tabPane.getClientProperty("pal_tabBg") as? Color) ?: pal.secondaryFieldBackground
            g2.color = if (isSelected) selBg else normBg
            g2.fillRect(x, y, w, h)
            if (isSelected) {
              g2.color = (tabPane.getClientProperty("pal_accent") as? Color) ?: pal.metarAmber
              g2.fillRect(x, y, w, 2)
            }
          } finally {
            g2.dispose()
          }
        }

        override fun paintTabBorder(
          g: Graphics,
          tabPlacement: Int,
          tabIndex: Int,
          x: Int,
          y: Int,
          w: Int,
          h: Int,
          isSelected: Boolean,
        ) {
          g.color = (tabPane.getClientProperty("pal_border") as? Color) ?: pal.borderStrong
          g.drawRect(x, y, w - 1, h - 1)
        }

        override fun paintContentBorder(g: Graphics, tabPlacement: Int, selectedIndex: Int) {
          val borderClr = (tabPane.getClientProperty("pal_border") as? Color) ?: pal.borderStrong
          val th = calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight)
          g.color = borderClr
          g.drawRect(0, th - 1, tabPane.width - 1, (tabPane.height - th).coerceAtLeast(1))
        }

        override fun paintFocusIndicator(
          g: Graphics,
          tabPlacement: Int,
          rects: Array<out Rectangle>,
          tabIndex: Int,
          iconRect: Rectangle,
          textRect: Rectangle,
          isSelected: Boolean,
        ) = Unit

        override fun paintText(
          g: Graphics,
          tabPlacement: Int,
          font: java.awt.Font,
          metrics: FontMetrics,
          tabIndex: Int,
          title: String,
          textRect: Rectangle,
          isSelected: Boolean,
        ) {
          val g2 = g.create() as Graphics2D
          try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.font = font
            val prim = (tabPane.getClientProperty("pal_textPrimary") as? Color) ?: pal.textPrimary
            val sec = (tabPane.getClientProperty("pal_textSecondary") as? Color) ?: pal.textSecondary
            g2.color = if (isSelected) prim else sec
            g2.drawString(title, textRect.x, textRect.y + metrics.ascent)
          } finally {
            g2.dispose()
          }
        }
      })
    }
    tabs.repaint()
  }

  fun stylePopupMenu(menu: JPopupMenu, pal: DesktopPalette) {
    menu.isOpaque = true
    menu.background = pal.panelSurface
    menu.foreground = pal.textPrimary
    menu.border = BorderFactory.createLineBorder(pal.borderStrong, 1)
    menu.components.forEach { comp ->
      if (comp is JMenuItem) {
        comp.setUI(object : BasicMenuItemUI() {
          init {
            selectionBackground = pal.buttonActiveBackground
            selectionForeground = pal.metarAmber
          }
        })
        comp.isOpaque = true
        comp.background = pal.panelSurface
        comp.foreground = pal.textPrimary
      } else if (comp is JPopupMenu.Separator) {
        comp.background = pal.panelSurface
        comp.foreground = pal.border
      }
    }
  }

  fun toHexColor(color: Color): String {
    return String.format("#%02X%02X%02X", color.red, color.green, color.blue)
  }

  fun wrapHtml(pal: DesktopPalette, innerHtml: String): String {
    val hex = toHexColor(pal.textPrimary)
    return "<html><body style='color:$hex;margin:0;padding:0;'>$innerHtml</body></html>"
  }

  fun syncUiManagerDefaults(pal: DesktopPalette) {
    UIManager.put("Label.foreground", pal.textPrimary)
    UIManager.put("Panel.background", pal.panelSurface)
    UIManager.put("OptionPane.background", pal.panelSurface)
    UIManager.put("OptionPane.messageForeground", pal.textPrimary)
    UIManager.put("ToolTip.background", pal.secondaryFieldBackground)
    UIManager.put("ToolTip.foreground", pal.textPrimary)
    UIManager.put("TableHeader.background", pal.secondaryFieldBackground)
    UIManager.put("TableHeader.foreground", pal.textPrimary)
  }

  private fun updateTitledBorder(border: Border?, pal: DesktopPalette) {
    when (border) {
      is TitledBorder -> border.titleColor = pal.textSecondary
      is CompoundBorder -> {
        updateTitledBorder(border.outsideBorder, pal)
        updateTitledBorder(border.insideBorder, pal)
      }
    }
  }

  fun applyThemeTree(
    root: Container,
    pal: DesktopPalette,
    skipComponents: Set<Component> = emptySet(),
  ) {
    syncUiManagerDefaults(pal)
    if (root in skipComponents) return
    if (root is JDialog) {
      root.background = pal.panelSurface
      (root.contentPane as? Container)?.let { cp ->
        cp.background = pal.panelSurface
      }
    } else if (root is RootPaneContainer) {
      root.contentPane.background = pal.panelSurface
    }
    when (root) {
      is JTabbedPane -> styleTabbedPane(root, pal)
      is JPanel -> {
        if (root.isOpaque && root.getClientProperty("preserveBackground") != true) {
          root.background = pal.panelSurface
        }
        updateTitledBorder(root.border, pal)
      }
      is JScrollPane -> {
        root.background = pal.panelSurface
        root.viewport.background = pal.panelSurface
        root.columnHeader?.background = pal.secondaryFieldBackground
        root.columnHeader?.view?.background = pal.secondaryFieldBackground
        updateTitledBorder(root.border, pal)
      }
      is JSplitPane -> {
        root.background = pal.panelSurface
      }
    }
    root.components.forEach { comp ->
      if (comp in skipComponents) return@forEach
      when (comp) {
        is JButton -> {
          if (comp.getClientProperty("accentStyled") == true) {
            styleAccentButton(comp, pal.metarAmber, Color.WHITE)
          } else {
            styleButton(comp, pal)
          }
        }
        is JComboBox<*> -> styleComboBox(comp, pal)
        is JCheckBox -> {
          comp.isOpaque = false
          comp.background = pal.panelSurface
          comp.foreground = pal.textPrimary
        }
        is JRadioButton -> {
          comp.isOpaque = false
          comp.background = pal.panelSurface
          comp.foreground = pal.textPrimary
        }
        is JSlider -> {
          comp.isOpaque = false
          comp.background = pal.panelSurface
          comp.foreground = pal.textPrimary
        }
        is JList<*> -> {
          comp.background = pal.fieldBackground
          comp.foreground = pal.textPrimary
          comp.selectionBackground = pal.buttonActiveBackground
          comp.selectionForeground = pal.textPrimary
        }
        is JLabel -> {
          val targetFg = if (comp.getClientProperty("preserveForeground") == true) {
            comp.foreground
          } else {
            pal.textPrimary
          }
          comp.foreground = targetFg
          val rawBody = comp.getClientProperty("htmlBody") as? String
          if (rawBody != null) {
            val hex = toHexColor(targetFg)
            comp.text = "<html><body style='color:$hex;margin:0;padding:0;'>$rawBody</body></html>"
          }
        }
        is JTextField -> {
          comp.background = pal.fieldBackground
          if (comp.getClientProperty("preserveForeground") != true) {
            comp.foreground = pal.textPrimary
          }
          comp.caretColor = pal.textPrimary
          if (comp.getClientProperty("preserveBorder") != true) {
            comp.border = BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(pal.border, 1),
              BorderFactory.createEmptyBorder(3, 6, 3, 6),
            )
          }
        }
        is JTextArea -> {
          comp.background = pal.fieldBackground
          if (comp.getClientProperty("preserveForeground") != true) {
            comp.foreground = pal.textPrimary
          }
          comp.caretColor = pal.textPrimary
        }
        is JTable -> {
          comp.isOpaque = true
          comp.background = pal.fieldBackground
          comp.foreground = pal.textPrimary
          comp.gridColor = pal.border
          comp.selectionBackground = pal.buttonActiveBackground
          comp.selectionForeground = pal.textPrimary
          comp.tableHeader?.let { hdr ->
            hdr.isOpaque = true
            hdr.background = pal.secondaryFieldBackground
            hdr.foreground = pal.textPrimary
            hdr.border = BorderFactory.createMatteBorder(0, 0, 1, 0, pal.borderStrong)
            hdr.defaultRenderer = object : DefaultTableCellRenderer() {
              override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int,
              ): Component {
                val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
                c.isOpaque = true
                c.background = pal.secondaryFieldBackground
                c.foreground = pal.textPrimary
                c.border = BorderFactory.createCompoundBorder(
                  BorderFactory.createMatteBorder(0, 0, 1, 1, pal.border),
                  BorderFactory.createEmptyBorder(4, 6, 4, 6),
                )
                return c
              }
            }
          }
        }
        is Container -> applyThemeTree(comp, pal, skipComponents)
      }
    }
  }
}
