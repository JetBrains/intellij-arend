package org.arend.search.proof

import com.intellij.ide.actions.BigPopupUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*

class ProofSearchUI(project : Project?) : BigPopupUI(project) {
    init {
        init()
    }

    override fun dispose() {}

    override fun createList(): JBList<Any> {
        val listModel = CollectionListModel<Any>()
        addListDataListener(listModel)

        return JBList(listModel)
    }

    override fun createCellRenderer(): ListCellRenderer<Any> {
        return object : ColoredListCellRenderer<Any>() {
            override fun customizeCellRenderer(
                list: JList<out Any>,
                value: Any?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {

            }
        }
    }

    override fun createTopLeftPanel(): JPanel {
        val title = JLabel("Proof Search")
        val topPanel: JPanel = NonOpaquePanel(BorderLayout())
        val foregroundColor =
            if (StartupUiUtil.isUnderDarcula()) if (UIUtil.isUnderWin10LookAndFeel()) JBColor.WHITE else JBColor(
                Gray._240, Gray._200
            ) else UIUtil.getLabelForeground()


        title.foreground = foregroundColor
        title.border = BorderFactory.createEmptyBorder(3, 5, 5, 0)
        if (SystemInfo.isMac) {
            title.font = title.font.deriveFont(Font.BOLD, title.font.size - 1f)
        } else {
            title.font = title.font.deriveFont(Font.BOLD)
        }

        topPanel.add(title)

        return topPanel
    }

    override fun createSettingsPanel(): JPanel {
        val res = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        res.isOpaque = false

        val actionGroup = DefaultActionGroup()
        val toolbar = ActionManager.getInstance().createActionToolbar("proof.search.top.toolbar", actionGroup, true)
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        val toolbarComponent = toolbar.component
        toolbarComponent.isOpaque = false
        res.add(toolbarComponent)
        return res
    }

    override fun getAccessibleName(): String = "Proof search"
}