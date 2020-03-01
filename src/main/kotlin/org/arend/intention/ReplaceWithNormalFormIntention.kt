package org.arend.intention

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendExpr
import org.arend.psi.ancestor
import org.arend.refactoring.SubExprError
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.normalizeExpr
import org.arend.refactoring.rangeOfConcrete

class ReplaceWithNormalFormIntention : SelfTargetingIntention<ArendExpr>(ArendExpr::class.java, "Replace with Normal Form") {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor) =
            element.ancestor<ArendDefinition>() != null

    private fun doApplyTo(element: ArendExpr, file: PsiFile, project: Project, editor: Editor) = try {
        val range = EditorUtil.getSelectionInAnyMode(editor)
                .takeUnless { it.isEmpty }
                ?: element.textRange
        val (subCore, subExpr, _) = correspondedSubExpr(range, file, project)
        val textRange = rangeOfConcrete(subExpr)
        editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
        normalizeExpr(project, subCore) {
            editor.document.apply {
                assert(isWritable)
                deleteString(range.startOffset, range.endOffset)
                if ('\\' in it || ' ' in it || '\n' in it) {
                    // Probably not a single identifier
                    insertString(range.startOffset, "($it)")
                } else {
                    // Do not insert parentheses when it's unlikely to be necessary
                    insertString(range.startOffset, it)
                }
            }
        }
    } catch (t: SubExprError) {
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