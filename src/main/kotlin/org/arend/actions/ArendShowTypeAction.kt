package org.arend.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.core.definition.Function
import org.arend.core.elimtree.ElimBody
import org.arend.core.expr.Expression
import org.arend.ext.core.definition.CoreFunctionDefinition
import org.arend.ext.core.ops.NormalizationMode
import org.arend.psi.ArendDefFunction
import org.arend.psi.ext.ArendCompositeElement
import org.arend.refactoring.*
import org.arend.settings.ArendProjectSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.subexpr.FindBinding
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

        fun select(range: TextRange) =
            editor.selectionModel.setSelection(range.startOffset, range.endOffset)

        fun hint(e: Expression?, element: ArendCompositeElement?) = if (e != null) {
            val normalizePopup = project.service<ArendProjectSettings>().data.popupNormalize
            val definitionRenamer = element?.let { PsiLocatedRenamer(it) }
            if (normalizePopup) normalizeExpr(project, e, NormalizationMode.RNF, definitionRenamer) { exprStr ->
                displayEditorHint(exprStr.toString(), project, editor, AD_TEXT_N)
            } else {
                displayEditorHint(exprToConcrete(project, e, NormalizationMode.RNF, definitionRenamer).toString(), project, editor, AD_TEXT)
            }
        } else throw SubExprException("failed to synthesize type from given expr")

        val sub = try {
            correspondedSubExpr(selected, file, project)
        } catch (e: SubExprException) {
            val (function, tc) = e.def ?: throw e
            if (function !is Concrete.FunctionDefinition || tc !is ArendDefFunction) throw e
            val body = function.body
            // Make sure it's not an expr body
            if (body.term != null) throw e
            val coreDef = tc.tcReferable?.typechecked
            val coreBody = (coreDef as? Function)?.body as? ElimBody
                ?: (coreDef as? CoreFunctionDefinition)?.actualBody as? ElimBody
                ?: throw e
            val psiBody = tc.functionBody ?: throw e
            val bind = binding(psiBody, selected) ?: throw e
            val ref = FindBinding.visitClauses(bind, body.clauses, coreBody.clauses) ?: throw e
            select(bind.textRange)
            hint(ref.typeExpr, null)
            return
        }

        val findBinding = sub.findBinding(selected)
        if (findBinding == null) {
            select(rangeOfConcrete(sub.subConcrete))
            hint(sub.subCore.type, sub.subPsi)
            return
        }
        val (param, type) = findBinding
        select(param.textRange)
        hint(type, null)
    }
}