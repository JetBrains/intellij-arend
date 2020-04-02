package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.arend.core.expr.Expression
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLiteral
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.refactoring.prettyPopupExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete

class ReplaceMetaWithResultIntention : ReplaceExpressionIntention("Replace meta with result") {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor): Boolean {
        val refElement = (element as? ArendLiteral)?.ipName ?: ((element as? ArendLiteral)?.longName ?: (element as? ArendArgumentAppExpr)?.longNameExpr?.longName)?.refIdentifierList?.lastOrNull() ?: return false
        return (refElement.resolve as? ModuleAdapter)?.metaReferable?.definition != null
    }

    override fun doApply(project: Project, editor: Editor, range: TextRange, subCore: Expression, subConcrete: Concrete.Expression) {
        val text = prettyPopupExpr(project, subCore)
        WriteCommandAction.runWriteCommandAction(project) {
            replaceExpr(editor.document, rangeOfConcrete(subConcrete), text)
        }
    }
}