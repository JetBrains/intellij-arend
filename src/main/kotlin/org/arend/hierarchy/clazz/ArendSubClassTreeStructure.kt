package org.arend.hierarchy.clazz

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.editor.ArendOptions
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendLongName

class ArendSubClassTreeStructure(val project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {
    private fun getChildren(descriptor: HierarchyNodeDescriptor): Array<ArendHierarchyNodeDescriptor> {
        val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
        val subClasses = getSubclasses(classElement)
        val result = ArrayList<ArendHierarchyNodeDescriptor>()

        subClasses.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        if (ArendOptions.instance.showImplFields) {
            classElement.classImplementList.mapTo(result) { ArendHierarchyNodeDescriptor(project, descriptor, it, false) }
        }
        if (ArendOptions.instance.showNonImplFields) {
            classElement.classFieldList.mapTo(result) {
                ArendHierarchyNodeDescriptor(project, descriptor, it, false)
            }
        }
        return result.toTypedArray()
    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<out Any> {
        return browser.buildChildren(getChildren(descriptor), TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE)
    }

    private fun getSubclasses(clazz: ArendDefClass): Set<PsiElement> {
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(clazz, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(myProject)
        val subClasses = HashSet<PsiElement>()
        options.isUsages = true
        options.isSearchForTextOccurrences = false

        finder?.processElementUsages(clazz, processor, options)

        for (usage in processor.results) {
            if (usage.element?.parent is ArendLongName && usage.element?.parent?.parent is ArendDefClass) {
                val parentLongName = usage.element?.parent as ArendLongName
                if (parentLongName.refIdentifierList.last().text == clazz.name) {
                    val subclass = usage.element?.parent?.parent as ArendDefClass

                    subClasses.add(subclass)

                }
            }
        }
        return subClasses
    }
}