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
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val substs = HashMap<Binding, ExpressionPattern>()
        val parameterType = error.parameter?.type
        val definition = error.definition as? DataLocatedReferable //safe cast
        val constructor = error.referable as? DataLocatedReferable
        val constructorTypechecked = constructor?.typechecked as? Constructor
        val clause = cause.element?.ancestor<ArendClause>()

        if (error.parameter == null || error.substitution == null || definition == null || constructor == null || clause == null ||
                constructorTypechecked == null || parameterType !is DataCallExpression) return // We could show a notification?

        val reverseSubstitution = ExprSubstitution()
        for (variable in error.substitution.keys) {
            val expr = error.substitution.get(variable)
            if (expr is ReferenceExpression && variable is Binding)
                reverseSubstitution.add(expr.binding, ReferenceExpression(variable))
        }

        // Stage 1: match data type constructor with actual  parameters
        val canMatch: Boolean = ExpressionMatcher.computeMatchingPatterns(parameterType, constructorTypechecked, reverseSubstitution, substs) != null

        if (!canMatch) return // We could show a notification?

        val psiFactory = ArendPsiFactory(project)

        val definitionPsi = definition.data?.element
        val bodyPsi = (definitionPsi as? ArendFunctionalDefinition)?.body
        val functionClausesPsi = when (bodyPsi) {
            is ArendFunctionBody -> bodyPsi.functionClauses
            is ArendInstanceBody -> bodyPsi.functionClauses
            else -> null
        }
        val clausesListPsi = functionClausesPsi?.clauseList

        println("data: ${definitionPsi?.text}")

        if (error.caseExpressions == null) {
            val varsToEliminate = HashSet<Variable>()
            for (subst in substs) if (subst.value !is BindingPattern) varsToEliminate.add(subst.key)

            val typecheckedParams = DependentLink.Helper.toList(definition.typechecked.parameters)
            val patternPrimers = HashMap<Variable, ArendPattern>()

            if (error.elimParams.isNotEmpty()) { //elim
                val elimPsi = bodyPsi?.elim!! //safe since elimParams is nonempty
                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>()
                for (e in error.elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second
                for (e in error.elimParams.zip(clause.patternList)) patternPrimers[e.first] = e.second

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
                println("elimParams: ${error.elimParams}")
                println("vars to eliminate: $varsToEliminate")

                if (clausesListPsi != null) for (transformedClause in clausesListPsi) {
                    doInsertPrimers(psiFactory, transformedClause, typecheckedParams, error.elimParams, varsToEliminate, if (transformedClause == clause) patternPrimers else null) { p -> p.name }
                }
            } else {
                if (clausesListPsi != null) for (transformedClause in clausesListPsi) {
                    val parameterIterator = typecheckedParams.iterator()
                    val patternIterator = transformedClause.patternList.iterator()
                    val patternMatchedParameters = LinkedHashSet<Variable>()
                    while (parameterIterator.hasNext() && patternIterator.hasNext()) {
                        val pattern = patternIterator.next()
                        var parameter : DependentLink? = parameterIterator.next()
                        if (pattern.isExplicit) while (parameter != null && !parameter.isExplicit)
                            parameter = if (parameterIterator.hasNext()) parameterIterator.next() else null
                        if (parameter != null && pattern.isExplicit == parameter.isExplicit) {
                            patternMatchedParameters.add(parameter)
                            if (transformedClause == clause) patternPrimers[parameter] = pattern
                        }
                    }

                    val newVars = varsToEliminate.minus(patternMatchedParameters)
                    val newVarsPlusPrecedingImplicitVars = HashSet<Variable>(newVars)
                    var implicitFlag = false
                    for (param in typecheckedParams.reversed()) {
                        if (implicitFlag && !param.isExplicit) newVarsPlusPrecedingImplicitVars.add(param)
                        if (newVars.contains(param) && !param.isExplicit) implicitFlag = true
                        if (param.isExplicit) implicitFlag = false
                    }

                    println("new vars: $newVars")

                    doInsertPrimers(psiFactory, transformedClause, typecheckedParams, ArrayList(patternMatchedParameters), newVarsPlusPrecedingImplicitVars, if (transformedClause == clause) patternPrimers else null) { p ->
                        val builder = StringBuilder()
                        if (!p.isExplicit) builder.append("{")
                        builder.append(if (newVars.contains(p)) p.name else "_")
                        if (!p.isExplicit) builder.append("}")
                        builder.toString()
                    }
                }
            }

            if (clausesListPsi != null) {
                println("Clause: ${clause.text}")
                println("patternPrimers: ")
                for (pp in patternPrimers) {
                    println("${pp.key} -> ${pp.value.text}")
                }

                for (tp in typecheckedParams) {//Fixme: we should also search within
                    val substitutedPattern = substs[tp]
                    val abstractPattern = patternPrimers[tp]
                    if (substitutedPattern != null && substitutedPattern !is BindingPattern && abstractPattern != null) {
                        val substitutions = HashMap<PsiElement, ExpressionPattern>()
                        val unifyOk = unify(substitutedPattern, abstractPattern, substitutions)
                        if (unifyOk) {

                        }
                    }
                }
            }

            print("cons: ${constructorTypechecked.name}; ")
            for (s in substs.entries) print("${s.key.name}->${s.value.toExpression()}; ")
            println()
        } else { // case
            println("caseExpressions: ${error.caseExpressions}")
        }
    }

    companion object {
        fun unify(pattern: ExpressionPattern, abstractPattern: ArendPattern, map: HashMap<PsiElement, ExpressionPattern>): Boolean {
            println("Unify ${pattern.toExpression()}/${pattern} against ${abstractPattern.text}/${abstractPattern}")
            return false
        }

        fun doInsertPrimers(psiFactory: ArendPsiFactory,
                            clause: ArendClause,
                            typecheckedParams: List<DependentLink>,
                            eliminatedParams: List<Variable>,
                            eliminatedVars: HashSet<Variable>,
                            clauseMap: HashMap<Variable, ArendPattern>?,
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
                    if (clauseMap != null) clauseMap[param] = anchor as ArendPattern
                    if (!commaInserted) clause.addAfter(comma, anchor)
                } else {
                    anchor = patternsMap[param]
                }
            }
        }
    }
}