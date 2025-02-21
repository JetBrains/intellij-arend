package org.arend.hierarchy.clazz

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ext.ArendClassFieldBase
import javax.swing.Icon

class ArendFieldHNodeDescriptor(project: Project, parent: HierarchyNodeDescriptor?,
                                element: PsiElement, isBase: Boolean, private val isImplemented: Boolean): ArendHierarchyNodeDescriptor(project, parent, element, isBase) {
    override fun getIcon(element: PsiElement): Icon? {
        val baseIcon = super.getIcon(element)
        if (element is ArendClassFieldBase<*>) {
            return RowIcon(if (isImplemented) AllIcons.Hierarchy.MethodDefined else AllIcons.Hierarchy.MethodNotDefined, baseIcon)
        }
        return baseIcon
    }
}