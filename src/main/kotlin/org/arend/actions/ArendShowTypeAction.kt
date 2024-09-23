package org.arend.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.codeInsight.ArendPopupHandler
import org.arend.core.definition.Function
import org.arend.core.elimtree.ElimBody
import org.arend.core.expr.Expression
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.definition.CoreFunctionDefinition
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.error.ErrorReporter
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendDefFunction
import org.arend.refactoring.*
import org.arend.resolving.PsiConcreteProvider
import org.arend.settings.ArendProjectSettings
import org.arend.term.concrete.Concrete
import org.arend.tracer.ArendTraceAction
import org.arend.typechecking.ArendExpressionTypechecker
import org.arend.typechecking.LibraryArendExtensionProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.subexpr.FindBinding
import org.arend.typechecking.visitor.DefinitionTypechecker
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.WhereVarsFixVisitor
import org.arend.util.ArendBundle
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
                ProgressManager.getInstance().run(object : Task.Backgroundable(project, ArendBundle.message("arend.show.type.progress"), true) {
                    override fun shouldStartInBackground(): Boolean = true

                    override fun run(indicator: ProgressIndicator) {
                        val concrete = exprToConcrete(project, e, NormalizationMode.RNF, definitionRenamer).toString()
                        displayEditorHint(concrete, project, editor, AD_TEXT)
                    }
                })
            }
        } else throw SubExprException("failed to synthesize type from given expr")

        val (expr, definitionRef) = ArendTraceAction.getElementAtRange(file, editor)
            ?: return displayErrorHint(editor, ArendBundle.message("arend.trace.action.cannot.find.expression"))
        val result = (PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true)
            .getConcrete(definitionRef) as? Concrete.Definition)?.let {
            val extension = LibraryArendExtensionProvider(project.service<TypeCheckingService>().libraryManager)
                .getArendExtension(it.data)
            val errorReporter = ErrorReporter {  }
            DesugarVisitor.desugar(it, errorReporter)
            WhereVarsFixVisitor.fixDefinition(listOf(it), errorReporter)
            val typechecker = ArendExpressionTypechecker(expr, errorReporter, extension).apply {
                instancePool = GlobalInstancePool(PsiInstanceProviderSet()[it.data], this)
            }
            it.accept(DefinitionTypechecker(typechecker, it.recursiveDefinitions).apply { updateState(false) }, null)
            Pair(typechecker.checkedExprResult, typechecker.checkedExprRange)
        }
        if (result?.first != null && result.second != null) {
            select(result.second!!)
            hint(result.first?.type, expr)
            return
        }

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
            val psiBody = tc.body ?: throw e
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