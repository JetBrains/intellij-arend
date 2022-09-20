package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.parentOfType

class ArendClassHierarchyProvider : HierarchyProvider {
    override fun getTarget(dataContext: DataContext): ArendDefClass? =
        CommonDataKeys.PSI_ELEMENT.getData(dataContext)?.parentOfType(false)

    override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
        val browser = ArendClassHierarchyBrowser(target.project, target)
        browser.changeView(TypeHierarchyBrowserBase.getSubtypesHierarchyType())
        return browser
    }

    override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {

    }
}