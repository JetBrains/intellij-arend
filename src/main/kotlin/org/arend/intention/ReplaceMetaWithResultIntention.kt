package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.expr.ErrorWithConcreteExpression
import org.arend.core.expr.visitor.ScopeDefinitionRenamer
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.*
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.refactoring.exprToConcrete
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.replaceExprSmart
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.typechecking.TypeCheckingService

class ReplaceMetaWithResultIntention : BaseArendIntention("Replace meta with result") {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val expr = element.ancestor<ArendExpr>()
        val refElement = (expr as? ArendLiteral)?.ipName ?: ((expr as? ArendLiteral)?.longName ?: (expr as? ArendLongNameExpr)?.longName)?.refIdentifierList?.lastOrNull() ?: return false
        return (refElement.resolve as? ModuleAdapter)?.metaReferable?.definition != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val expr = element.ancestor<ArendExpr>() ?: return
        val (subCore, subConcrete, _) = tryCorrespondedSubExpr(expr.textRange, expr.containingFile as? ArendFile ?: return, project, editor ?: return) ?: return
        val cExpr = if (subCore is ErrorWithConcreteExpression) {
            subCore.expression.accept(DefinitionRenamerConcreteVisitor(ScopeDefinitionRenamer(ConvertingScope(project.service<TypeCheckingService>().newReferableConverter(false), expr.scope))), null)
        } else {
            exprToConcrete(project, subCore, null, expr)
        }

        val text = cExpr.toString()
        WriteCommandAction.writeCommandAction(project).run<Exception> {
            replaceExprSmart(editor.document, expr, subConcrete, rangeOfConcrete(subConcrete), null, cExpr, text).length
        }
    }
}