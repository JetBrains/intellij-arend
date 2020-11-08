package org.arend.toolWindow.debugExpr

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
import org.arend.psi.ext.TCDefinition
import org.arend.refactoring.collectArendExprs
import org.arend.refactoring.selectedExpr
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.DefinitionTypechecker

class StartDebuggerAction : ArendPopupAction() {
    init {
        templatePresentation.icon = ArendIcons.DEBUGGER
    }

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val expr = selectedExpr(file, range) { return displayErrorHint(editor, it.capitalize()) }
            val (head, _) = collectArendExprs(expr.parent, range) ?: return displayErrorHint(editor, "Cannot find a suitable expression.")
            val def = expr.ancestor<TCDefinition>()?.tcReferable ?: return displayErrorHint(editor, "Selected expr must be inside of an expression.")
            val debugger = project.service<ArendDebugService>().showFor(head, def)
            val concreteDef = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true).getConcrete(def) as? Concrete.Definition
                ?: return displayErrorHint(editor, "Cannot find concrete definition corresponding to ${def.representableName}.")
            concreteDef.accept(DefinitionTypechecker(debugger), null)
        }
    }
}