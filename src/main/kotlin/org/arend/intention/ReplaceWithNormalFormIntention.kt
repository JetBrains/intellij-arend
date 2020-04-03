package org.arend.intention

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
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
                val document = editor.document
                assert(document.isWritable)
                val startOffset = range.startOffset
                document.deleteString(startOffset, range.endOffset)
                val likeIdentifier = '\\' in it || ' ' in it || '\n' in it
                val andNoParenthesesAround = likeIdentifier && !document.charsSequence.let {
                    it[startOffset - 1] == '(' && it[startOffset] == ')'
                }
                val str =
                        // Do not insert parentheses when it's unlikely to be necessary
                        if (andNoParenthesesAround) "($it)"
                        // Probably not a single identifier
                        else it
                document.insertString(startOffset, str)
                editor.selectionModel.setSelection(startOffset, startOffset + str.length)
            }
        }
    } catch (t: SubExprException) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance()
                    .apply { showErrorHint(editor, "Failed to normalize because ${t.message}") }
                    .setRequestFocusForNextHint(false)
        }
    }


    override fun applyTo(element: ArendExpr, project: Project, editor: Editor) {
        val file = element.containingFile ?: return
        doApplyTo(element, file, project, editor)
    }

}