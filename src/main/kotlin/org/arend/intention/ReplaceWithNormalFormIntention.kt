package org.arend.intention

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendExpr
import org.arend.psi.ancestor
import org.arend.refactoring.SubExprError
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.normalizeExpr

class ReplaceWithNormalFormIntention : SelfTargetingIntention<ArendExpr>(ArendExpr::class.java, "Replace with Normal Form") {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor) =
            element.ancestor<ArendDefinition>() != null

    private fun doApplyTo(element: ArendExpr, file: PsiFile, project: Project, editor: Editor) = try {
        val range = EditorUtil.getSelectionInAnyMode(editor)
                .takeUnless { it.isEmpty }
                ?: element.textRange
        val (subCore) = correspondedSubExpr(range, file, project)
        normalizeExpr(project, subCore) {
            WriteCommandAction.runWriteCommandAction(project) {
                val length = replaceExpr(editor.document, range, it)
                val startOffset = range.startOffset
                editor.selectionModel
                        .setSelection(startOffset, startOffset + length)
            }
        }
    } catch (t: SubExprError) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance()
                    .apply { showErrorHint(editor, "Failed to normalize because ${t.message}") }
                    .setRequestFocusForNextHint(false)
        }
    }

    private fun replaceExpr(document: Document, range: TextRange, it: String): Int {
        assert(document.isWritable)
        document.deleteString(range.startOffset, range.endOffset)
        return if ('\\' in it || ' ' in it || '\n' in it) {
            // Probably not a single identifier
            val s = "($it)"
            document.insertString(range.startOffset, s)
            s.length
        } else {
            // Do not insert parentheses when it's unlikely to be necessary
            document.insertString(range.startOffset, it)
            it.length
        }
    }


    override fun applyTo(element: ArendExpr, project: Project, editor: Editor) {
        val file = element.containingFile ?: return
        doApplyTo(element, file, project, editor)
    }

}