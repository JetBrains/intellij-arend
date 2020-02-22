package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.core.expr.Expression
import org.arend.psi.ArendExpr
import org.arend.refactoring.SubExprError
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.rangeOfConcrete


abstract class ArendPopupHandler(private val requestFocus: Boolean) : CodeInsightActionHandler {
    override fun startInWriteAction() = false

    private inline fun displayHint(crossinline f: HintManager.() -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance().apply { setRequestFocusForNextHint(requestFocus) }.f()
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
        val range = EditorUtil.getSelectionInAnyMode(editor)
        val (subCore, subExpr, subPsi) = correspondedSubExpr(range, file, project)
        val textRange = rangeOfConcrete(subExpr)
        editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
        displayHint { showInformationHint(editor, pretty(project, subCore, subPsi, range)) }
    } catch (t: SubExprError) {
        displayHint { showErrorHint(editor, "Failed to obtain type because ${t.message}") }
    }

    abstract fun pretty(project: Project, subCore: Expression, subPsi: ArendExpr, range: TextRange): String
}
