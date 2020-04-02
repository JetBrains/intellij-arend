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
import org.arend.refactoring.SubExprException
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.normalizeExpr
import org.arend.refactoring.rangeOfConcrete

class ReplaceWithNormalFormIntention : SelfTargetingIntention<ArendExpr>(
        ArendExpr::class.java,
        "Replace with Weak Head Normal Form"
) {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor) =
            element.ancestor<ArendDefinition>() != null

    private fun doApplyTo(element: ArendExpr, file: PsiFile, project: Project, editor: Editor) = try {
        val selected = EditorUtil.getSelectionInAnyMode(editor)
                .takeUnless { it.isEmpty }
                ?: element.textRange
        val (subCore, subConcrete) = correspondedSubExpr(selected, file, project)
        normalizeExpr(project, subCore) {
            WriteCommandAction.runWriteCommandAction(project) {
                val range = rangeOfConcrete(subConcrete)
                val length = replaceExpr(editor.document, range, it)
                val start = range.startOffset
                editor.selectionModel.setSelection(start, start + length)
            }
        }
    } catch (t: SubExprException) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance()
                    .apply { showErrorHint(editor, "Failed to normalize because ${t.message}") }
                    .setRequestFocusForNextHint(false)
        }
    }

    private fun replaceExpr(document: Document, range: TextRange, it: String): Int {
        assert(document.isWritable)
        document.deleteString(range.startOffset, range.endOffset)
        val likeIdentifier = '\\' in it || ' ' in it || '\n' in it
        val andNoParenthesesAround = likeIdentifier && !document.charsSequence.let {
            it[range.startOffset - 1] == '(' && it[range.startOffset] == ')'
        }
        return if (andNoParenthesesAround) {
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