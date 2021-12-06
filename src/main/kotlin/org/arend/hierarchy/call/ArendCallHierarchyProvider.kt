package org.arend.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ext.PsiLocatedReferable

class ArendCallHierarchyProvider : HierarchyProvider {
    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
        return ArendCallHierarchyBrowser(target.project, target)
    }

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
        (hierarchyBrowser as ArendCallHierarchyBrowser).changeView(CallHierarchyBrowserBase.getCallerType())
    }

    override fun getTarget(dataContext: DataContext): PsiElement? {
        val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        return PsiTreeUtil.getParentOfType(element, PsiLocatedReferable::class.java, false)
    }
}