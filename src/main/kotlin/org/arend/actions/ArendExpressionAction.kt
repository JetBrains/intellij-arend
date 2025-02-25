package org.arend.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.codeInsight.ArendPopupHandler
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.extImpl.definitionRenamer.ConflictDefinitionRenamer
import org.arend.refactoring.exprToConcrete
import org.arend.settings.ArendProjectSettings
import org.arend.tracer.ArendTraceAction
import org.arend.typechecking.SearchingArendCheckerFactory
import org.arend.typechecking.result.TypecheckingResult
import org.arend.typechecking.runner.RunnerService
import org.arend.util.ArendBundle

abstract class ArendExpressionAction(private val message: String, private val getter: (TypecheckingResult) -> Expression?) : ArendPopupAction() {
    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            val (expr, defName) = ArendTraceAction.getElementAtRange(file, editor)
                ?: return displayErrorHint(editor, ArendBundle.message("arend.trace.action.cannot.find.expression"))
            val module = defName.module ?: return displayErrorHint(editor, "Failed to obtain type: cannot locate file")
            val factory = SearchingArendCheckerFactory(expr)
            var text = ""
            project.service<RunnerService>().runChecker(module, defName.longName, factory, {
                val type = factory.checkedExprResult?.let { getter(it) }
                val resultRange = factory.checkedExprRange
                if (type != null && resultRange != null) {
                    val normalizePopup = project.service<ArendProjectSettings>().data.popupNormalize
                    val definitionRenamer = ConflictDefinitionRenamer() // TODO[server2]: PsiLocatedRenamer(expr)
                    text = exprToConcrete(project, type, if (normalizePopup) NormalizationMode.RNF else null, definitionRenamer).toString()
                }
            }) {
                val resultRange = factory.checkedExprRange
                if (resultRange != null) {
                    editor.selectionModel.setSelection(resultRange.startOffset, resultRange.endOffset)
                }
                if (text.isEmpty()) {
                    displayErrorHint(editor, "Failed to obtain type: cannot check expression")
                } else {
                    displayEditorHint(text, project, editor, message)
                }
            }
        }
    }
}