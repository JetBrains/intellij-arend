package org.arend.intention

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.arend.core.expr.ErrorWithConcreteExpression
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.refactoring.*
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.util.ArendBundle

class ReplaceMetaWithResultIntention : BaseArendIntention(ArendBundle.message("arend.expression.replaceMetaWithResult")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val expr = element.ancestor<ArendExpr>()
        val refElement = (expr as? ArendLiteral)?.ipName ?: (expr as? ArendLiteral)?.refIdentifier ?: (expr as? ArendLongNameExpr)?.longName?.refIdentifierList?.lastOrNull() ?: return false
        val ref = refElement.cachedReferable
        return ref is MetaReferable && (ref.definition != null || ref.resolver != null)
    }

    override fun startInWriteAction() = ApplicationManager.getApplication().isUnitTestMode

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val expr = element.ancestor<ArendExpr>() ?: return
        val (subCore, subConcrete, _) = tryCorrespondedSubExpr(expr.textRange, expr.containingFile as? ArendFile ?: return, project, editor ?: return) ?: return
        val definitionRenamer = PsiLocatedRenamer(expr)
        val cExpr = runReadAction {
            if (subCore is ErrorWithConcreteExpression) {
                subCore.expression.accept(DefinitionRenamerConcreteVisitor(CachingDefinitionRenamer(definitionRenamer)), null)
            } else {
                exprToConcrete(project, subCore, null, CachingDefinitionRenamer(definitionRenamer))
            }
        }

        val text = cExpr.toString()
        WriteCommandAction.writeCommandAction(project).run<Exception> {
            definitionRenamer.writeAllImportCommands()
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
            replaceExprSmart(editor.document, expr, subConcrete, rangeOfConcrete(subConcrete), null, cExpr, text)
        }
    }
}