package org.arend.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.core.expr.*
import org.arend.refactoring.*
import org.arend.settings.ArendProjectSettings
import org.jetbrains.annotations.Nls

class ArendShowTypeAction : ArendPopupAction() {
    private companion object {
        @Nls private const val AD_TEXT = "Type of Expression"
        @Nls private const val AD_TEXT_N = "Type of Expression $NF"
    }

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
            doInvoke(editor, file, project)
        } catch (t: SubExprException) {
            displayErrorHint(editor, "Failed to obtain type because ${t.message}")
        }
    }

    @Throws(SubExprException::class)
    private fun ArendPopupHandler.doInvoke(editor: Editor, file: PsiFile, project: Project) {
        val selected = EditorUtil.getSelectionInAnyMode(editor)
        val sub = correspondedSubExpr(selected, file, project)
        fun select(range: TextRange) =
                editor.selectionModel.setSelection(range.startOffset, range.endOffset)

        fun hint(e: Expression?) = e?.let {
            val normalizePopup = project.service<ArendProjectSettings>().data.normalizePopup
            if (normalizePopup) normalizeExpr(project, it) { exprStr ->
                displayEditorHint(exprStr, project, editor, AD_TEXT_N)
            } else {
                displayEditorHint(prettyPopupExpr(project, it), project, editor, AD_TEXT)
            }
        } ?: throw SubExprException("failed to synthesize type from given expr")

        val findBinding = sub.findBinding(selected)
        if (findBinding == null) {
            select(rangeOfConcrete(sub.subConcrete))
            hint(sub.subCore.type)
            return
        }
        val (param, type) = findBinding
        select(param.textRange)
        hint(type)
    }
}