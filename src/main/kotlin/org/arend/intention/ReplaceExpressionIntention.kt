package org.arend.intention

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.core.expr.Expression
import org.arend.psi.ArendExpr
import org.arend.psi.ancestor
import org.arend.psi.ext.TCDefinition
import org.arend.refactoring.SubExprException
import org.arend.refactoring.correspondedSubExpr
import org.arend.term.concrete.Concrete

abstract class ReplaceExpressionIntention(text: String) : SelfTargetingIntention<ArendExpr>(ArendExpr::class.java, text) {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor) =
        element.ancestor<TCDefinition>() != null

    private fun doApplyTo(element: ArendExpr, file: PsiFile, project: Project, editor: Editor) = try {
        val selected = EditorUtil.getSelectionInAnyMode(editor)
            .takeUnless { it.isEmpty }
            ?: element.textRange
        val (subCore, subConcrete) = correspondedSubExpr(selected, file, project)
        doApply(project, editor, subCore, subConcrete)
    } catch (t: SubExprException) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance()
                .apply { showErrorHint(editor, "Failed because ${t.message}") }
                .setRequestFocusForNextHint(false)
        }
    }

    protected abstract fun doApply(project: Project, editor: Editor, subCore: Expression, subConcrete: Concrete.Expression)

    protected fun replaceExpr(document: Document, range: TextRange, it: String): Int {
        assert(document.isWritable)
        val startOffset = range.startOffset
        document.deleteString(startOffset, range.endOffset)
        val likeIdentifier = '\\' in it || ' ' in it || '\n' in it
        val andNoParenthesesAround = likeIdentifier && !document.immutableCharSequence.let {
            it[startOffset - 1] == '(' && it[startOffset] == ')'
        }
        val str =
                // Do not insert parentheses when it's unlikely to be necessary
                if (andNoParenthesesAround) "($it)"
                // Probably not a single identifier
                else it
        document.insertString(startOffset, str)
        return str.length
    }

    override fun applyTo(element: ArendExpr, project: Project, editor: Editor) {
        val file = element.containingFile ?: return
        doApplyTo(element, file, project, editor)
    }
}