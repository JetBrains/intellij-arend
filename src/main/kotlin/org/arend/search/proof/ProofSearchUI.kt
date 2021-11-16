package org.arend.search.proof

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.ide.actions.BigPopupUI
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.arend.ArendIcons
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.DocumentEvent

class ProofSearchUI(project : Project?) : BigPopupUI(project) {

    private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val model: CollectionListModel<Any> = CollectionListModel()

    @Volatile
    private var progressIndicator: ProgressIndicator? = null
        get() = synchronized(this) {
            field
        }
        set(value) = synchronized(this) {
            field = value
        }


    init {
        init()
        initSearchActions()
    }

    override fun dispose() {}

    override fun createList(): JBList<Any> {
        addListDataListener(model)

        return JBList(model)
    }

    override fun createCellRenderer(): ListCellRenderer<Any> {
        // todo: better generics
        return ArendProofSearchRenderer()
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

    private fun initSearchActions() {
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val newSearchString = searchPattern
                scheduleSearch()
            }
        })
        val escape = ActionManager.getInstance().getAction("EditorEscape")
        DumbAwareAction.create { close() }
            .registerCustomShortcutSet(escape?.shortcutSet ?: CommonShortcuts.ESCAPE, this)

    }

    private fun scheduleSearch() {
        if (!searchAlarm.isDisposed && searchAlarm.activeRequestCount == 0) {
            searchAlarm.addRequest({ runProofSearch() }, 100)
        }
    }

    private fun runProofSearch() {
        val project = myProject ?: return
        progressIndicator?.cancel()
        invokeLater {
            model.removeAll()
        }
        runBackgroundableTask("Proof Search", myProject) { progressIndicator ->
            invokeLater {
                this.progressIndicator = progressIndicator
            }
            fetchWeightedElements(project, searchPattern, progressIndicator) { entry ->
                invokeLater {
                    model.add(entry)
                }
                true
            }
        }
    }

    override fun createSearchField(): ExtendableTextField {
        val res: SearchField = object : SearchField() {
            override fun getAccessibleContext(): AccessibleContext {
                if (accessibleContext == null) {
                    accessibleContext = TextFieldWithListAccessibleContext(this, myResultsList.accessibleContext)
                }
                return accessibleContext
            }
        }
        val leftExt: ExtendableTextComponent.Extension = object : ExtendableTextComponent.Extension {
            override fun getIcon(hovered: Boolean): Icon {
                return ArendIcons.TURNSTILE
            }

            override fun isIconBeforeText(): Boolean {
                return true
            }

            override fun getIconGap(): Int {
                return JBUIScale.scale(10)
            }
        }
        res.addExtension(leftExt)
        res.putClientProperty(SearchEverywhereUI.SEARCH_EVERYWHERE_SEARCH_FILED_KEY, true)
        res.layout = BorderLayout()
        return res
    }

    fun close() {
        stopSearch()
        searchFinishedHandler.run()
    }

    fun stopSearch() {
        invokeAndWaitIfNeeded { progressIndicator?.cancel() }
    }
}