package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.naming.reference.ClassReferable
import org.arend.psi.ArendDefClass
import org.arend.settings.ArendProjectSettings

class ArendSuperClassTreeStructure(project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {

    companion object {
        fun getChildren(descriptor: HierarchyNodeDescriptor, project: Project): Array<ArendHierarchyNodeDescriptor> {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
            val result = ArrayList<ArendHierarchyNodeDescriptor>()
            classElement.superClassReferences.mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it as ArendDefClass, false) }
            if (project.service<ArendProjectSettings>().data.showImplFields) {
                classElement.classImplementList.mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it, false) }
            }
            if (project.service<ArendProjectSettings>().data.showNonImplFields) {
                if (descriptor.parentDescriptor == null) {
                    ClassReferable.Helper.getNotImplementedFields(classElement).mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it as PsiElement, false) }
                } else {
                    val implFields = HashSet<String>()
                    implInAncestors(descriptor, implFields)
                    classElement.classFieldList.filter { !implFields.contains(it.textRepresentation()) }.mapTo(result) {
                        ArendHierarchyNodeDescriptor(project, descriptor, it, false)
                    }
                }
            }

            return result.toTypedArray()
        }

        private fun implInAncestors(descriptor: HierarchyNodeDescriptor, implFields: MutableSet<String>) {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return
            classElement.implementedFields.mapTo(implFields) { it.textRepresentation() }
            if (descriptor.parentDescriptor != null) {
                implInAncestors(descriptor.parentDescriptor as ArendHierarchyNodeDescriptor, implFields)
            }
        }

        /*
        private fun addSuperclassFields(fields: MutableSet<ArendClassField>, implFields: MutableSet<String>, clazz: ArendDefClass) {
            clazz.implementedFields.mapTo(implFields) { it.textRepresentation() }
            clazz.classFieldList.mapTo(fields) { it }
            for (supClass in clazz.superClassReferences) {
                addSuperclassFields(fields, implFields, supClass as ArendDefClass)
            }
        }

        fun getAllFields(clazz: ArendDefClass, excludeImpl: Boolean): Set<PsiElement> {
            val fields = HashSet<ArendClassField>()
            val implFields = HashSet<String>()
            addSuperclassFields(fields, implFields, clazz)
            if (excludeImpl) {
                return fields.filterNot { implFields.contains(it.textRepresentation()) }.toSet()
            }
            return fields
        } */



    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<out Any> {
        val children = getChildren(descriptor, myProject)
        return browser.buildChildren(children, TypeHierarchyBrowserBase.SUPERTYPES_HIERARCHY_TYPE)
    }

}