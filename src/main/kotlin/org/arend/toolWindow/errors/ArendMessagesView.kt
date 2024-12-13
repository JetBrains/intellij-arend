package org.arend.toolWindow.errors

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.PsiElement
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.ext.error.GeneralError
import org.arend.ext.error.MissingClausesError
import org.arend.injection.InjectedArendEditor
import org.arend.psi.ArendFile
import org.arend.psi.arc.ArcFile
import org.arend.psi.ext.ArendGoal
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.toolWindow.errors.tree.*
import org.arend.typechecking.error.ArendError
import org.arend.typechecking.error.ErrorService
import org.arend.util.ArendBundle
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArendMessagesView(private val project: Project, toolWindow: ToolWindow) : ArendErrorTreeListener,
    TreeSelectionListener, ProjectManagerListener {
    private val root = DefaultMutableTreeNode("Errors")
    private val treeModel = DefaultTreeModel(root)
    val tree = ArendErrorTree(treeModel, this)
    private val autoScrollFromSource = ArendErrorTreeAutoScrollFromSource(project, tree)

    private val treePanel = SimpleToolWindowPanel(false)
    private val treeDetailsSplitter = OnePixelSplitter()
    private val goalsErrorsSplitter = OnePixelSplitter(!toolWindow.anchor.isHorizontal, 0.5f)

    private var goalEditor: ArendMessagesViewEditor? = null
    private val goalEmptyPanel =
        JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.goal.panel.text"))
    private val goalsPanel = JBUI.Panels.simplePanel(goalEmptyPanel)
    private val defaultGoalsTabTitle = ArendBundle.message("arend.messages.view.latest.goal.title")
    private val goalsTabInfo = TabInfo(goalsPanel).apply {
        setText(defaultGoalsTabTitle)
        setTooltipText(ArendBundle.message("arend.messages.view.latest.goal.tooltip"))
    }

    private var errorEditor: ArendMessagesViewEditor? = null
    private val errorEmptyPanel =
        JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.error.panel.text"))
    private val errorsPanel = JBUI.Panels.simplePanel(errorEmptyPanel)

    init {
        ProjectManager.getInstance().addProjectManagerListener(project, this)

        toolWindow.setIcon(ArendIcons.MESSAGES)
        val contentManager = toolWindow.contentManager
        contentManager.addContent(contentManager.factory.createContent(treeDetailsSplitter, "", false))
        project.messageBus.connect(project)
            .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    updateOrientation(toolWindow)
                }
            })

        setupTreeDetailsSplitter(!toolWindow.anchor.isHorizontal)

        tree.cellRenderer = ArendErrorTreeCellRenderer(project)
        tree.addTreeSelectionListener(this)

        val actionGroup = DefaultActionGroup()
        val actionManager = CommonActionsManager.getInstance()
        val treeExpander = DefaultTreeExpander(tree)
        actionGroup.add(actionManager.createExpandAllAction(treeExpander, tree))
        actionGroup.add(actionManager.createCollapseAllAction(treeExpander, tree))
        actionGroup.addSeparator()

        actionGroup.add(ArendErrorTreeAutoScrollToSource(project, tree).createToggleAction())
        actionGroup.add(autoScrollFromSource.createActionGroup())
        actionGroup.addSeparator()
        actionGroup.add(ArendMessagesFilterActionGroup(project, autoScrollFromSource))
        actionGroup.addSeparator()
        actionGroup.add(ArendShowErrorsPanelAction())

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendMessagesView.toolbar", actionGroup, false)
        toolbar.targetComponent = treePanel
        treePanel.toolbar = toolbar.component
        treePanel.setContent(ScrollPaneFactory.createScrollPane(tree, true))

        goalsErrorsSplitter.apply {
            firstComponent = SingleHeightTabs(project, toolWindow.disposable).apply {
                addTab(goalsTabInfo)
            }
            secondComponent = SingleHeightTabs(project, toolWindow.disposable).apply {
                addTab(TabInfo(errorsPanel).apply {
                    setText(ArendBundle.message("arend.messages.view.error.title"))
                    setTooltipText(ArendBundle.message("arend.messages.view.error.tooltip"))
                })
            }
            val isShowErrorsPanel = project.service<ArendMessagesService>().isShowErrorsPanel
            secondComponent.isVisible = isShowErrorsPanel.get()
            isShowErrorsPanel.afterSet {
                secondComponent.isVisible = true
                updateEditors()
            }
            isShowErrorsPanel.afterReset { secondComponent.isVisible = false }
        }

        project.service<ArendMessagesService>().isShowImplicitGoals.afterChange { updateEditors() }
        project.service<ArendMessagesService>().isShowGoalsInErrorsPanel.afterChange { updateEditors() }

        update()
    }

    private fun setupTreeDetailsSplitter(vertical: Boolean) {
        treeDetailsSplitter.orientation = vertical
        treeDetailsSplitter.proportion = if (vertical) 0.75f else 0.25f
        // We first set components to null to clear the splitter. Without that, it might not be repainted properly.
        treeDetailsSplitter.firstComponent = null
        treeDetailsSplitter.secondComponent = null
        treeDetailsSplitter.firstComponent = if (vertical) goalsErrorsSplitter else treePanel
        treeDetailsSplitter.secondComponent = if (vertical) treePanel else goalsErrorsSplitter
    }

    private fun updateOrientation(toolWindow: ToolWindow) {
        if (toolWindow.id == ArendMessagesFactory.TOOL_WINDOW_ID) {
            val isVertical = !toolWindow.anchor.isHorizontal
            if (treeDetailsSplitter.isVertical != isVertical) {
                goalsErrorsSplitter.orientation = isVertical
                setupTreeDetailsSplitter(isVertical)
            }
        }
    }

    override fun projectClosing(project: Project) {
        if (project == this.project) {
            root.removeAllChildren()
            goalsPanel.removeAll()
            errorsPanel.removeAll()
            goalEditor?.release()
            goalEditor = null
            errorEditor?.release()
            errorEditor = null
        }
    }

    override fun valueChanged(e: TreeSelectionEvent?) = updateEditors()

    fun updateEditors() {
        val treeElement = getSelectedMessage()
        if (treeElement != null) {
            if (isGoal(treeElement) && !isGoalTextPinned()) {
                if (goalEditor == null) {
                    goalEditor = ArendMessagesViewEditor(project, treeElement, true)
                }
                if (!isImplicitGoal(treeElement) || isShowImplicitGoals()) {
                    updateEditor(goalEditor!!, treeElement)
                } else {
                    removeNotActionToolbars(goalEditor!!)
                }
                updateActionGroup(goalEditor!!)
                updateGoalsView(goalEditor?.component ?: goalEmptyPanel)
            }
            if (isShowErrorsPanel() && !isErrorTextPinned() && (!isGoal(treeElement) || isShowGoalsInErrorsPanel())) {
                if (errorEditor == null) {
                    errorEditor = ArendMessagesViewEditor(project, treeElement, false)
                }
                updateEditor(errorEditor!!, treeElement)
                updatePanel(errorsPanel, errorEditor?.component ?: errorEmptyPanel)
            }
            if (errorEditor?.isEmptyActionGroup() == true) {
                errorEditor?.setupActions()
            }
        } else {
            ApplicationManager.getApplication().executeOnPooledThread {
                runReadAction {
                    if (!isGoalTextPinned()) {
                        goalEditor?.treeElement?.sampleError?.let { currentGoal ->
                            if (isParentDefinitionPsiInvalid(currentGoal)) {
                                goalEditor?.clear()
                                updateGoalsView(goalEmptyPanel)
                            } else if (currentGoal.file?.isBackgroundTypecheckingFinished == true) {
                                val error = currentGoal.error
                                val (resolve, scope) = InjectedArendEditor.resolveCauseReference(error)
                                val doc = goalEditor?.treeElement?.let { goalEditor?.getDoc(it, error, resolve, scope) }

                                if (isParentDefinitionRemovedFromTree(currentGoal)) {
                                    goalEditor?.clear()
                                    updateGoalsView(goalEmptyPanel)
                                } else if (isCausePsiInvalid(currentGoal) && goalsTabInfo.text == defaultGoalsTabTitle) {
                                    runInEdt {
                                        goalsTabInfo.append(
                                            " (${ArendBundle.message("arend.messages.view.latest.goal.removed.title")})",
                                            SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                        )
                                    }
                                } else if (goalEditor?.currentDoc.toString() != doc.toString()) {
                                    updateGoalText()
                                }
                            }
                        }
                    }
                    if (isShowErrorsPanel() && !isErrorTextPinned()) {
                        val currentError = errorEditor?.treeElement?.sampleError
                        if (currentError != null && isParentDefinitionRemovedFromTree(currentError)) {
                            errorEditor?.clear()
                            updatePanel(errorsPanel, errorEmptyPanel)
                        }
                    }
                }
            }
        }
    }

    private fun getSelectedMessage(): ArendErrorTreeElement? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ArendErrorTreeElement

    private fun isImplicitGoal(treeElement: ArendErrorTreeElement?): Boolean {
        val error = treeElement?.highestError?.error ?: return false
        return error.level == GeneralError.Level.GOAL && error.cause !is ArendGoal
    }

    private fun isGoal(treeElement: ArendErrorTreeElement?) =
        treeElement?.highestError?.error?.level == GeneralError.Level.GOAL

    private fun isShowImplicitGoals() = project.service<ArendMessagesService>().isShowImplicitGoals.get()

    private fun isGoalTextPinned() = project.service<ArendMessagesService>().isGoalTextPinned

    private fun isErrorTextPinned() = project.service<ArendMessagesService>().isErrorTextPinned

    private fun isShowErrorsPanel() = project.service<ArendMessagesService>().isShowErrorsPanel.get()

    private fun isShowGoalsInErrorsPanel() = project.service<ArendMessagesService>().isShowGoalsInErrorsPanel.get()

    private fun updateEditor(editor: ArendMessagesViewEditor, treeElement: ArendErrorTreeElement) {
        if (editor.treeElement != treeElement) {
            for (arendError in treeElement.errors) {
                configureError(arendError.error)
            }
            editor.update(treeElement)
        }
        editor.addEditorComponent()
    }

    private fun updateActionGroup(editor: ArendMessagesViewEditor) {
        editor.updateActionGroup()
    }

    private fun removeNotActionToolbars(editor: ArendMessagesViewEditor) {
        val components = editor.component?.components
        val notActionToolbars = components?.filter { it !is ActionToolbar } ?: listOf()
        editor.removeUnnecessaryComponents(notActionToolbars)
    }

    private fun isParentDefinitionPsiInvalid(error: ArendError): Boolean {
        val definition = error.definition
        return definition == null || !definition.isValid
    }

    private fun isParentDefinitionRemovedFromTree(error: ArendError): Boolean {
        val definition = error.definition
        return definition == null || !tree.containsNode(definition)
    }

    private fun isCausePsiInvalid(error: ArendError): Boolean {
        val cause = error.cause
        return cause == null || !cause.isValid
    }

    private fun updatePanel(panel: JPanel, component: JComponent) {
        runInEdt {
            panel.removeAll()
            panel.add(component)
            panel.revalidate()
            panel.repaint()
        }
    }

    private fun updateGoalsView(component: JComponent) {
        updatePanel(goalsPanel, component)
        runInEdt { goalsTabInfo.setText(defaultGoalsTabTitle) }
    }

    fun updateGoalText() {
        goalEditor?.updateErrorText()
        if (isGoal(errorEditor?.treeElement)) {
            errorEditor?.updateErrorText()
        }
    }

    fun updateErrorText() {
        val level = errorEditor?.treeElement?.highestError?.error?.level
        if (level != null && level != GeneralError.Level.GOAL) {
            errorEditor?.updateErrorText()
        }
    }

    fun clearGoalEditor() {
        if (goalEditor?.treeElement == getSelectedMessage()) {
            tree.clearSelection()
        }
        goalEditor?.clear()
        updateGoalsView(goalEmptyPanel)
    }

    private fun configureError(error: GeneralError) {
        if (error is MissingClausesError) {
            error.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        }
    }

    override fun errorRemoved(arendError: ArendError) {
        if (autoScrollFromSource.isAutoScrollEnabled) {
            autoScrollFromSource.updateCurrentSelection()
        }
    }

    private fun getArendFilesWithErrors(filterSet: EnumSet<MessageType>, errorsMap: Map<ArendFile, List<ArendError>>): List<ArendFile> {
        return errorsMap.entries.filter { entry -> entry.value.any { it.error.satisfies(filterSet) } }
            .map { it.key }
    }

    fun update() {
        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
        val errorsMap = project.service<ErrorService>().errors

        ApplicationManager.getApplication().run {
            executeOnPooledThread {
                val arendFilesWithErrors = getArendFilesWithErrors(filterSet, errorsMap).onEach {
                    it.arendLibrary
                    it.moduleLocation
                }
                val arcFiles = arendFilesWithErrors.filterIsInstance<ArcFile>()
                    .groupBy { it.fullName }.values.map { it.maxBy { file -> file.arcTimestamp } }
                runInEdt {
                    val expandedPaths = TreeUtil.collectExpandedPaths(tree)
                    val selectedPath = tree.selectionPath

                    val map = HashMap<PsiConcreteReferable, HashMap<PsiElement?, ArendErrorTreeElement>>()
                    tree.update(root) { node ->
                        if (node == root) {
                            arendFilesWithErrors.filter { it !is ArcFile } + arcFiles
                        }
                        else when (val obj = node.userObject) {
                            is ArendFile -> {
                                val arendErrors = errorsMap[obj]
                                val children = LinkedHashSet<Any>()
                                for (arendError in arendErrors ?: emptyList()) {
                                    if (!arendError.error.satisfies(filterSet)) {
                                        continue
                                    }

                                    var def: PsiConcreteReferable? = null
                                    executeOnPooledThread {
                                        runReadAction {
                                            def = arendError.definition
                                        }
                                    }.get()
                                    if (def == null) {
                                        children.add(ArendErrorTreeElement(arendError))
                                    } else {
                                        children.add(def!!)
                                        map.computeIfAbsent(def!!) { LinkedHashMap() }
                                            .computeIfAbsent(arendError.cause) { ArendErrorTreeElement() }.add(arendError)
                                    }
                                }
                                children
                            }
                            is PsiConcreteReferable -> map[obj]?.values ?: emptySet()
                            else -> emptySet()
                        }
                    }
                    treeModel.reload()
                    TreeUtil.restoreExpandedPaths(tree, expandedPaths)
                    tree.selectionPath = tree.getExistingPrefix(selectedPath)
                }
            }
        }
    }
}
