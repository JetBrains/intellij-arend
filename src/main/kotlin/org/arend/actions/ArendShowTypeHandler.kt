package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.refactoring.SubExprError
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.prettyPopupExpr
import org.arend.refactoring.rangeOfConcrete


class ArendShowTypeHandler(private val requestFocus: Boolean) : CodeInsightActionHandler {
    override fun startInWriteAction() = false

    private inline fun displayHint(crossinline f: HintManager.() -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance().apply { setRequestFocusForNextHint(requestFocus) }.f()
        }
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
        val visited = correspondedSubExpr(editor, file, project)
        val subCore = visited.proj1
        val textRange = rangeOfConcrete(visited.proj2)
        editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
        displayHint { showInformationHint(editor, prettyPopupExpr(project, subCore.type)) }
    } catch (t: SubExprError) {
        displayHint { showErrorHint(editor, "Failed to obtain type because ${t.message}") }
    }
}
