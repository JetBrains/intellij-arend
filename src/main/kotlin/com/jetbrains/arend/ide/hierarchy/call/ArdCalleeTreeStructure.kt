package com.jetbrains.arend.ide.hierarchy.call

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.hierarchy.ArdHierarchyNodeDescriptor
import com.jetbrains.arend.ide.psi.ext.ArdReferenceElement
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable
import com.jetbrains.arend.ide.resolving.ArdReference

class ArdCalleeTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArdHierarchyNodeDescriptor(project, null, baseNode, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val defElement = descriptor.psiElement as PsiLocatedReferable // ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val callees = HashSet<PsiElement>()
        val result = ArrayList<ArdHierarchyNodeDescriptor>()
        visit(defElement, callees, defElement)
        callees.mapTo(result) { ArdHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }

    private fun visit(element: PsiElement, callees: HashSet<PsiElement>, root: PsiLocatedReferable) {
        val children = element.children
        for (child in children) {
            visit(child, callees, root)
            if (child is ArdReferenceElement && root.name != child.referenceName) {
                if (child.reference != null) {
                    val ref = (child.reference as ArdReference).resolve()
                    if (ref != null && ref is PsiLocatedReferable) {
                        callees.add(ref)
                    }
                }
            }
        }
    }
}