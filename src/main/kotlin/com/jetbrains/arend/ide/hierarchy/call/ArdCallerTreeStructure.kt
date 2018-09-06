package com.jetbrains.arend.ide.hierarchy.call

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import com.jetbrains.arend.ide.hierarchy.ArdHierarchyNodeDescriptor
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable


class ArdCallerTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArdHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val defElement = descriptor.psiElement as PsiLocatedReferable
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(defElement, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val callers = HashSet<PsiElement>()
        val result = ArrayList<ArdHierarchyNodeDescriptor>()
        val options = FindUsagesOptions(myProject)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        if (descriptor.psiElement != null) {
            finder?.processElementUsages(defElement, processor, options)
        }
        for (usage in processor.results) {
            val def = PsiTreeUtil.getParentOfType(usage.element, PsiLocatedReferable::class.java)
            if (def != null && def.name != defElement.name) {
                callers.add(def)
            }
        }
        callers.mapTo(result) { ArdHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }
}