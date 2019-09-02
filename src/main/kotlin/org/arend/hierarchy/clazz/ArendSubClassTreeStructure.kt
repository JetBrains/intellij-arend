package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass
import org.arend.settings.ArendProjectSettings
import org.arend.util.ClassInheritorsSearch

class ArendSubClassTreeStructure(project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {
    private fun getChildren(descriptor: HierarchyNodeDescriptor): Array<ArendHierarchyNodeDescriptor> {
        val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
        val subClasses = getSubclasses(classElement)
        val result = ArrayList<ArendHierarchyNodeDescriptor>()

        subClasses.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        if (myProject.service<ArendProjectSettings>().showImplFields) {
            classElement.classImplementList.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        }
        if (myProject.service<ArendProjectSettings>().showNonImplFields) {
            classElement.classFieldList.mapTo(result) {
                ArendHierarchyNodeDescriptor(myProject, descriptor, it, false)
            }
        }
        return result.toTypedArray()
    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<out Any> {
        return browser.buildChildren(getChildren(descriptor), TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE)
    }

    private fun getSubclasses(clazz: ArendDefClass): List<PsiElement> {
        return ClassInheritorsSearch.getInstance(myProject).search(clazz)
    }
}