package org.vclang.typechecking.execution

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

class DefinitionProxy(
    definitionName: String,
    isSuite: Boolean,
    locationUrl: String?,
    preservePresentableName: Boolean = true,
    psi: PsiElement?) : SMTestProxy(definitionName, isSuite, locationUrl, preservePresentableName) {

    private val myPsi = psi

    fun addText(text: String, contentType: ConsoleViewContentType) =
        addAfterLastPassed { it.print(text, contentType) }

    fun addHyperlink(text: String, info: HyperlinkInfo?) =
        addAfterLastPassed { it.printHyperlink(text, info) }

    override fun getLocation(project: Project, searchScope: GlobalSearchScope): Location<*>? {
        if (myPsi != null) return PsiLocation(myPsi)
        return null
    }
}
