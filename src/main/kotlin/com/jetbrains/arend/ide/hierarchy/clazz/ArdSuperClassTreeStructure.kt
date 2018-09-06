package com.jetbrains.arend.ide.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.ArrayUtil
import com.jetbrains.arend.ide.hierarchy.ArdHierarchyNodeDescriptor
import com.jetbrains.arend.ide.psi.ArdDefClass

class ArdSuperClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArdHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = (descriptor.psiElement ?: return ArrayUtil.EMPTY_OBJECT_ARRAY)
                as? ArdDefClass ?: return ArrayUtil.EMPTY_OBJECT_ARRAY
        val result = ArrayList<ArdHierarchyNodeDescriptor>()
        classElement.superClassReferences.mapTo(result) { ArdHierarchyNodeDescriptor(myProject, descriptor, it as ArdDefClass, false) }
        return result.toArray()
    }
}