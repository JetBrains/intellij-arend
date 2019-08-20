package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.editor.ArendOptions
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass

class ArendSuperClassTreeStructure(project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {

    companion object {
        fun getChildren(descriptor: HierarchyNodeDescriptor, project: Project): Array<ArendHierarchyNodeDescriptor> {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
            val result = ArrayList<ArendHierarchyNodeDescriptor>()
            classElement.superClassReferences.mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it as ArendDefClass, false) }
            if (ArendOptions.instance.showImplFields) {
                classElement.classImplementList.mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it, false) }
            }
            if (ArendOptions.instance.showNonimplFields) {
                if (descriptor.parentDescriptor == null) {
                    getAllFields(classElement, true).mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it, false) }
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

        private fun addSuperclassFields(fields: MutableSet<PsiElement>, implFields: MutableSet<String>, clazz: ArendDefClass, excludeImpl: Boolean) {
            clazz.implementedFields.mapTo(implFields) { it.textRepresentation() }
            clazz.classFieldList.filter { !excludeImpl || !implFields.contains(it.textRepresentation()) }.mapTo(fields) { it }
            for (supClass in clazz.superClassReferences) {
                addSuperclassFields(fields, implFields, supClass as ArendDefClass, excludeImpl)
            }
        }

        fun getAllFields(clazz: ArendDefClass, excludeImpl: Boolean): Set<PsiElement> {
            val fields = HashSet<PsiElement>()
            val implFields = HashSet<String>()
            addSuperclassFields(fields, implFields, clazz, excludeImpl)
            return fields
        }



    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<out Any> {
        val children = getChildren(descriptor, myProject)
        return browser.buildChildren(children, TypeHierarchyBrowserBase.SUPERTYPES_HIERARCHY_TYPE)
    }

}