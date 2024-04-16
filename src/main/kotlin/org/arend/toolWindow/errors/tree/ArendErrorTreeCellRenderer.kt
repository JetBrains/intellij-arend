package org.arend.toolWindow.errors.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.ui.render.LabelBasedRenderer
import org.arend.ArendIcons
import org.arend.ext.module.ModulePath
import org.arend.psi.ArendFile
import java.awt.Component
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode


class ArendErrorTreeCellRenderer(private val project: Project) : LabelBasedRenderer.Tree() {

    override fun getTreeCellRendererComponent(tree: JTree, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        val obj = ((value as? DefaultMutableTreeNode)?.userObject)
        if (obj is ArendFile) {
            val arendErrorTreeCellRendererService = project.service<ArendErrorTreeCellRendererService>()
            arendErrorTreeCellRendererService.renderCellArendFile(obj, this) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            }
        } else {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
        }

        when (obj) {
            is ModulePath -> icon = ArendIcons.AREND_FILE
            is Iconable -> icon = obj.getIcon(0)
            is ArendErrorTreeElement -> icon = ArendIcons.getErrorLevelIcon(obj.highestError.error)
        }
        return this
    }
}
