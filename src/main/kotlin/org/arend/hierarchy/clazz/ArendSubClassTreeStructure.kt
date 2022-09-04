package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ext.ArendDefClass
import org.arend.search.ClassDescendantsSearch
import org.arend.settings.ArendProjectSettings

class ArendSubClassTreeStructure(project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {

    private fun getChildren(descriptor: HierarchyNodeDescriptor): Array<ArendHierarchyNodeDescriptor> {
        val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
        val subClasses = myProject.service<ClassDescendantsSearch>().search(classElement)
        val result = ArrayList<ArendHierarchyNodeDescriptor>()
        val settings = myProject.service<ArendProjectSettings>().data

        subClasses.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        if (settings.showImplFields) {
            classElement.classImplementList.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        }
        if (settings.showNonImplFields) {
            classElement.fieldReferables.mapNotNullTo(result) {
                if (it is PsiElement) ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) else null
            }
        }
        return result.toTypedArray()
    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor) =
        browser.buildChildren(getChildren(descriptor), TypeHierarchyBrowserBase.getSubtypesHierarchyType())
}