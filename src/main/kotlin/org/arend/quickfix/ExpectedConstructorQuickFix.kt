package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Constructor
import org.arend.core.expr.ConCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.expr.TupleExpression
import org.arend.core.pattern.*
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.variable.Variable
import org.arend.psi.ArendCaseExpr
import org.arend.psi.ArendFunctionBody
import org.arend.psi.ext.ArendCompositeElement
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.error.local.ExpectedConstructorError

class ExpectedConstructorQuickFix(val error: ExpectedConstructorError, val cause: SmartPsiElementPointer<ArendCompositeElement>): IntentionAction {
    private enum class MatchResult { MATCH_OK, DONT_MATCH, UNKNOWN }
    private data class MatchResultPair(val canMatch: Boolean, val substs: HashMap<Variable, ConstructorExpressionPattern>)

    private val matchResults = HashMap<Constructor, MatchResultPair>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (error.dataCall == null || error.substitution == null) return false

        val dataDef = error.dataCall.definition
        val dataCallArgs = error.dataCall.defCallArguments
        val reverseMap = HashMap<Binding, Variable>()
        for (entry in error.substitution.entries) entry.value.let {if (it is ReferenceExpression)
            reverseMap[it.binding] = entry.key
        }


        fun inverseMatch(pattern: Pattern, expression: Expression, substs: MutableMap<Variable, ConstructorExpressionPattern>): MatchResult {
            val normalizedExpression = expression.normalize(NormalizationMode.WHNF)
            if (pattern is EmptyPattern) return MatchResult.DONT_MATCH
            if (normalizedExpression is ReferenceExpression) {
                val correctBinding = reverseMap[normalizedExpression.binding] ?: normalizedExpression.binding
                if (pattern is ExpressionPattern) {
                    if (pattern is ConstructorExpressionPattern) substs[correctBinding] = pattern
                    return MatchResult.MATCH_OK
                }
            }

            val ccArguments = when (normalizedExpression) {
                is ConCallExpression -> normalizedExpression.conCallArguments
                is TupleExpression -> normalizedExpression.fields
                else -> null
            }

            if (pattern is ConstructorExpressionPattern && ccArguments != null) {
                val subpatterns: List<Pattern> = pattern.subPatterns
                if (normalizedExpression is ConCallExpression && normalizedExpression.definition != pattern.definition) return MatchResult.DONT_MATCH
                for ((cPattern, cArgument) in subpatterns.zip(ccArguments)) {
                    when (inverseMatch(cPattern, cArgument, substs)) {
                        MatchResult.DONT_MATCH -> return MatchResult.DONT_MATCH
                        MatchResult.UNKNOWN -> return MatchResult.UNKNOWN
                        else -> {}
                    }
                }
                return MatchResult.MATCH_OK
            }
            return MatchResult.UNKNOWN
        }

        matchResults.clear()

        for (cons in dataDef.constructors) {
            var canMatch = true
            val substs = HashMap<Variable, ConstructorExpressionPattern>()
            for ((pattern, argument) in cons.patterns.zip(dataCallArgs)) {
                val mr = inverseMatch(pattern, argument, substs)
                if (mr == MatchResult.UNKNOWN) return false
                if (mr == MatchResult.DONT_MATCH) canMatch = false
            }
            matchResults[cons] = MatchResultPair(canMatch, substs)
        }

        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val varsToEliminate = HashSet<Variable>()
        val dlr = (this.error.definition as? DataLocatedReferable)?.data?.element
        System.out.println("data: ${dlr?.text}")
        System.out.println("elimParams: ${error.elimParams}")
        System.out.println("caseExpressions: ${error.caseExpressions}")

        // Add eliminated variables
        if (error.caseExpressions == null) {
            if (error.elimParams.isNotEmpty()) { //elim
                
            }
        } else { // case

        }

        for (mr in matchResults) {
            print("cons: ${mr.key.name}; canMatch: ${mr.value.canMatch}; ")
            for (s in mr.value.substs.entries) print("${s.key.name}->${s.value.toExpression()}; ")
            println()
        }

    }
}