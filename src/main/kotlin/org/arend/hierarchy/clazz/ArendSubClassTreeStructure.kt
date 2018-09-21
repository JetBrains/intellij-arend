package org.arend.hierarchy.clazz

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendLongName

class ArendSubClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = descriptor.psiElement as ArendDefClass
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(classElement, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val subClasses = HashSet<PsiElement>()
        val result = ArrayList<ArendHierarchyNodeDescriptor>()
        val options = FindUsagesOptions(myProject)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        if (descriptor.psiElement != null) {
            finder?.processElementUsages(descriptor.psiElement as PsiElement, processor, options)
        }
        for (usage in processor.results) {
            if (usage.element?.parent is ArendLongName && usage.element?.parent?.parent is ArendDefClass) {
                val subclass = usage.element?.parent?.parent as ArendDefClass
                //if (subclass.name != classElement.name) {
                subClasses.add(subclass)
                //}
            }
        }
        subClasses.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }
}