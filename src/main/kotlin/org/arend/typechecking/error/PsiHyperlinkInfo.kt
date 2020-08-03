package org.arend.typechecking.error

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.error.GeneralError
import org.arend.ext.error.SourceInfoReference
import org.arend.ext.reference.ArendRef
import org.arend.ext.reference.DataContainer
import org.arend.highlight.BasePass
import org.arend.psi.navigate

private class PsiHyperlinkInfo(private val sourceElement: SmartPsiElementPointer<out PsiElement>) : HyperlinkInfo {
    override fun navigate(project: Project?) {
        runReadAction { sourceElement.element }?.navigate()
    }
}

private class FixedHyperlinkInfo(private val error: GeneralError) : HyperlinkInfo {
    override fun navigate(project: Project?) {
        val cause = BasePass.getCauseElement(error.cause) ?: return
        val desc = OpenFileDescriptor(project ?: cause.project, cause.containingFile.virtualFile, BasePass.getImprovedTextOffset(error, cause))
        desc.isUseCurrentWindow = FileEditorManager.USE_CURRENT_WINDOW.isIn(cause)
        if (desc.canNavigate()) {
            desc.navigate(true)
        }
    }
}

fun createHyperlinkInfo(referable: ArendRef, error: GeneralError?): Pair<HyperlinkInfo?, ArendRef> =
    if (error != null && referable is SourceInfoReference) {
        Pair(FixedHyperlinkInfo(error), referable)
    } else {
        val data = (referable as? DataContainer)?.data
        val ref = data as? SmartPsiElementPointer<*> ?: (data as? PsiElement
            ?: referable as? PsiElement)?.let { runReadAction { SmartPointerManager.createPointer(it) } }
        Pair(ref?.let { PsiHyperlinkInfo(it) }, referable)
    }
