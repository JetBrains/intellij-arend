package org.arend.toolWindow.errors

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.psi.PsiElement
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.ext.error.GeneralError
import org.arend.ext.error.MissingClausesError
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.toolWindow.errors.tree.*
import org.arend.typechecking.error.ArendError
import org.arend.typechecking.error.ErrorService
import org.arend.util.ArendBundle
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArendMessagesView(private val project: Project, toolWindow: ToolWindow) : ArendErrorTreeListener, TreeSelectionListener, ProjectManagerListener {
    private val root = DefaultMutableTreeNode("Errors")
    private val treeModel = DefaultTreeModel(root)
    val tree = ArendErrorTree(treeModel, this)
    private val autoScrollFromSource = ArendErrorTreeAutoScrollFromSource(project, tree)

    private val toolWindowPanel = SimpleToolWindowPanel(false)
    private val treeDetailsSplitter = OnePixelSplitter(false, 0.25f)
    private val goalsAllMessagesSplitter = OnePixelSplitter(false, 0.5f)
    private var goalEditor: ArendMessagesViewEditor? = null
    private val goalEmptyPanel = JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.goal.panel.text"))
    private var allMessagesEditor: ArendMessagesViewEditor? = null
    private val allMessagesEmptyPanel = JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.all.messages.panel.text"))

    init {
        ProjectManager.getInstance().addProjectManagerListener(project, this)

        toolWindow.setIcon(ArendIcons.MESSAGES)
        val contentManager = toolWindow.contentManager
        contentManager.addContent(contentManager.factory.createContent(toolWindowPanel, "", false))

        tree.cellRenderer = ArendErrorTreeCellRenderer()
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
        actionGroup.add(ArendShowAllMessagesPanelAction())

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendMessagesView.toolbar", actionGroup, false)
        toolbar.setTargetComponent(toolWindowPanel)

        toolWindowPanel.toolbar = toolbar.component
        toolWindowPanel.setContent(treeDetailsSplitter)
        treeDetailsSplitter.firstComponent = ScrollPaneFactory.createScrollPane(tree, true)
        treeDetailsSplitter.secondComponent = goalsAllMessagesSplitter

        goalsAllMessagesSplitter.apply {
            firstComponent = goalEmptyPanel
            secondComponent = allMessagesEmptyPanel
            val isShowAllMessagesPanel = project.service<ArendMessagesService>().isShowAllMessagesPanel
            secondComponent.isVisible = isShowAllMessagesPanel.get()
            isShowAllMessagesPanel.afterSet {
                secondComponent.isVisible = true
                updateEditors()
            }
            isShowAllMessagesPanel.afterReset { secondComponent.isVisible = false }
        }

        update()
    }

    override fun projectClosing(project: Project) {
        if (project == this.project) {
            root.removeAllChildren()
            goalsAllMessagesSplitter.firstComponent = goalEmptyPanel
            goalsAllMessagesSplitter.secondComponent = allMessagesEmptyPanel
            goalEditor?.release()
            goalEditor = null
            allMessagesEditor?.release()
            allMessagesEditor = null
        }
    }

    override fun valueChanged(e: TreeSelectionEvent?) = updateEditors()

    fun updateEditors() {
        val treeElement = getSelectedMessage()
        if (treeElement != null) {
            if (treeElement.highestError.error.level == GeneralError.Level.GOAL && !isGoalTextPinned()) {
                if (goalEditor == null) {
                    goalEditor = ArendMessagesViewEditor(project, treeElement, true)
                }
                updateEditor(goalEditor!!, treeElement)
                goalsAllMessagesSplitter.firstComponent = goalEditor?.component ?: goalEmptyPanel
            }
            if (isShowAllMessagesPanel()) {
                if (allMessagesEditor == null) {
                    allMessagesEditor = ArendMessagesViewEditor(project, treeElement, false)
                }
                updateEditor(allMessagesEditor!!, treeElement)
                goalsAllMessagesSplitter.secondComponent = allMessagesEditor?.component ?: allMessagesEmptyPanel
            }
        } else {
            if (goalEditor?.treeElement != null && !isGoalTextPinned() && shouldClear(goalEditor)) {
                goalEditor?.treeElement = null
                val outdatedMessage = "\n[${ArendBundle.message("arend.messages.view.outdated.goal")}]"
                goalEditor?.addDoc(DocFactory.text(outdatedMessage), null, false)
            }
            if (isShowAllMessagesPanel() && shouldClear(allMessagesEditor)) {
                allMessagesEditor?.clear()
                goalsAllMessagesSplitter.secondComponent = allMessagesEmptyPanel
            }
        }
    }

    private fun getSelectedMessage(): ArendErrorTreeElement? =
            tree.lastSelectedPathComponent.castSafelyTo<DefaultMutableTreeNode>()
                    ?.userObject?.castSafelyTo<ArendErrorTreeElement>()

    private fun isGoalTextPinned() = project.service<ArendMessagesService>().isGoalTextPinned

    private fun isShowAllMessagesPanel() = project.service<ArendMessagesService>().isShowAllMessagesPanel.get()

    private fun updateEditor(editor: ArendMessagesViewEditor, treeElement: ArendErrorTreeElement) {
        if (editor.treeElement != treeElement) {
            for (arendError in treeElement.errors) {
                configureError(arendError.error)
            }
            editor.update(treeElement)
        }
    }

    private fun shouldClear(editor: ArendMessagesViewEditor?): Boolean {
        val activeError = editor?.treeElement?.sampleError
        if (activeError != null) {
            val def = activeError.definition
            if (def == null || !tree.containsNode(def)) {
                return true
            }
        }
        return false
    }

    fun updateGoalText() {
        goalEditor?.updateErrorText()
        if (allMessagesEditor?.treeElement?.highestError?.error?.level == GeneralError.Level.GOAL) {
            allMessagesEditor?.updateErrorText()
        }
    }

    fun updateErrorText() {
        val level = allMessagesEditor?.treeElement?.highestError?.error?.level
        if (level != null &&  level != GeneralError.Level.GOAL) {
            allMessagesEditor?.updateErrorText()
        }
    }

    fun clearGoalEditor() {
        if (goalEditor?.treeElement == getSelectedMessage()) {
            tree.clearSelection()
        }
        goalEditor?.clear()
        goalsAllMessagesSplitter.firstComponent = goalEmptyPanel
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

    fun update() {
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
        val selectedPath = tree.selectionPath

        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
        val errorsMap = project.service<ErrorService>().errors
        val map = HashMap<PsiConcreteReferable, HashMap<PsiElement?, ArendErrorTreeElement>>()
        tree.update(root) { node ->
            if (node == root) errorsMap.entries.filter { entry -> entry.value.any { it.error.satisfies(filterSet) } }.map { it.key }
            else when (val obj = node.userObject) {
                is ArendFile -> {
                    val arendErrors = errorsMap[obj]
                    val children = LinkedHashSet<Any>()
                    for (arendError in arendErrors ?: emptyList()) {
                        if (!arendError.error.satisfies(filterSet)) {
                            continue
                        }

                        val def = arendError.definition
                        if (def == null) {
                            children.add(ArendErrorTreeElement(arendError))
                        } else {
                            children.add(def)
                            map.computeIfAbsent(def) { LinkedHashMap() }.computeIfAbsent(arendError.cause) { ArendErrorTreeElement() }.add(arendError)
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