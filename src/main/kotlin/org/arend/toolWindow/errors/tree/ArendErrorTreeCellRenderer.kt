package org.arend.toolWindow.errors.tree

import com.intellij.openapi.util.Iconable
import com.intellij.ui.render.LabelBasedRenderer
import org.arend.ArendIcons
import org.arend.module.ModuleLocation
import org.arend.naming.reference.GlobalReferable
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode


class ArendErrorTreeCellRenderer : LabelBasedRenderer.Tree() {
    override fun getTreeCellRendererComponent(tree: JTree, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
        when (val obj = (value as? DefaultMutableTreeNode)?.userObject) {
            is ModuleLocation -> icon = ArendIcons.AREND_FILE
            is Iconable -> icon = obj.getIcon(0)
            is ArendErrorTreeElement -> icon = ArendIcons.getErrorLevelIcon(obj.highestError)
            is GlobalReferable -> icon = ArendIcons.referableToIcon(obj)
        }
        return this
    }
}
