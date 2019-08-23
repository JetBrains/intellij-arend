package org.arend.toolWindow.errors.tree

import com.intellij.openapi.util.Iconable
import com.intellij.ui.JBDefaultTreeCellRenderer
import org.arend.ArendIcons
import org.arend.error.GeneralError
import org.arend.module.ModulePath
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode


class ArendErrorTreeCellRenderer(tree: ArendErrorTree) : JBDefaultTreeCellRenderer(tree) {
    override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
        when (val obj = (value as? DefaultMutableTreeNode)?.userObject) {
            is ModulePath -> icon = ArendIcons.AREND_FILE
            is Iconable -> icon = obj.getIcon(0)
            is GeneralError -> icon = ArendIcons.getErrorLevelIcon(obj.level)
        }
        return this
    }
}
