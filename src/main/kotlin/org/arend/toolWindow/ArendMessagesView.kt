package org.arend.toolWindow

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.layout.panel
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.editor.ArendOptions
import org.arend.error.GeneralError
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.typechecking.error.ErrorService
import java.awt.Component
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


class ArendMessagesView(private val project: Project) {
    companion object {
        fun getInstance(project: Project) = ServiceManager.getService(project, ArendMessagesView::class.java)!!
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

        val actionManager = CommonActionsManager.getInstance()
        val arendOption = ArendOptions.instance

        val actionGroup = DefaultActionGroup()
        val treeExpander = DefaultTreeExpander(tree)
        actionGroup.add(actionManager.createExpandAllAction(treeExpander, tree))
        actionGroup.add(actionManager.createCollapseAllAction(treeExpander, tree))
        actionGroup.addSeparator()
        val autoScrollToSource = object : AutoScrollToSourceHandler() {
            override fun isAutoScrollMode() = arendOption.autoScrollToSource

            override fun setAutoScrollMode(state: Boolean) {
                arendOption.autoScrollToSource = state
            }

            override fun scrollToSource(component: Component?) {
                tree.navigate(false)
            }
        }
        autoScrollToSource.install(tree)
        actionGroup.add(autoScrollToSource.createToggleAction())

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendMessagesView.toolbar", actionGroup, true)
        toolbar.setTargetComponent(splitter)

        splitter.firstComponent = JBScrollPane(panel {
            row { toolbar.component() }
            row { tree() }
        })
        splitter.secondComponent = JPanel()

        this.toolWindow = toolWindow
        return splitter
    }

    fun update() {
        val tree = tree ?: return
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)

        val errorsMap = ErrorService.getInstance(project).errors
        val map = HashMap<ArendDefinition, HashSet<GeneralError>>()
        tree.update(root ?: return) {
            if (it == root) errorsMap.keys
            else when (val obj = it.userObject) {
                is ArendFile -> {
                    val arendErrors = errorsMap[obj]
                    val children = HashSet<Any>()
                    for (arendError in arendErrors ?: emptyList()) {
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