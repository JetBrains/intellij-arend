package org.vclang.hierarchy.clazz

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.vclang.hierarchy.VcHierarchyNodeDescriptor
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcLongName

class VcSubClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, VcHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = descriptor.psiElement as VcDefClass
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(classElement, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val subClasses = HashSet<PsiElement>()
        val result = ArrayList<VcHierarchyNodeDescriptor>()
        val options = FindUsagesOptions(myProject)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        if (descriptor.psiElement != null) {
            finder?.processElementUsages(descriptor.psiElement as PsiElement, processor, options)
        }
        for (usage in processor.results) {
            if (usage.element?.parent is VcLongName && usage.element?.parent?.parent is VcDefClass) {
                val subclass = usage.element?.parent?.parent as VcDefClass
                //if (subclass.name != classElement.name) {
                subClasses.add(subclass)
                //}
            }
        }
        subClasses.mapTo(result) { VcHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }
}