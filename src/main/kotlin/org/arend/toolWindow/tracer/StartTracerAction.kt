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
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.TCDefinition
import org.arend.psi.linearDescendants
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

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val expr = selectedExpr(file, range) { return displayErrorHint(editor, it.capitalize()) }
            val (head, _) = collectArendExprs(expr.parent, range) ?: return displayErrorHint(editor, "Cannot find a suitable expression.")
            val def = expr.ancestor<TCDefinition>()?.tcReferable ?: return displayErrorHint(editor, "Selected expr must be inside of an expression.")
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