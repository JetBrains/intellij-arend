package org.arend.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.util.FullName

class ArendHierarchyNodeDescriptor(project: Project, parent: HierarchyNodeDescriptor?,
                                   element: PsiElement, isBase: Boolean) : HierarchyNodeDescriptor(project, parent, element, isBase) {
    override fun update(): Boolean {
        val oldText = myHighlightedText

        if (psiElement != null && myHighlightedText.ending.appearance.text == "") {
            val fullName = FullName(psiElement as PsiLocatedReferable)
            myHighlightedText.ending.addText(fullName.longName.toString())
            myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', HierarchyNodeDescriptor.getPackageNameAttributes())
        }

        return !Comparing.equal(myHighlightedText, oldText) || super.update()
    }
}