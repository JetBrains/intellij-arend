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
import org.arend.core.expr.DataCallExpression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.pattern.*
import org.arend.core.subst.ExprSubstitution
import org.arend.ext.variable.Variable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
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
        if (error.parameter == null || error.substitution == null) return false
        val parameterType = error.parameter.type
        if (parameterType !is DataCallExpression) return false
        if (this.error.definition !is DataLocatedReferable) return false

        val dataDef = error.dataCall.definition

        matchResults.clear()

        val reverseSubstitution = ExprSubstitution()
        for (variable in error.substitution.keys) {
            val expr = error.substitution.get(variable)
            if (expr is ReferenceExpression && variable is Binding)
                reverseSubstitution.add(expr.binding, ReferenceExpression(variable))
        }

        for (cons in dataDef.constructors) {
            val substs = HashMap<Binding, ExpressionPattern>()
            matchResults[cons] = MatchResultPair(ExpressionMatcher.computeMatchingPatterns(parameterType, cons, reverseSubstitution, substs) != null, substs)
        }

        return matchResults.values.any { it.canMatch }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        val definition = this.error.definition as DataLocatedReferable //safe cast
        val definitionPsi = definition.data?.element
        val bodyPsi = (definitionPsi as? ArendFunctionalDefinition)?.body
        val functionClausesPsi = when (bodyPsi) {
            is ArendFunctionBody -> bodyPsi.functionClauses
            is ArendInstanceBody -> bodyPsi.functionClauses
            else -> null
        }
        val clausesListPsi = functionClausesPsi?.clauseList

        System.out.println("data: ${definitionPsi?.text}")

        // Add eliminated variables and "primers" for the corresponding patterns
        if (error.caseExpressions == null) {
            val varsToEliminate = HashSet<Variable>()
            for (entry in matchResults) if (entry.value.canMatch) for (subst in entry.value.substs)
                if (subst.value !is BindingPattern) varsToEliminate.add(subst.key)

            val typecheckedParams = DependentLink.Helper.toList(definition.typechecked.parameters)
            val patternPrimers = HashMap<ArendClause, HashMap<Variable, ArendPattern>>()

            if (error.elimParams.isNotEmpty()) { //elim
                val elimPsi = bodyPsi?.elim!! //safe since elimParams is nonempty
                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>()
                for (e in error.elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second
                varsToEliminate.removeAll(error.elimParams)

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
                System.out.println("elimParams: ${error.elimParams}")
                System.out.println("vars to eliminate: $varsToEliminate")

                if (clausesListPsi != null) for (clause in clausesListPsi) {
                    val clauseMap = HashMap<Variable, ArendPattern>()
                    patternPrimers[clause] = clauseMap

                    doInsertPrimers(psiFactory, clause, typecheckedParams, error.elimParams, varsToEliminate, clauseMap) { p -> p.name }
                }
            } else {
                if (clausesListPsi != null) for (clause in clausesListPsi) {
                    val clauseMap = LinkedHashMap<Variable, ArendPattern>()
                    patternPrimers[clause] = clauseMap

                    val parameterIterator = typecheckedParams.iterator()
                    val patternIterator = clause.patternList.iterator()
                    while (parameterIterator.hasNext() && patternIterator.hasNext()) {
                        val pattern = patternIterator.next()
                        var parameter : DependentLink? = parameterIterator.next()
                        if (pattern.isExplicit) while (parameter != null && !parameter.isExplicit)
                            parameter = if (parameterIterator.hasNext()) parameterIterator.next() else null
                        if (parameter != null && pattern.isExplicit == parameter.isExplicit) {
                            clauseMap[parameter] = pattern
                        }
                    }

                    val newVars = varsToEliminate.minus(clauseMap.keys)
                    val newVarsPlusPrecedingImplicitVars = HashSet<Variable>(newVars)
                    var implicitFlag = false
                    for (param in typecheckedParams.reversed()) {
                        if (implicitFlag && !param.isExplicit) newVarsPlusPrecedingImplicitVars.add(param)
                        if (newVars.contains(param) && !param.isExplicit) implicitFlag = true
                        if (param.isExplicit) implicitFlag = false
                    }

                    System.out.println("existing vars: ${clauseMap.keys}")
                    System.out.println("new vars: $newVars")

                    doInsertPrimers(psiFactory, clause, typecheckedParams, ArrayList(clauseMap.keys), newVarsPlusPrecedingImplicitVars, clauseMap) { p ->
                        val builder = StringBuilder()
                        if (!p.isExplicit) builder.append("{")
                        builder.append(if (newVars.contains(p)) p.name else "_")
                        if (!p.isExplicit) builder.append("}")
                        builder.toString()
                    }
                }
            }

            if (clausesListPsi != null) for (clause in clausesListPsi) {
                //TODO:
                // For each clause: find matching constructor of the inductive datatype; (we may need to acquire some additional info from the typechecker)
                // If we can't find it -- do nothing; otherwise find the corresponding matchResult
                // If !matchResult.canMatch -- remove (or comment out) the clause
                // otherwise: for each v in varsToEliminate:
                // set substitutedPattern = mr.second[v]
                // set originalPattern = the well-typed pattern for "patternPrimers[v]" (again, we may need to acquire some additional info from the typechecker)
                // match substitutedPattern against originalPattern (which most often is a BindingPattern)
                // if matchResult is OK then use the tooling of SplitAtomPatternIntention to substitute the resulting substitutions into the clause
                // if it is not OK then replace every occurrence of every free variable of the originalPattern with GOAL and simply replace originalPattern with substitutedPattern
            }

            for (mr in matchResults) {
                print("cons: ${mr.key.name}; conCall: ${mr.value.canMatch}; ")
                for (s in mr.value.substs.entries) print("${s.key.name}->${s.value.toExpression()}; ")
                println()
            }
        } else { // case
            System.out.println("caseExpressions: ${error.caseExpressions}")
        }
    }

    companion object {
        fun doInsertPrimers(psiFactory: ArendPsiFactory,
                            clause: ArendClause,
                            typecheckedParams: List<DependentLink>,
                            eliminatedParams: List<Variable>,
                            eliminatedVars: HashSet<Variable>,
                            clauseMap: HashMap<Variable, ArendPattern>,
                            nameCalculator: (DependentLink) -> String) {
            val patternsMap = HashMap<Variable, ArendPattern>()
            for (e in eliminatedParams.zip(clause.patternList)) patternsMap[e.first] = e.second

            var anchor : PsiElement? = null

            for (param in typecheckedParams) {
                if (eliminatedVars.contains(param)) {
                    val template = psiFactory.createClause("${nameCalculator.invoke(param)}, dummy").childOfType<ArendPattern>()!!
                    val comma = template.nextSibling
                    var commaInserted = false
                    if (anchor != null)  {
                        anchor = clause.addAfter(comma, anchor)
                        commaInserted = true
                        anchor = clause.addAfterWithNotification(template, anchor ?: clause.firstChild)
                        clause.addBefore(psiFactory.createWhitespace(" "), anchor)
                    } else {
                        anchor = clause.addBeforeWithNotification(template, clause.firstChild)
                    }
                    clauseMap[param] = anchor as ArendPattern
                    if (!commaInserted) clause.addAfter(comma, anchor)
                } else {
                    anchor = patternsMap[param]
                }
            }
        }
    }
}