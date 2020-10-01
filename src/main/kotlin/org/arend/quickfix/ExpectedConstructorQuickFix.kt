package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Binding
import org.arend.core.expr.ConCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.pattern.*
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.variable.Variable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.error.local.ExpectedConstructorError

class ExpectedConstructorQuickFix(val error: ExpectedConstructorError, val cause: SmartPsiElementPointer<ArendCompositeElement>): IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (error.dataCall == null || error.substitution == null) return

        val dataDef = error.dataCall.definition
        val dataCallArgs = error.dataCall.defCallArguments
        val reverseMap = HashMap<Binding, Variable>()
        for (entry in error.substitution.entries) entry.value.let {if (it is ReferenceExpression)
            reverseMap[it.binding] = entry.key
        }

        fun inverseMatch(pattern: Pattern, expression: Expression, substs: MutableMap<Variable, ExpressionPattern>): Boolean {
            val normalizedExpression = expression.normalize(NormalizationMode.WHNF)
            if (pattern is EmptyPattern) return false
            if (normalizedExpression is ReferenceExpression) {
                val correctBinding = reverseMap[normalizedExpression.binding]
                if (correctBinding != null && pattern is ExpressionPattern) {
                    substs[correctBinding] = pattern
                    return true
                }
            }

            if (pattern is ConstructorExpressionPattern && normalizedExpression is ConCallExpression) {
                val subpatterns: List<Pattern> = pattern.subPatterns
                val ccArguments = normalizedExpression.conCallArguments
                if (normalizedExpression.definition != pattern.definition) return false
                for ((cPattern, cArgument) in subpatterns.zip(ccArguments))
                    if (!inverseMatch(cPattern, cArgument, substs)) return false
                return true
            }
            return false
        }
        for (cons in dataDef.constructors) {
            for ((pattern, argument) in cons.patterns.zip(dataCallArgs)) {
                val substs = HashMap<Variable, ExpressionPattern>()
                val mr = inverseMatch(pattern, argument, substs)
                print("cons: ${cons.name}; mr: $mr; ")
                for (s in substs.entries) print("${s.key.name}->${s.value.toExpression()}")
                println()
            }
        }
    }
}