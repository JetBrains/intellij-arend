package org.arend.tracer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.LayeredIcon
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.arend.actions.ArendPopupAction
import org.arend.codeInsight.ArendPopupHandler
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.collectArendExprs
import org.arend.refactoring.selectedExpr
import org.arend.server.ArendServerService
import org.arend.term.concrete.Concrete
import org.arend.typechecking.LibraryArendExtensionProvider
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.local.GoalDataHolder
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.visitor.DefinitionTypechecker
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.typechecking.visitor.WhereVarsFixVisitor
import org.arend.util.ArendBundle
import org.arend.util.list.PersistentList

class ArendTraceAction : ArendPopupAction() {
    init {
        templatePresentation.icon = TRACE_ICON
    }

    override fun update(presentation: Presentation, project: Project, editor: Editor, file: PsiFile) {
        if (file !is ArendFile) {
            presentation.isEnabled = false
            return
        }
        val expressionAtCaret = getExpressionAtCaret(file, editor)
        if (expressionAtCaret != null) {
            presentation.isEnabled = true
            presentation.text = "Trace to Expression"
            return
        }
        val declarationAtCaret = getDeclarationAtCaret(file, editor)
        if (declarationAtCaret != null) {
            presentation.isEnabled = true
            presentation.text = "Trace '${declarationAtCaret.second.representableName}'"
            return
        }
        presentation.isEnabled = false
    }

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            /* TODO[server2]
            val (expression, definitionRef) = getElementAtCursor(file, editor)
                ?: return displayErrorHint(editor, ArendBundle.message("arend.trace.action.cannot.find.expression"))
            val definition = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true)
                .getConcrete(definitionRef) as? Concrete.Definition
                ?: return displayErrorHint(
                    editor, ArendBundle.message(
                        "arend.trace.action.cannot.find.concrete.definition",
                        definitionRef.representableName
                    )
                )
            val tracingData = runTracingTypechecker(project, definition, expression)
            val starter = object : XDebugProcessStarter() {
                override fun start(session: XDebugSession): XDebugProcess = ArendTraceProcess(session, tracingData)
            }
            XDebuggerManager.getInstance(project).startSessionAndShowTab(
                definition.data.representableName,
                TRACE_ICON,
                null,
                false,
                starter
            )
            */
        }
    }

    companion object {
        private val TRACE_ICON = LayeredIcon.create(AllIcons.Actions.ListFiles, AllIcons.Nodes.RunnableMark)

        fun getElementAtRange(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val sExpr = selectedExpr(file, range)
            if (sExpr != null) {
                val def = sExpr.ancestor<PsiLocatedReferable>()?.let { file.project.service<ArendServerService>().server.getTCReferable(it) }
                if (def != null) {
                    return Pair(sExpr, def)
                }
            }
            return null
        }

        fun getElementAtCursor(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            return getExpressionAtCaret(file, editor) ?: getDeclarationAtCaret(file, editor)
        }

        private fun getExpressionAtCaret(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val sExpr = selectedExpr(file, range)
            if (sExpr != null) {
                val expression = collectArendExprs(sExpr.parent, range)?.first as? ArendExpr
                if (expression != null) {
                    val def = sExpr.ancestor<PsiLocatedReferable>()?.let { file.project.service<ArendServerService>().server.getTCReferable(it) }
                    if (def != null) {
                        return Pair(expression, def)
                    }
                }
            }
            return null
        }

        private fun getDeclarationAtCaret(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            val def = file.findElementAt(editor.caretModel.currentCaret.offset)?.ancestor<PsiLocatedReferable>() ?: return null
            val ref = file.project.service<ArendServerService>().server.getTCReferable(def) ?: return null
            val head = when (def) {
                is ArendFunctionDefinition<*> -> {
                    val body = def.body ?: return null
                    val expr = body.expr
                    if (expr != null) expr else {
                        val clauses = body.clauseList
                        if (clauses.isNotEmpty()) clauses[0].expression else body.coClauseList.firstOrNull()?.expr
                    }
                }
                is ArendDefData -> {
                    val body = def.dataBody ?: return null
                    val constructors = body.constructorList
                    val constructor = constructors.find {
                        it.parameters.isNotEmpty()
                    } ?: body.constructorClauseList.find {
                            it.constructors.any { constructor -> constructor.parameters.isNotEmpty() }
                        }?.constructors?.find {
                            it.parameters.isNotEmpty()
                        }
                    constructor?.parameters?.firstOrNull()?.type
                }
                is ArendDefClass -> {
                    val classStat = def.classStatList.firstOrNull()
                    classStat?.classField?.returnExpr?.type
                        ?: classStat?.classImplement?.expr?.type
                        ?: classStat?.coClause?.expr?.type
                        ?: classStat?.overriddenField?.returnExpr?.type
                        ?: def.classFieldList.firstOrNull()?.returnExpr?.type
                        ?: def.classImplementList.firstOrNull()?.expr?.type
                        ?: def.fieldTeleList.firstOrNull()?.type
                }
                else -> return null
            } ?: return null
            return Pair(head, ref)
        }

        internal fun runTracingTypechecker(
            project: Project,
            definition: Concrete.Definition,
            expression: ArendExpr
        ): ArendTracingData {
            val extension = LibraryArendExtensionProvider(project.service<TypeCheckingService>().libraryManager)
                .getArendExtension(definition.data)
            val errorsConsumer = ErrorsConsumer()
            val tracer = ArendTracingTypechecker(errorsConsumer, extension).apply {
                instancePool = GlobalInstancePool(PersistentList.empty() /* TODO[server2]: PsiInstanceProviderSet()[definition.data] */, this)
            }
            var firstTraceEntryIndex = -1
            ActionUtil.underModalProgress(project, ArendBundle.message("arend.tracer.collecting.tracing.data")) {
                DesugarVisitor.desugar(definition, tracer.errorReporter)
                WhereVarsFixVisitor.fixDefinition(listOf(definition), tracer.errorReporter)
                definition.accept(DefinitionTypechecker(tracer, definition.recursiveDefinitions).apply { updateState(false) }, null)
                firstTraceEntryIndex = tracer.trace.indexOfEntry(expression)
            }
            return ArendTracingData(tracer.trace, errorsConsumer.hasErrors, firstTraceEntryIndex)
        }

        private class ErrorsConsumer : ErrorReporter {
            var hasErrors = false

            override fun report(error: GeneralError) {
                if (error !is GoalDataHolder) {
                    hasErrors = true
                }
            }
        }
    }
}