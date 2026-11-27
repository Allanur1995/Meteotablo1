package com.example.windows

import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import javax.swing.border.TitledBorder
import javax.swing.plaf.basic.BasicButtonUI
import javax.swing.plaf.basic.BasicComboBoxUI

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
    // Style arrow button child inside BasicComboBoxUI
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
    if (root in skipComponents) return
    when (root) {
      is JTabbedPane -> {
        root.isOpaque = true
        root.background = pal.panelSurface
        root.foreground = pal.textPrimary
      }
      is JPanel -> {
        if (root.isOpaque) {
          root.background = pal.panelSurface
        }
        updateTitledBorder(root.border, pal)
      }
      is JScrollPane -> {
        root.background = pal.panelSurface
        root.viewport.background = pal.panelSurface
        updateTitledBorder(root.border, pal)
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
          comp.foreground = pal.textPrimary
        }
        is JLabel -> {
          if (comp.getClientProperty("preserveForeground") != true) {
            comp.foreground = pal.textPrimary
          }
        }
        is JTextField -> {
          comp.background = pal.fieldBackground
          comp.foreground = pal.textPrimary
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
          comp.background = pal.fieldBackground
          comp.foreground = pal.textPrimary
          comp.gridColor = pal.border
          comp.selectionBackground = pal.buttonActiveBackground
          comp.selectionForeground = pal.textPrimary
          comp.tableHeader?.let { hdr ->
            hdr.background = pal.secondaryFieldBackground
            hdr.foreground = pal.textPrimary
          }
        }
        is Container -> applyThemeTree(comp, pal, skipComponents)
      }
    }
  }
}
