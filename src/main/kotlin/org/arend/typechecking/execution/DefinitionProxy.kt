package org.arend.typechecking.execution

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope

class DefinitionProxy(
    definitionName: String,
    isSuite: Boolean,
    locationUrl: String?,
    preservePresentableName: Boolean = true,
    psi: PsiElement?) : SMTestProxy(definitionName, isSuite, locationUrl, preservePresentableName) {

    private val psiPointer: SmartPsiElementPointer<PsiElement>? =
        if (psi == null) {
            null
        } else {
            val file = psi.containingFile
            SmartPointerManager.getInstance(file.project).createSmartPsiElementPointer(psi, file)
        }

    override fun getLocation(project: Project, searchScope: GlobalSearchScope): Location<*>? =
        psiPointer?.element?.let { PsiLocation(it) }
}
