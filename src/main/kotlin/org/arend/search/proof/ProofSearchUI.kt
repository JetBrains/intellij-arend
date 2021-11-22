package org.arend.search.proof

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.ide.actions.BigPopupUI
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.BooleanFunction
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.remove
import org.arend.ArendIcons
import org.arend.psi.navigate
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.DocumentEvent

class ProofSearchUI(private val project : Project?) : BigPopupUI(project) {

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
        project?.let { actionGroup.addAction(ShowInFindWindowAction(this, it)) }
        val toolbar = ActionManager.getInstance().createActionToolbar("proof.search.top.toolbar", actionGroup, true)
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        toolbar.setTargetComponent(this)
        val toolbarComponent = toolbar.component
        toolbarComponent.isOpaque = false
        res.add(toolbarComponent)
        return res
    }

    override fun getAccessibleName(): String = "Proof search"

    private fun initSearchActions() {
        searchField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                scheduleSearch()
            }
        })
        myResultsList.expandableItemsHandler.isEnabled = false
        val escape = ActionManager.getInstance().getAction("EditorEscape")
        DumbAwareAction.create { close() }
            .registerCustomShortcutSet(escape?.shortcutSet ?: CommonShortcuts.ESCAPE, this)
        adjustEmptyText(
            mySearchField,
            { field: JBTextField -> field.text.isEmpty() },
            "Pattern string",
            ""
        )

        DumbAwareAction.create { event: AnActionEvent? ->
            // todo: also navigate by clicking on an entry
            val indices = myResultsList.selectedIndices
            val first = model.getElementAt(indices[0])
            if (first is FoundItemDescriptor<*>) {
                val navigatable = first.item.castSafelyTo<ProofSearchEntry>()!!.def.navigationElement
                close()
                navigatable.navigate(true)
            } else if (first is MoreElement) {
                model.remove(first)
                runProofSearch(first.sequence)
            }
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, this, this)
    }

    fun loooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooong (a : Int) : Int { return 1 }

    private fun scheduleSearch() {
        if (!searchAlarm.isDisposed && searchAlarm.activeRequestCount == 0) {
            searchAlarm.addRequest({ runProofSearch(null) }, 100)
        }
    }

    private fun runProofSearch(results : Sequence<FoundItemDescriptor<ProofSearchEntry>>?) {
        val project = myProject ?: return
        progressIndicator?.cancel()
        if (results == null) {
            invokeLater {
                model.removeAll()
            }
        }
        runBackgroundableTask("Proof Search", myProject) { progressIndicator ->
            this.progressIndicator = progressIndicator
            val elements = results ?: fetchWeightedElements(project, searchPattern)
            var counter = 20
            for (element in elements) {
                if (progressIndicator.isCanceled) {
                    break
                }
                --counter
                invokeLater {
                    model.add(element)
                }
                if (counter == 0) {
                    invokeLater {
                        model.add(MoreElement(elements.drop(20)))
                    }
                    break
                }
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

    private fun stopSearch() {
        progressIndicator?.cancel()
    }

    private fun adjustEmptyText(
        textEditor: JBTextField,
        function: BooleanFunction<in JBTextField>,
        leftText: @NlsContexts.StatusText String,
        rightText: @NlsContexts.StatusText String
    ) {
        textEditor.putClientProperty("StatusVisibleFunction", function)
        val statusText = textEditor.emptyText
        statusText.isShowAboveCenter = false
        statusText.setText(leftText, SimpleTextAttributes.GRAY_ATTRIBUTES)
        statusText.appendText(false, 0, rightText, SimpleTextAttributes.GRAY_ATTRIBUTES, null)
        statusText.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL))
    }


}