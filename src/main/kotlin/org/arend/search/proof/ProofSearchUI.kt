package org.arend.search.proof

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.ide.actions.BigPopupUI
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.arend.ArendIcons
import org.arend.psi.navigate
import org.arend.util.ArendBundle
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.DocumentEvent

class ProofSearchUI(private val project: Project) : BigPopupUI(project) {

    private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val model: CollectionListModel<ProofSearchUIEntry> = CollectionListModel()

    private val loadingIcon: JBLabel = JBLabel(EmptyIcon.ICON_16)

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
        initActions()
    }

    override fun dispose() {
        close()
        model.removeAll()
    }

    override fun createList(): JBList<Any> {
        @Suppress("UNCHECKED_CAST")
        addListDataListener(model as AbstractListModel<Any>)

        return JBList(model)
    }

    override fun createCellRenderer(): ListCellRenderer<Any> {
        @Suppress("UNCHECKED_CAST")
        return ArendProofSearchRenderer() as ListCellRenderer<Any>
    }

    override fun createTopLeftPanel(): JPanel {
        @Suppress("DialogTitleCapitalization")
        val title = JBLabel(ArendBundle.message("arend.proof.search.title"))
        val topPanel = JPanel(MigLayout("flowx, ins 0, gap 0, fillx, filly"))
        val foregroundColor = when {
            StartupUiUtil.isUnderDarcula() -> when {
                UIUtil.isUnderWin10LookAndFeel() -> JBColor.WHITE
                else -> JBColor(Gray._240, Gray._200)
            }
            else -> UIUtil.getLabelForeground()
        }

        title.foreground = foregroundColor
        title.border = BorderFactory.createEmptyBorder(3, 5, 5, 0)
        if (SystemInfo.isMac) {
            title.font = loadingIcon.font.deriveFont(Font.BOLD, title.font.size - 1f)
        } else {
            title.font = loadingIcon.font.deriveFont(Font.BOLD)
        }
        topPanel.add(title, "gapright 4")
        topPanel.add(loadingIcon, "w 24, wmin 24")
        return topPanel
    }

    override fun createSettingsPanel(): JPanel {
        val res = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        res.isOpaque = false

        val actionGroup = DefaultActionGroup()
        actionGroup.addAction(GearActionGroup(this, project))
        actionGroup.addAction(ShowInFindWindowAction(this, project))
        val toolbar = ActionManager.getInstance().createActionToolbar("proof.search.top.toolbar", actionGroup, true)
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        toolbar.setTargetComponent(this)
        val toolbarComponent = toolbar.component
        toolbarComponent.isOpaque = false
        res.add(toolbarComponent)
        return res
    }

    override fun getAccessibleName(): String = ArendBundle.message("arend.proof.search.title")

    private fun initActions() {
        myResultsList.expandableItemsHandler.isEnabled = false

        registerSearchAction()
        registerEscapeAction()
        registerEnterAction()
        registerMouseActions()

        adjustEmptyText(mySearchField)
    }

    private fun registerSearchAction() {
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = scheduleSearch()
        })
    }

    private fun registerMouseActions() {
        val mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onMouseClicked(e)
            }
        }
        myResultsList.addMouseListener(mouseListener)
        myResultsList.addMouseMotionListener(mouseListener)
    }

    private fun registerEnterAction() {
        DumbAwareAction.create {
            val indices = myResultsList.selectedIndices
            if (indices.isNotEmpty()) {
                onEntrySelected(model.getElementAt(indices[0]))
            }
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, this, this)
    }

    private fun registerEscapeAction() {
        val escapeAction = ActionManager.getInstance().getAction("EditorEscape")
        DumbAwareAction
            .create { close() }
            .registerCustomShortcutSet(escapeAction?.shortcutSet ?: CommonShortcuts.ESCAPE, this)
    }

    private fun scheduleSearch() {
        if (!searchAlarm.isDisposed && searchAlarm.activeRequestCount == 0) {
            searchAlarm.addRequest({ runProofSearch(null) }, 100)
        }
    }

    private fun onMouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1) {
            e.consume()
            val i = myResultsList.locationToIndex(e.point)
            if (i > -1) {
                myResultsList.selectedIndex = i
                onEntrySelected(model.getElementAt(i))
            }
        }
    }

    private fun onEntrySelected(element: ProofSearchUIEntry) = when (element) {
        is DefElement -> {
            val navigationElement = element.entry.def.navigationElement
            close()
            navigationElement.navigate(true)
        }
        is MoreElement -> {
            model.remove(element)
            runProofSearch(element.sequence)
        }
    }

    fun runProofSearch(results: Sequence<ProofSearchEntry>?) {
        cleanupCurrentResults(results)

        val settings = ProofSearchUISettings(project)

        runBackgroundableTask(ArendBundle.message("arend.proof.search.title"), myProject) { progressIndicator ->
            runWithLoadingIcon {
                this.progressIndicator = progressIndicator
                val elements = results ?: generateProofSearchResults(project, searchPattern)
                var counter = PROOF_SEARCH_RESULT_LIMIT
                for (element in elements) {
                    if (progressIndicator.isCanceled) {
                        break
                    }
                    invokeLater {
                        model.add(DefElement(element))
                        if (results != null && counter == PROOF_SEARCH_RESULT_LIMIT && myResultsList.selectedIndex == -1) {
                            myResultsList.selectedIndex = myResultsList.itemsCount - 1
                        }
                    }
                    --counter
                    if (settings.shouldLimitSearch() && counter == 0) {
                        invokeLater {
                            model.add(MoreElement(elements.drop(PROOF_SEARCH_RESULT_LIMIT)))
                        }
                        break
                    }
                }
            }
        }
    }

    private inline fun runWithLoadingIcon(action: () -> Unit) {
        runInEdt {
            loadingIcon.icon = AnimatedIcon.Default.INSTANCE
            loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.loading.tooltip")
        }
        try {
            action()
        } finally {
            runInEdt {
                loadingIcon.setIconWithAlignment(ArendIcons.CHECKMARK, SwingConstants.LEFT, SwingConstants.TOP)
                loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.completed.tooltip")
            }
        }
    }

    private fun cleanupCurrentResults(results: Sequence<ProofSearchEntry>?) {
        progressIndicator?.cancel()
        if (results == null) {
            invokeLater {
                model.removeAll()
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
            override fun getIcon(hovered: Boolean): Icon = ArendIcons.TURNSTILE

            override fun isIconBeforeText(): Boolean = true

            override fun getIconGap(): Int = JBUIScale.scale(10)
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

    private fun stopSearch() {
        progressIndicator?.cancel()
    }

    private fun adjustEmptyText(
        textEditor: JBTextField
    ) {
        textEditor.putClientProperty("StatusVisibleFunction", { field: JBTextField -> field.text.isEmpty() })
        val statusText = textEditor.emptyText
        statusText.isShowAboveCenter = false
        statusText.setText(ArendBundle.message("arend.proof.search.pattern"), SimpleTextAttributes.GRAY_ATTRIBUTES)
        statusText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL))
    }
}

const val PROOF_SEARCH_RESULT_LIMIT = 20