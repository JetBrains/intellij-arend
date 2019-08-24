package org.arend.toolWindow.errors

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.panel
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.error.GeneralError
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.tree.*
import org.arend.typechecking.error.ErrorService
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


class ArendMessagesView(private val project: Project) {
    companion object {
        fun activate(project: Project, action: () -> Unit) {
            ToolWindowManager.getInstance(project).getToolWindow("Arend Errors").activate(action)
        }
    }

    private var root: DefaultMutableTreeNode? = null
    private var treeModel: DefaultTreeModel? = null
    var tree: ArendErrorTree? = null
    var toolWindow: ToolWindow? = null

    private fun createTree(): ArendErrorTree {
        val root = DefaultMutableTreeNode("Errors")
        val treeModel = DefaultTreeModel(root)
        val tree = ArendErrorTree(treeModel)
        tree.cellRenderer = ArendErrorTreeCellRenderer(tree)

        this.treeModel = treeModel
        this.root = root
        this.tree = tree
        return tree
    }

    fun initView(toolWindow: ToolWindow): JBSplitter {
        val splitter = JBSplitter(false, 0.25f)
        val tree = createTree()

        toolWindow.icon = ArendIcons.MESSAGES
        toolWindow.contentManager.addContent(ContentFactory.SERVICE.getInstance().createContent(splitter, "", false))

        val actionGroup = DefaultActionGroup()
        val actionManager = CommonActionsManager.getInstance()
        val treeExpander = DefaultTreeExpander(tree)
        actionGroup.add(actionManager.createExpandAllAction(treeExpander, tree))
        actionGroup.add(actionManager.createCollapseAllAction(treeExpander, tree))
        actionGroup.addSeparator()

        actionGroup.add(ArendErrorTreeAutoScrollToSource(project, tree).createToggleAction())
        actionGroup.add(ArendErrorTreeAutoScrollFromSource(project, tree).createToggleAction())
        actionGroup.addSeparator()
        actionGroup.add(ArendMessagesFilterActionGroup(project))

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendMessagesView.toolbar", actionGroup, true)
        toolbar.setTargetComponent(splitter)

        splitter.firstComponent = panel {
            row { toolbar.component() }
            row { JBScrollPane(tree)() }
        }
        splitter.secondComponent = JPanel()

        this.toolWindow = toolWindow
        update()
        return splitter
    }

    fun update() {
        val tree = tree ?: return
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)

        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet.clone()
        if (filterSet.contains(GeneralError.Level.WARNING)) {
            filterSet.add(GeneralError.Level.WEAK_WARNING)
        }

        val errorsMap = project.service<ErrorService>().errors
        val map = HashMap<ArendDefinition, HashSet<GeneralError>>()
        tree.update(root ?: return) {
            if (it == root) errorsMap.keys
            else when (val obj = it.userObject) {
                is ArendFile -> {
                    val arendErrors = errorsMap[obj]
                    val children = HashSet<Any>()
                    for (arendError in arendErrors ?: emptyList()) {
                        if (!filterSet.contains(arendError.error.level)) {
                            continue
                        }

                        val def = arendError.definition
                        if (def == null) {
                            children.add(arendError.error)
                        } else {
                            children.add(def)
                            map.computeIfAbsent(def) { HashSet() }.add(arendError.error)
                        }
                    }
                    children
                }
                is ArendDefinition -> map[obj] ?: emptySet()
                else -> emptySet()
            }
        }

        treeModel?.reload()
        TreeUtil.restoreExpandedPaths(tree, expandedPaths)
    }
}