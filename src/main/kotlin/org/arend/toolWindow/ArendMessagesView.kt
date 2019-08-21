package org.arend.toolWindow

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.error.GeneralError
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.typechecking.error.ErrorService
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

    private fun createTree(): Tree {
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
        splitter.firstComponent = JBScrollPane(createTree())
        splitter.secondComponent = JPanel()

        toolWindow.icon = ArendIcons.MESSAGES
        toolWindow.contentManager.addContent(ContentFactory.SERVICE.getInstance().createContent(splitter, "", false))

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