package org.arend.typechecking.error

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ArendFileTypeInstance
import org.arend.ext.error.ArgInferenceError
import org.arend.ext.error.GeneralError
import org.arend.ext.error.SourceInfoReference
import org.arend.ext.error.TypeMismatchError
import org.arend.ext.reference.ArendRef
import org.arend.ext.reference.DataContainer
import org.arend.highlight.BasePass
import org.arend.psi.navigate
import org.arend.util.ArendBundle

private class PsiHyperlinkInfo(private val sourceElement: SmartPsiElementPointer<out PsiElement>) : HyperlinkInfo {
    override fun navigate(project: Project) {
        runReadAction { sourceElement.element }?.navigate()
    }
}

private class FixedHyperlinkInfo(private val error: GeneralError) : HyperlinkInfo {
    override fun navigate(project: Project) {
        val cause = BasePass.getCauseElement(error.cause) ?: return
        val file = cause.containingFile?.virtualFile ?: return
        val desc = OpenFileDescriptor(project, file, BasePass.getImprovedTextOffset(error, cause))
        desc.isUseCurrentWindow = FileEditorManager.USE_CURRENT_WINDOW.isIn(cause)
        if (desc.canNavigate()) {
            desc.navigate(true)
        }
    }
}

fun mapToTypeDiffInfo(error: GeneralError?): Pair<DocumentContent, DocumentContent>? {
    val diffContentFactory = DiffContentFactory.getInstance()
    val pair = when (error) {
        is TypeMismatchError -> Pair(error.expected, error.actual)
        is ArgInferenceError -> if (error.expected != null && error.actual != null) Pair(error.expected, error.actual) else null
        else -> null
    } ?: return null
    return Pair(diffContentFactory.create(pair.first.toString(), ArendFileTypeInstance), diffContentFactory.create(pair.second.toString(), ArendFileTypeInstance))
}

class DiffHyperlinkInfo(private val typeDiffInfo: Pair<DocumentContent, DocumentContent>): HyperlinkInfo {
    override fun navigate(project: Project) {
        val (expectedContent, actualContent) = typeDiffInfo

        val diffRequest =
            SimpleDiffRequest(ArendBundle.message("arend.click.to.see.diff.link.title"), expectedContent, actualContent, "Expected type", "Actual type")

        invokeLater {
            DiffManager.getInstance().showDiff(project, diffRequest)
        }
    }
}

fun createHyperlinkInfo(referable: ArendRef, error: GeneralError?): Pair<HyperlinkInfo?, ArendRef> =
    if (error != null && referable is SourceInfoReference) {
        Pair(FixedHyperlinkInfo(error), referable)
    } else {
        val data = (referable as? DataContainer)?.data
        val ref = data as? SmartPsiElementPointer<*> ?: (data as? PsiElement
            ?: referable as? PsiElement)?.let { runReadAction { if (it.isValid) SmartPointerManager.createPointer(it) else null } }
        Pair(ref?.let { PsiHyperlinkInfo(it) }, referable)
    }
