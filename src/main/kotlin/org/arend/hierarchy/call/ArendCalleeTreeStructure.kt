package org.arend.hierarchy.call

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.ArendReferenceElement

class ArendCalleeTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val defElement = descriptor.psiElement as PsiLocatedReferable // ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val callees = HashSet<PsiElement>()
        val result = ArrayList<ArendHierarchyNodeDescriptor>()
        visit(defElement, callees, defElement)
        callees.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }

    private fun visit(element: PsiElement, callees: HashSet<PsiElement>, root: PsiLocatedReferable) {
        val children = element.children
        for (child in children) {
            visit(child, callees, root)
            if (child is ArendReferenceElement && root.name != child.referenceName) {
                val ref = child.reference?.resolve()
                if (ref is PsiLocatedReferable) {
                    callees.add(ref)
                }
            }
        }
    }
}