package org.vclang.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.vclang.psi.ext.PsiGlobalReferable

class VcCallHierarchyProvider : HierarchyProvider {
    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
        return VcCallHierarchyBrowser(target.project, target)
    }

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as VcCallHierarchyBrowser).changeView(CallHierarchyBrowserBase.CALLER_TYPE)
    }

    override fun getTarget(dataContext: DataContext): PsiElement? {
        val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        return PsiTreeUtil.getParentOfType(element, PsiGlobalReferable::class.java, false)
    }
}