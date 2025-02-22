package org.arend.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiElement
import org.arend.psi.ext.*

open class ArendHierarchyNodeDescriptor(project: Project, parent: HierarchyNodeDescriptor?,
                                        element: PsiElement, isBase: Boolean) : HierarchyNodeDescriptor(project, parent, element, isBase) {

    override fun update(): Boolean {
        val oldText = myHighlightedText

        if (myHighlightedText.ending.appearance.text == "") when (val element = psiElement) {
            is ArendClassFieldBase<*> -> {
                val fullName = element.fullName
                val clazz = (parentDescriptor as? ArendHierarchyNodeDescriptor)?.psiElement as? ArendDefClass
                if (parentDescriptor?.parentDescriptor != null || clazz?.internalReferables?.contains(element) == true) {
                    myHighlightedText.ending.addText(fullName.longName.lastName.toString())
                } else {
                    myHighlightedText.ending.addText(fullName.longName.toString())
                    if (fullName.module != null) {
                        myHighlightedText.ending.addText(" (" + fullName.module + ')', getPackageNameAttributes())
                    }
                }
            }
            /*is ArendClassImplement -> {
                val name = element.longName
                val clazz = element.parentOfType<ArendDefClass>()
                val ref = name.refIdentifierList.lastOrNull()?.reference?.resolve()
                if (ref is FieldReferable && clazz != null && !clazz.fieldReferables.contains<Any>(ref)) {
                    val fullName = FullName(ref)
                    myHighlightedText.ending.addText(fullName.longName.toString())
                    myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', getPackageNameAttributes())
                } else {
                    myHighlightedText.ending.addText(name.text)
                }
            }*/
            is PsiLocatedReferable -> {
                val fullName = element.fullName
                myHighlightedText.ending.addText(fullName.longName.toString())
                if (fullName.module != null) {
                    myHighlightedText.ending.addText(" (" + fullName.module + ')', getPackageNameAttributes())
                }
            }
        }

        return !Comparing.equal(myHighlightedText, oldText) || super.update()
    }

    /*override fun getIcon(element: PsiElement): Icon? {
        val baseIcon = super.getIcon(element)
        return when (element) {
            //is ArendClassImplement -> RowIcon(AllIcons.Hierarchy.MethodDefined, baseIcon)
            is FieldReferable -> RowIcon(if (isImplemented) AllIcons.Hierarchy.MethodDefined else AllIcons.Hierarchy.MethodNotDefined, baseIcon)
            else -> baseIcon
        }
    }*/

    companion object {
        fun nodePath(node: ArendHierarchyNodeDescriptor): String {
            val parent = node.parentDescriptor?.let { nodePath(it as ArendHierarchyNodeDescriptor) } ?: ""
            return parent + node.highlightedText.ending.appearance.text
        }
    }

}