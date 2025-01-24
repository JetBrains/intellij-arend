package org.arend.scratch

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.typechecking.error.ErrorService
import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile

class ArendScratchFile(project: Project, file: VirtualFile) : ScratchFile(project, file) {
    override fun getExpressions(psiFile: PsiFile): List<ScratchExpression> {
        val arendFile = psiFile as? ArendFile ?: return emptyList()
        val result = mutableListOf<ScratchExpression>()
        val document = FileDocumentManager.getInstance().getDocument(psiFile.virtualFile) ?: return emptyList()
        arendFile.traverseGroup { group ->
            val ref = group.referable as? PsiLocatedReferable
            if (ref is TCDefinition) {
                result.add(ScratchExpression(ref, document.getLineNumber(ref.startOffset), document.getLineNumber(ref.endOffset)))
            }
        }
        return result
    }

    override fun hasErrors(): Boolean {
        return !(getPsiFile() as? ArendFile?)?.let { project.service<ErrorService>().getErrors(it) }.isNullOrEmpty()
    }
}
