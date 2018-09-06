package com.jetbrains.arend.ide.hierarchy.clazz

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import com.jetbrains.arend.ide.hierarchy.ArdHierarchyNodeDescriptor
import com.jetbrains.arend.ide.psi.ArdDefClass
import com.jetbrains.arend.ide.psi.ArdLongName

class ArdSubClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArdHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = descriptor.psiElement as ArdDefClass
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(classElement, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val subClasses = HashSet<PsiElement>()
        val result = ArrayList<ArdHierarchyNodeDescriptor>()
        val options = FindUsagesOptions(myProject)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        if (descriptor.psiElement != null) {
            finder?.processElementUsages(descriptor.psiElement as PsiElement, processor, options)
        }
        for (usage in processor.results) {
            if (usage.element?.parent is ArdLongName && usage.element?.parent?.parent is ArdDefClass) {
                val subclass = usage.element?.parent?.parent as ArdDefClass
                //if (subclass.name != classElement.name) {
                subClasses.add(subclass)
                //}
            }
        }
        subClasses.mapTo(result) { ArdHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }
}