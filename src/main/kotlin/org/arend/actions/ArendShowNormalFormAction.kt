package org.arend.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.codeInsight.ArendPopupHandler
import org.arend.ext.core.ops.NormalizationMode
import org.arend.refactoring.*
import org.arend.settings.ArendProjectSettings
import org.jetbrains.annotations.Nls

/**
 * This class is better called "ArendShowElaboratedAction"
 */
class ArendShowNormalFormAction : ArendPopupAction() {
    private companion object {
        @Nls private const val AD_TEXT = "Elaborated Expression"
        @Nls private const val AD_TEXT_N = "Elaborated Expression $NF"
    }

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val (subCore, subExpr, subPsi) = correspondedSubExpr(range, file, project)
            val textRange = rangeOfConcrete(subExpr)
            editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
            val normalizePopup = project.service<ArendProjectSettings>().data.popupNormalize
            if (normalizePopup) normalizeExpr(project, subCore, NormalizationMode.ENF, PsiLocatedRenamer(subPsi)) {
                displayEditorHint(it.toString(), project, editor, AD_TEXT_N)
            } else {
                displayEditorHint(exprToConcrete(project, subCore).toString(), project, editor, AD_TEXT)
            }
        } catch (t: SubExprException) {
            displayErrorHint(editor, "Failed to normalize because ${t.message}")
        }
    }
}