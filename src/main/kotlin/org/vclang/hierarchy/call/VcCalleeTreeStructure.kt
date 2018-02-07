package org.vclang.hierarchy.call

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.vclang.hierarchy.VcHierarchyNodeDescriptor
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.resolving.VcReference

class VcCalleeTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, VcHierarchyNodeDescriptor(project, null, baseNode, true)) {

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val defElement = descriptor.psiElement as PsiGlobalReferable // ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val callees = HashSet<PsiElement>()
        val result = ArrayList<VcHierarchyNodeDescriptor>()
        visit(defElement, callees, defElement)
        callees.mapTo(result) { VcHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }

    private fun visit(element: PsiElement, callees: HashSet<PsiElement>, root: PsiGlobalReferable) {
        val children = element.children
        for (child in children) {
            visit(child, callees, root)
            if (child is VcReferenceElement && root.name != child.referenceName) {
                if (child.reference != null) {
                    val ref = (child.reference as VcReference).resolve()
                    if (ref != null && ref is PsiGlobalReferable) {
                        callees.add(ref)
                    }
                }
            }
        }
    }
}