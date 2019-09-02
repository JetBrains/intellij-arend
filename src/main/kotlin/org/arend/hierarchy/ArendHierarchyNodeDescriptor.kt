package org.arend.hierarchy

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import org.arend.hierarchy.clazz.ArendSuperClassTreeStructure
import org.arend.naming.reference.ClassReferable
import org.arend.psi.ArendClassField
import org.arend.psi.ArendClassImplement
import org.arend.psi.ArendDefClass
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.parentOfType
import org.arend.util.FullName
import javax.swing.Icon

class ArendHierarchyNodeDescriptor(project: Project, parent: HierarchyNodeDescriptor?,
                                   element: PsiElement, isBase: Boolean) : HierarchyNodeDescriptor(project, parent, element, isBase) {

    override fun update(): Boolean {
        val oldText = myHighlightedText

        if (myHighlightedText.ending.appearance.text == "") {
            if (psiElement is ArendClassField) {
                val fullName = FullName(psiElement as PsiLocatedReferable)
                val clazz = (parentDescriptor as? ArendHierarchyNodeDescriptor)?.psiElement as? ArendDefClass
                if (parentDescriptor?.parentDescriptor != null || clazz?.classFieldList?.contains(psiElement as ArendClassField) == true) {
                    myHighlightedText.ending.addText(fullName.longName.lastName.toString())
                } else {
                    myHighlightedText.ending.addText(fullName.longName.toString())
                }
                myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', HierarchyNodeDescriptor.getPackageNameAttributes())
            } else if (psiElement is PsiLocatedReferable) {
                val fullName = FullName(psiElement as PsiLocatedReferable)
                myHighlightedText.ending.addText(fullName.longName.toString())
                myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', HierarchyNodeDescriptor.getPackageNameAttributes())
            } else if (psiElement is ArendClassImplement) {
                val name = (psiElement as ArendClassImplement).longName
                val impl = psiElement as ArendClassImplement
                val clazz = impl.parentOfType<ArendDefClass>()
                val ref = name.refIdentifierList.lastOrNull()?.reference?.resolve()
                if (ref is ArendClassField && clazz != null && !clazz.fields.contains<Any>(ref)) {
                    val fullName = FullName(ref as PsiLocatedReferable)
                    myHighlightedText.ending.addText(fullName.longName.toString())
                    myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', HierarchyNodeDescriptor.getPackageNameAttributes())
                } else {
                    myHighlightedText.ending.addText(name.text)
                }
            }
        }

        return !Comparing.equal(myHighlightedText, oldText) || super.update()
    }

    override fun getIcon(element: PsiElement): Icon? {
        val baseIcon = super.getIcon(element)
        if (element is ArendClassImplement) {
            return RowIcon(AllIcons.Hierarchy.MethodDefined, baseIcon)
        } else if (element is ArendClassField) {
            return RowIcon(AllIcons.Hierarchy.MethodNotDefined, baseIcon)
        }
        return baseIcon
    }

    companion object {
        fun nodePath(node: ArendHierarchyNodeDescriptor): String {
            val parent = node.parentDescriptor?.let { nodePath(it as ArendHierarchyNodeDescriptor) } ?: ""
            return parent + node.highlightedText.ending.appearance.text
        }
    }

}