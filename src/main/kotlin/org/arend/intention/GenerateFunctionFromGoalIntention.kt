package org.arend.intention

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.TypedSingleDependentLink
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.PiExpression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.expr.type.TypeExpression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.extImpl.UncheckedExpressionImpl
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendAtomFieldsAcc
import org.arend.psi.ext.ArendGoal
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionDefinition
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete

class GenerateFunctionFromGoalIntention : AbstractGenerateFunctionIntention() {

    override fun getText(): String = ArendBundle.message("arend.generate.function.from.goal")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!canModify(file) || !BaseArendIntention.canModify(file)) {
            return false
        }
        return file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) != null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY

    override fun extractSelectionData(file: PsiFile, editor: Editor, project: Project): SelectionResult? {
        val goal = file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) ?: return null
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal }?.error as? GoalError
                ?: return null
        val goalType = arendError.expectedType
        val (modifiedType, customArguments) = getCustomArguments(goal, goalType) { tryCorrespondedSubExpr(it, file, project, editor)?.subCore }
        val goalExpr = goal.expr?.let {
            tryCorrespondedSubExpr(it.textRange, file, project, editor)
        }?.subCore
        return SelectionResult((modifiedType ?: goalType)?.normalize(NormalizationMode.RNF),
                goal,
                goal.textRange,
                null,
                goal.defIdentifier?.name,
                goal.expr?.text,
                goalExpr,
                customArguments)
    }

    private fun getCustomArguments(goal: ArendGoal, goalType: Expression?, typechecker: (TextRange) -> Expression?): Pair<Expression?, List<TypedSingleDependentLink>> {
        val emptyResult = null to emptyList<TypedSingleDependentLink>()
        if (goalType !is PiExpression) return emptyResult
        val goalApplication = goal.parentOfType<ArendArgumentAppExpr>()?.takeIf {
            it.atomFieldsAcc?.isEffectivelyGoal(goal) ?: false
        } ?: return emptyResult
        return unrollGoalType(goal, goalType, goalApplication, typechecker)
    }

    private fun unrollGoalType(goal: ArendGoal, goalType: Expression, goalApplication: ArendArgumentAppExpr, typechecker: (TextRange) -> Expression?): Pair<Expression, List<TypedSingleDependentLink>> {
        var currentGoalType: Expression = goalType
        val forbiddenNames = mutableSetOf<String>()
        val customArguments = mutableListOf<TypedSingleDependentLink>()
        val reverseSubstitution: MutableMap<Expression, Binding> = LinkedHashMap()
        for ((arg, isExplicit) in computeGoalArguments(goal, goalApplication)) {
            currentGoalType as PiExpression
            val expectedType = currentGoalType.parameters.typeExpr
            val typechecked = typechecker(arg.textRange)
            if (typechecked == null) {
                customArguments.add(TypedSingleDependentLink(isExplicit, "_", TypeExpression(expectedType, null)))
            } else {
                val contextElement = goal.parentOfType<ArendFunctionDefinition<*>>() ?: goal
                val newBinding = TypedBinding(suggestParameterName(forbiddenNames, contextElement, expectedType), expectedType)
                customArguments.add(TypedSingleDependentLink(isExplicit, newBinding.name, TypeExpression(expectedType, null)))
                reverseSubstitution[typechecked] = newBinding
            }
            currentGoalType = currentGoalType.codomain
        }
        val substitutedType = performReverseSubstitution(currentGoalType, reverseSubstitution)
        return substitutedType to customArguments
    }

    private fun performReverseSubstitution(type: Expression, reverseSubstitution: Map<Expression, Binding>): Expression {
        var currentType = type
        for ((subExpr, binding) in reverseSubstitution) {
            val uncheckedTargetExpression = currentType.replaceSubexpressions ({
                if (it == subExpr) {
                    ReferenceExpression(binding)
                } else {
                    null
                }
            }, false)
            currentType = UncheckedExpressionImpl.extract(uncheckedTargetExpression)
        }
        return currentType
    }

    private fun computeGoalArguments(goal: ArendGoal, psiAppExpr: ArendArgumentAppExpr): List<Pair<PsiElement, Boolean>> {
        val parsedAppExpr = appExprToConcrete(psiAppExpr, true)
                ?: return psiAppExpr.argumentList.map { it to it.isExplicit }
        var result = emptyList<Pair<PsiElement, Boolean>>()
        parsedAppExpr.accept(object : BaseConcreteExpressionVisitor<Unit>() {
            override fun visitApp(expr: Concrete.AppExpression, params: Unit?): Concrete.Expression {
                val functionPsi = expr.function.data as PsiElement
                if (functionPsi.isEffectivelyGoal(goal)) {
                    result = expr.arguments.map { it.expression.data as PsiElement to it.isExplicit }
                    return expr
                }
                return super.visitApp(expr, params)
            }
        }, Unit)
        return result
    }

    private fun PsiElement.isEffectivelyGoal(goal: PsiElement): Boolean {
        return this is ArendAtomFieldsAcc && PsiTreeUtil.isAncestor(this, goal, false)
    }

    private fun suggestParameterName(forbiddenNames: MutableSet<String>, context: ArendCompositeElement, type: Expression): String {
        var candidate = if (type is DefCallExpression && type.definition.name[0].isLetter()) {
            type.definition.name[0].lowercase()
        } else {
            "x"
        }
        while (forbiddenNames.contains(candidate) || context.scope.resolveName(candidate) != null) {
            candidate += '\''
        }
        forbiddenNames.add(candidate)
        return candidate
    }
}