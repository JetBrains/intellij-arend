package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Constructor
import org.arend.core.expr.ReferenceExpression
import org.arend.core.pattern.*
import org.arend.core.subst.ExprSubstitution
import org.arend.ext.variable.Variable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.error.local.ExpectedConstructorError
import org.arend.typechecking.patternmatching.ExpressionMatcher

class ExpectedConstructorQuickFix(val error: ExpectedConstructorError, val cause: SmartPsiElementPointer<ArendCompositeElement>): IntentionAction {
    private data class MatchResultPair(val canMatch: Boolean, val substs: HashMap<Binding, ExpressionPattern>)

    private val matchResults = HashMap<Constructor, MatchResultPair>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (error.dataCall == null || error.substitution == null) return false
        if (this.error.definition !is DataLocatedReferable) return false

        val dataDef = error.dataCall.definition
        val reverseSubstitution = ExprSubstitution()
        for (variable in error.substitution.keys) {
            val expr = error.substitution.get(variable)
            if (expr is ReferenceExpression && variable is Binding) {
                reverseSubstitution.add(expr.binding, ReferenceExpression(variable))
            }
        }

        matchResults.clear()

        for (cons in dataDef.constructors) {
            val substs = HashMap<Binding, ExpressionPattern>()
            matchResults[cons] = MatchResultPair(ExpressionMatcher.computeMatchingPatterns(error.dataCall, cons, /*error.substitution*/ reverseSubstitution, substs) != null, substs)
        }

        return matchResults.values.any { it.canMatch }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val definition = this.error.definition as DataLocatedReferable //safe cast
        val dlr = definition.data?.element

        System.out.println("data: ${dlr?.text}")
        System.out.println("elimParams: ${error.elimParams}")
        System.out.println("caseExpressions: ${error.caseExpressions}")

        // Add eliminated variables
        val varsToEliminate = HashSet<Variable>()
        if (error.caseExpressions == null) {
            if (error.elimParams.isNotEmpty()) { //elim
                for (entry in matchResults) if (entry.value.canMatch) for (subst in entry.value.substs)
                    if (!error.elimParams.contains(subst.key) && subst.value !is BindingPattern) varsToEliminate.add(subst.key)
                val typecheckedParams = DependentLink.Helper.toList(definition.typechecked.parameters)
                val elimParamIndices = error.elimParams.map { typecheckedParams.indexOf(it) }
                val elimPsi = (dlr as? ArendDefFunction)?.functionBody?.elim!! //safe since elimParams is nonempty
                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>() //TODO: wrong
                for (e in error.elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second
                val psiFactory = ArendPsiFactory(project)
                var anchor: PsiElement? = null
                for (param in typecheckedParams) {
                    if (varsToEliminate.contains(param)) {
                        val template = psiFactory.createRefIdentifier("${param.name}, dummy")
                        val comma = template.nextSibling
                        var commaInserted = false
                        if (anchor != null)  {
                            anchor = elimPsi.addAfter(comma, anchor)
                            commaInserted = true
                        }
                        anchor = elimPsi.addAfterWithNotification(template, anchor ?: elimPsi.elimKw)
                        elimPsi.addBefore(psiFactory.createWhitespace(" "), anchor)
                        if (!commaInserted) {
                            anchor = elimPsi.addAfter(comma, anchor)
                        }
                    } else {
                        anchor = paramsMap[param]
                    }
                }
                System.out.println("indices of original elimParams: $elimParamIndices elimPsiText: ${elimPsi.text}")
                System.out.println("vars to eliminate: $varsToEliminate")
            }
        } else { // case

        }

        for (mr in matchResults) {
            print("cons: ${mr.key.name}; conCall: ${mr.value.canMatch}; ")
            for (s in mr.value.substs.entries) print("${s.key.name}->${s.value.toExpression()}; ")
            println()
        }

    }
}