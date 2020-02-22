package org.arend.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.psi.ArendExpr
import org.arend.refactoring.SubExprError
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.prettyPopupExpr
import org.arend.refactoring.rangeOfConcrete

class ArendShowNormalFormAction : ArendPopupAction() {
    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val (subCore, subExpr, subPsi) = correspondedSubExpr(range, file, project)
            val textRange = rangeOfConcrete(subExpr)
            editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
            displayHint {
                showInformationHint(editor, prettyPopupExpr(project, subCore))
            }
        } catch (t: SubExprError) {
            displayHint { showErrorHint(editor, "Failed to normalize because ${t.message}") }
        }
    }
}