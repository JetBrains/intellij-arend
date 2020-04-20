package org.arend.intention

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.isAncestor
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.psi.ArendGoal
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.refactoring.LocatedReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TypeCheckingService

class GoalFillingIntention : SelfTargetingIntention<ArendGoal>(ArendGoal::class.java,
        "Fill goal with expression inside") {
    // To fill goal, there need to be an expression inside of it
    override fun isApplicableTo(element: ArendGoal, caretOffset: Int, editor: Editor) =
            element.expr != null // && selectedArendError(editor) != null

    override fun applyTo(element: ArendGoal, project: Project, editor: Editor) {
        WriteCommandAction.runWriteCommandAction(project) {
            val psiDef = element.ancestor<PsiConcreteReferable>()
                    ?: return@runWriteCommandAction
            val service = project.service<TypeCheckingService>()
            val refConverter = LocatedReferableConverter(service.newReferableConverter(false))
            var errorReported = false
            val errorReporter = ErrorReporter { reportedError ->
                if (reportedError.level != GeneralError.Level.ERROR)
                    return@ErrorReporter
                errorReported = true
                ApplicationManager.getApplication().invokeLater {
                    HintManager.getInstance().showErrorHint(editor, reportedError.message)
                }
            }
            val concreteProvider = PsiConcreteProvider(project, refConverter, errorReporter, null, true)
            val concreteDef = concreteProvider.getConcrete(psiDef)
                    as? Concrete.Definition
                    ?: return@runWriteCommandAction
            val visitor = object : BaseConcreteExpressionVisitor<Unit>() {
                var isRecursive = false
                override fun visitGoal(expr: Concrete.GoalExpression, params: Unit): Concrete.Expression? {
                    val exprElement = expr.data as? PsiElement
                    if (exprElement != null && exprElement.isAncestor(element, strict = false))
                        return expr.expression
                    return super.visitGoal(expr, params)
                }

                override fun visitReference(expr: Concrete.ReferenceExpression, params: Unit?): Concrete.Expression {
                    if (expr.referent == concreteDef.data) isRecursive = true
                    return super.visitReference(expr, params)
                }
            }
            concreteDef.accept(visitor, Unit)
            val typecheckingService = project.service<TypeCheckingService>()
            val typechecking = ArendTypechecking(PsiInstanceProviderSet(concreteProvider, refConverter),
                    typecheckingService.typecheckerState, concreteProvider, refConverter, errorReporter, typecheckingService.dependencyListener)
            typechecking.unitFound(concreteDef, visitor.isRecursive)
            if (!errorReported) {
                // It's better to use PsiElement's mutation API I believe
                val document = editor.document
                assert(document.isWritable)
                val textRange = element.textRange
                document.deleteString(textRange.endOffset - "}".length, textRange.endOffset)
                document.deleteString(textRange.startOffset, textRange.startOffset + "{?".length)
            }
        }
    }
}