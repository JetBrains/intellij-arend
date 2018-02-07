package org.vclang.hierarchy.call

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.vclang.hierarchy.VcHierarchyNodeDescriptor
import org.vclang.psi.ext.PsiGlobalReferable


class VcCallerTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, VcHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val defElement = descriptor.psiElement as PsiGlobalReferable
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(defElement, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val callers = HashSet<PsiElement>()
        val result = ArrayList<VcHierarchyNodeDescriptor>()
        val options = FindUsagesOptions(myProject)
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        if (descriptor.psiElement != null) {
            finder?.processElementUsages(defElement, processor, options)
        }
        for (usage in processor.results) {
            val def = PsiTreeUtil.getParentOfType(usage.element, PsiGlobalReferable::class.java)
            if (def != null && def.name != defElement.name) {
                callers.add(def)
            }
        }
        callers.mapTo(result) { VcHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }
}