package org.arend.toolWindow

import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.arend.error.GeneralError
import org.arend.naming.reference.Referable
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.MutableTreeNode
import javax.swing.tree.TreeSelectionModel


class ArendErrorTree(treeModel: DefaultTreeModel) : Tree(treeModel) {
    init {
        isRootVisible = false
        emptyText.text = "No errors"
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
    }

    override fun convertValueToText(value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): String =
        when (val obj = ((value as? DefaultMutableTreeNode)?.userObject)) {
            is ArendFile -> obj.modulePath?.toString() ?: obj.name
            is GeneralError -> obj.shortMessage
            is Referable -> obj.textRepresentation()
            else -> obj?.toString() ?: ""
        }

    private fun <T : MutableTreeNode> insertNode(child: T, parent: T, comparator: (T, T) -> Int): T {
        val index = TreeUtil.indexedBinarySearch(parent, child, comparator)
        return if (index < 0) {
            (treeModel as DefaultTreeModel).insertNodeInto(child, parent, -(index + 1))
            child
        } else {
            @Suppress("UNCHECKED_CAST")
            parent.getChildAt(index) as T
        }
    }

    fun update(node: DefaultMutableTreeNode, childrenFunc: (DefaultMutableTreeNode) -> Set<Any?>) {
        val children = childrenFunc(node)
        var i = node.childCount - 1
        while (i >= 0) {
            if (!children.contains((node.getChildAt(i) as? DefaultMutableTreeNode)?.userObject)) {
                node.remove(i)
            }
            i--
        }

        for (child in children) {
            val childNode = insertNode(DefaultMutableTreeNode(child), node) { d1, d2 ->
                val obj1 = d1.userObject
                val obj2 = d2.userObject
                when {
                    obj1 is ArendFile && obj2 is ArendFile -> (obj1.modulePath?.toString() ?: obj1.name).compareTo(obj2.modulePath?.toString() ?: obj2.name)
                    obj1 is ArendDefinition && obj2 is ArendDefinition -> obj1.textOffset.compareTo(obj2.textOffset)
                    obj1 is GeneralError && obj2 is GeneralError -> obj1.level.compareTo(obj2.level) * -1
                    obj1 is GeneralError -> 1
                    obj2 is GeneralError -> -1
                    obj1 is ArendFile -> 1
                    obj2 is ArendFile -> -1
                    else -> 0
                }
            }
            update(childNode, childrenFunc)
        }
    }
}