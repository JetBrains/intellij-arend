package org.arend.toolWindow

import com.intellij.ui.treeStructure.Tree
import org.arend.error.GeneralError
import org.arend.naming.reference.Referable
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel


class ArendErrorTree(treeModel: TreeModel) : Tree(treeModel) {
    init {
        isRootVisible = false
        emptyText.text = "No errors"
    }

    override fun convertValueToText(value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) =
        when (val obj = ((value as? DefaultMutableTreeNode)?.userObject)) {
            is GeneralError -> obj.shortMessage
            is Referable -> obj.textRepresentation()
            else -> obj?.toString() ?: ""
        }
}