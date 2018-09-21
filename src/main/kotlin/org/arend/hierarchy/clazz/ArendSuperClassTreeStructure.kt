package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.ArrayUtil
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass

class ArendSuperClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = (descriptor.psiElement ?: return ArrayUtil.EMPTY_OBJECT_ARRAY)
                as? ArendDefClass ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val result = ArrayList<ArendHierarchyNodeDescriptor>()
        classElement.superClassReferences.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it as ArendDefClass, false) }
        return result.toArray()
    }
}