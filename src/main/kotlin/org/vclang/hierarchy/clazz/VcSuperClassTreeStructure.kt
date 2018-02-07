package org.vclang.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.ArrayUtil
import org.vclang.hierarchy.VcHierarchyNodeDescriptor
import org.vclang.psi.VcDefClass

class VcSuperClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, VcHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = (descriptor.psiElement ?: return ArrayUtil.EMPTY_OBJECT_ARRAY)
                as? VcDefClass ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val result = ArrayList<VcHierarchyNodeDescriptor>()
        classElement.superClassReferences.mapTo(result) { VcHierarchyNodeDescriptor(myProject, descriptor, it as VcDefClass, false) }
        return result.toArray()
    }
}