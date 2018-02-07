package org.vclang.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.vclang.psi.VcDefClass

class VcClassHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): PsiElement? {
        val element = CommonDataKeys.PSI_ELEMENT.getData(dataContext)
        return PsiTreeUtil.getParentOfType(element, VcDefClass::class.java, false)
    }

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
        val browser = VcClassHierarchyBrowser(target.project, target)
        browser.changeView(TypeHierarchyBrowserBase.SUBTYPES_HIERARCHY_TYPE)
        return browser
    }

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {

    }
}