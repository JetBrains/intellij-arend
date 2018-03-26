package org.vclang.typechecking.execution

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope

class DefinitionProxy(
    definitionName: String,
    isSuite: Boolean,
    locationUrl: String?,
    preservePresentableName: Boolean = true,
    private val psi: PsiElement?) : SMTestProxy(definitionName, isSuite, locationUrl, preservePresentableName) {

    override fun getLocation(project: Project, searchScope: GlobalSearchScope): Location<*>? =
        if (psi != null) PsiLocation(psi) else null
}
