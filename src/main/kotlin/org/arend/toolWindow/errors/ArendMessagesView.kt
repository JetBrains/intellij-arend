package org.arend.toolWindow.errors

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.panel
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.error.GeneralError
import org.arend.injection.InjectedArendEditor
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.toolWindow.errors.tree.*
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.MissingClausesError
import javax.swing.JPanel
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArendMessagesView(private val project: Project, toolWindow: ToolWindow) : ArendErrorTreeListener, TreeSelectionListener, ProjectManagerListener {
    private val root = DefaultMutableTreeNode("Errors")
    private val treeModel = DefaultTreeModel(root)
    val tree = ArendErrorTree(treeModel, this)
    private val autoScrollFromSource = ArendErrorTreeAutoScrollFromSource(project, tree)

    private val splitter = JBSplitter(false, 0.25f)
    private val emptyPanel = JPanel()
    private var activeEditor: InjectedArendEditor? = null

    private val errorEditors = HashMap<GeneralError, InjectedArendEditor>()

    init {
        ProjectManager.getInstance().addProjectManagerListener(project, this)

        toolWindow.icon = ArendIcons.MESSAGES
        toolWindow.contentManager.addContent(ContentFactory.SERVICE.getInstance().createContent(splitter, "", false))

        tree.cellRenderer = ArendErrorTreeCellRenderer(tree)
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
        actionGroup.add(ArendPrintOptionsActionGroup(project, PrintOptionKind.ERROR_PRINT_OPTIONS))
        actionGroup.add(ArendPrintOptionsActionGroup(project, PrintOptionKind.GOAL_PRINT_OPTIONS))

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendMessagesView.toolbar", actionGroup, true)
        toolbar.setTargetComponent(splitter)

        splitter.firstComponent = panel {
            row { toolbar.component() }
            row { JBScrollPane(tree)() }
        }
        splitter.secondComponent = emptyPanel

        update()
    }

    override fun projectClosing(project: Project) {
        if (project == this.project) {
            root.removeAllChildren()
            for (arendEditor in errorEditors.values) {
                arendEditor.release()
                if (arendEditor == activeEditor) {
                    activeEditor = null
                }
            }
            errorEditors.clear()

            splitter.secondComponent = emptyPanel
            activeEditor?.release()
            activeEditor = null
        }
    }

    override fun valueChanged(e: TreeSelectionEvent?) {
        ((tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? GeneralError)?.let { error ->
            val arendEditor = errorEditors.computeIfAbsent(error) {
                configureError(error)
                InjectedArendEditor(project, error)
            }

            if (activeEditor?.error?.let { errorEditors.containsKey(it) } == false) {
                activeEditor?.release()
            }

            activeEditor = arendEditor
            splitter.secondComponent = arendEditor.component ?: emptyPanel
        }
    }

    private fun configureError(error: GeneralError) {
        if (error is MissingClausesError) {
            error.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        }
    }

    override fun errorRemoved(error: GeneralError) {
        val removed = errorEditors.remove(error) ?: return
        if (removed != activeEditor) {
            removed.release()
        }

        if (autoScrollFromSource.isAutoScrollEnabled) {
            autoScrollFromSource.updateCurrentSelection()
        }
    }

    fun update() {
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
        val selectedPath = tree.selectionPath

        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
        val errorsMap = project.service<ErrorService>().errors
        val map = HashMap<ArendDefinition, HashSet<GeneralError>>()
        tree.update(root) {
            if (it == root) errorsMap.keys
            else when (val obj = it.userObject) {
                is ArendFile -> {
                    val arendErrors = errorsMap[obj]
                    val children = LinkedHashSet<Any>()
                    for (arendError in arendErrors ?: emptyList()) {
                        if (!arendError.error.satisfies(filterSet)) {
                            continue
                        }

                        val def = arendError.definition
                        if (def == null) {
                            children.add(arendError.error)
                        } else {
                            children.add(def)
                            map.computeIfAbsent(def) { LinkedHashSet() }.add(arendError.error)
                        }
                    }
                    children
                }
                is ArendDefinition -> map[obj] ?: emptySet()
                else -> emptySet()
            }
        }

        treeModel.reload()
        TreeUtil.restoreExpandedPaths(tree, expandedPaths)
        tree.selectionPath = tree.getExistingPrefix(selectedPath)
        activeEditor?.updateErrorText()
    }
}