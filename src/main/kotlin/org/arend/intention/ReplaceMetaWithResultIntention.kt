package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.arend.core.expr.ErrorWithConcreteExpression
import org.arend.core.expr.Expression
import org.arend.ext.reference.Precedence
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendLiteral
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.refactoring.prettyPopupExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.replaceExprSmart
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor

class ReplaceMetaWithResultIntention : ReplaceExpressionIntention("Replace meta with result") {
    override fun isApplicableTo(element: ArendExpr, caretOffset: Int, editor: Editor): Boolean {
        if (!super.isApplicableTo(element, caretOffset, editor)) {
            return false
        }
        val refElement = (element as? ArendLiteral)?.ipName ?: ((element as? ArendLiteral)?.longName ?: (element as? ArendArgumentAppExpr)?.longNameExpr?.longName)?.refIdentifierList?.lastOrNull() ?: return false
        return (refElement.resolve as? ModuleAdapter)?.metaReferable?.definition != null
    }

    override fun doApply(project: Project, editor: Editor, subCore: Expression, subConcrete: Concrete.Expression, element: ArendExpr) {
        val text = if (subCore is ErrorWithConcreteExpression) {
            val builder = StringBuilder()
            subCore.expression.accept(PrettyPrintVisitor(builder, 2), Precedence(Concrete.Expression.PREC))
            builder.toString()
        } else {
            prettyPopupExpr(project, subCore, null, element)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            replaceExprSmart(editor.document, element, rangeOfConcrete(subConcrete), text).length
        }
    }
}