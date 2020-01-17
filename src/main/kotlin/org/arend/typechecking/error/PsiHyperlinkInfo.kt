package org.arend.typechecking.error

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.GeneralError
import org.arend.ext.error.SourceInfo
import org.arend.ext.error.SourceInfoReference
import org.arend.ext.reference.ArendRef
import org.arend.ext.reference.DataContainer
import org.arend.highlight.BasePass
import org.arend.psi.navigate

class PsiHyperlinkInfo(private val sourceElement: SmartPsiElementPointer<out PsiElement>) : HyperlinkInfo {
    companion object {
        private fun replaceReference(ref: ArendRef, error: GeneralError?): ArendRef {
            if (error != null && ref is SourceInfoReference) {
                val newCause = BasePass.getImprovedCause(error)
                if (newCause != null) {
                    return SourceInfoReference(newCause as? SourceInfo
                        ?: PsiSourceInfo(runReadAction { SmartPointerManager.createPointer(newCause) }))
                }
            }
            return ref
        }

        fun create(referable: ArendRef, error: GeneralError?): Pair<PsiHyperlinkInfo?, ArendRef> {
            val reference = replaceReference(referable, error)
            val data = (reference as? DataContainer)?.data
            val ref = data as? SmartPsiElementPointer<*> ?:
                    (data as? PsiElement ?: reference as? PsiElement)?.let { runReadAction { SmartPointerManager.createPointer(it) } }
            return Pair(ref?.let { PsiHyperlinkInfo(it) }, reference)
        }
    }

    override fun navigate(project: Project?) {
        runReadAction { sourceElement.element }?.navigate()
    }
}