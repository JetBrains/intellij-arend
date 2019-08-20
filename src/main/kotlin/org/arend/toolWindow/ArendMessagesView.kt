package org.arend.toolWindow

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.JBSplitter
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import org.arend.ArendIcons
import org.arend.psi.ArendDefinition
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
    var toolWindow: ToolWindow? = null

    private fun createTree(): Tree {
        val root = DefaultMutableTreeNode("Errors")
        val treeModel = DefaultTreeModel(root)
        this.treeModel = treeModel
        this.root = root
        val tree = ArendErrorTree(treeModel)
        tree.cellRenderer = ArendErrorTreeCellRenderer(tree)
        return tree
    }

    fun initView(toolWindow: ToolWindow): JBSplitter {
        val splitter = JBSplitter(false, 0.25f)
        splitter.firstComponent = createTree()
        splitter.secondComponent = JPanel()

        toolWindow.icon = ArendIcons.MESSAGES
        toolWindow.contentManager.addContent(ContentFactory.SERVICE.getInstance().createContent(splitter, "", false))

        this.toolWindow = toolWindow

        return splitter
    }

    fun update() {
        val root = root ?: return
        root.removeAllChildren()
        val entries = ErrorService.getInstance(project).errors
        for (entry in entries) {
            val fileNode = DefaultMutableTreeNode(entry.key.modulePath)
            root.add(fileNode)
            val map = HashMap<ArendDefinition, DefaultMutableTreeNode>()
            for (arendError in entry.value) {
                val errorNode = DefaultMutableTreeNode(arendError.error)
                val definition = arendError.definition
                if (definition != null) {
                    map.computeIfAbsent(definition) {
                        val node = DefaultMutableTreeNode(definition)
                        fileNode.add(node)
                        node
                    }.add(errorNode)
                } else {
                    fileNode.add(errorNode)
                }
            }
        }
        treeModel?.reload()
    }
}