package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendDefClass
import org.arend.settings.ArendProjectSettings

class ArendSuperClassTreeStructure(project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {

    companion object {
        fun getChildren(descriptor: HierarchyNodeDescriptor, project: Project): Array<ArendHierarchyNodeDescriptor> {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
            val result = ArrayList<ArendHierarchyNodeDescriptor>()
            classElement.superClassReferences.mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it as ArendDefClass, false) }
            val settings = project.service<ArendProjectSettings>().data
            if (settings.showImplFields) {
                classElement.implementedFields.mapTo(result) { ArendFieldHNodeDescriptor(project, descriptor, it as PsiElement, isBase = false, isImplemented = true) }
            }
            if (settings.showNonImplFields) {
                if (descriptor.parentDescriptor == null) {
                    ClassReferable.Helper.getNotImplementedFields(classElement).mapTo(result) { ArendFieldHNodeDescriptor(project, descriptor, it as PsiElement, isBase = false, isImplemented = false) }
                } else {
                    val implFields = HashSet<Referable>()
                    implInAncestors(descriptor, implFields)
                    classElement.fieldReferables.mapNotNullTo(result) {
                        if (it is PsiElement && !implFields.contains(it))
                            ArendFieldHNodeDescriptor(project, descriptor, it, isBase = false, isImplemented = false)
                        else null
                    }
                }
            }

            return result.toTypedArray()
        }

        private fun implInAncestors(descriptor: HierarchyNodeDescriptor, implFields: MutableSet<Referable>) {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return
            implFields.addAll(classElement.implementedFields)
            if (descriptor.parentDescriptor != null) {
                implInAncestors(descriptor.parentDescriptor as ArendHierarchyNodeDescriptor, implFields)
            }
        }
    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<out Any> {
        val children = getChildren(descriptor, myProject)
        return browser.buildChildren(children, TypeHierarchyBrowserBase.getSupertypesHierarchyType())
    }
}