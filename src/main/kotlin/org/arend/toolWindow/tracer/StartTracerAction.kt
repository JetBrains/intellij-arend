package org.arend.toolWindow.tracer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.ArendIcons
import org.arend.actions.ArendPopupAction
import org.arend.codeInsight.ArendPopupHandler
import org.arend.error.DummyErrorReporter
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.TCDefinition
import org.arend.refactoring.collectArendExprs
import org.arend.refactoring.selectedExpr
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete
import org.arend.typechecking.LibraryArendExtensionProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.computation.BooleanCancellationIndicator
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.visitor.DefinitionTypechecker
import org.arend.typechecking.visitor.DesugarVisitor

class StartTracerAction : ArendPopupAction() {
    init {
        templatePresentation.icon = ArendIcons.TRACER
    }

    private fun getDefinition(file: PsiFile, editor: Editor): Pair<TCDefReferable, ArendExpr>? {
        val range = EditorUtil.getSelectionInAnyMode(editor)
        val sExpr = selectedExpr(file, range)
        if (sExpr != null) {
            val pair = collectArendExprs(sExpr.parent, range)
            if (pair != null) {
                val def = sExpr.ancestor<TCDefinition>()?.tcReferable
                if (def != null) {
                    return Pair(def, pair.first)
                }
            }
        }

        val def = file.findElementAt(editor.caretModel.currentCaret.offset)?.ancestor<TCDefinition>() ?: return null
        val ref = def.tcReferable ?: return null
        val head = when (def) {
            is ArendFunctionalDefinition -> {
                val body = def.body ?: return null
                val expr = body.expr
                if (expr != null) expr else {
                    val clauses = body.clauseList
                    if (clauses.isNotEmpty()) clauses[0].expr else body.coClauseList.firstOrNull()?.expr
                }
            }
            is ArendDefData -> {
                val body = def.dataBody ?: return null
                val constructors = body.constructorList
                val constructor = constructors.firstOrNull() ?: body.constructorClauseList.find { it.constructorList.isNotEmpty() }?.constructorList?.firstOrNull()
                constructor?.typeTeleList?.firstOrNull()?.typedExpr?.expr
            }
            is ArendDefClass -> {
                val classStat = def.classStatList.firstOrNull() ?: return null
                classStat.classField?.returnExpr?.exprList?.firstOrNull()
                    ?: classStat.classImplement?.expr
                    ?: classStat.coClause?.expr
                    ?: classStat.overriddenField?.returnExpr?.exprList?.firstOrNull()
            }
            else -> return null
        } ?: return null
        return Pair(ref, head)
    }

    override fun isValidForFile(project: Project, editor: Editor, file: PsiFile) =
        file is ArendFile && getDefinition(file, editor) != null

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            val (def,head) = getDefinition(file, editor) ?: return displayErrorHint(editor, "Cannot find definition")
            val service = project.service<TypeCheckingService>()
            val cancellationIndicator = BooleanCancellationIndicator()
            val tracer = project.service<ArendTracerService>().createTracer(def.representableName, TracingTypechecker(
                project.service<ErrorService>(),
                LibraryArendExtensionProvider(service.libraryManager).getArendExtension(def),
                head.linearDescendants.last { it is ArendSourceNode },
                editor,
                cancellationIndicator
            ))
            tracer.instancePool = GlobalInstancePool(PsiInstanceProviderSet()[def], tracer)
            val definition = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true).getConcrete(def) as? Concrete.Definition
                ?: return displayErrorHint(editor, "Cannot find concrete definition corresponding to ${def.representableName}.")
            ApplicationManager.getApplication().executeOnPooledThread {
                ComputationRunner<Unit>().run(cancellationIndicator) {
                    DesugarVisitor.desugar(definition, tracer.errorReporter)
                    definition.accept(DefinitionTypechecker(tracer).apply { updateState(false) }, null)
                    runInEdt {
                        tracer.checkAndRemoveExpressionHighlight()
                    }
                }
            }
        }
    }
}