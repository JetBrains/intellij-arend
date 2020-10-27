package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.ext.core.body.CorePattern
import org.arend.ext.core.context.CoreBinding
import org.arend.ext.core.context.CoreParameter
import org.arend.ext.core.definition.CoreDefinition
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.*
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.ext.error.MissingClausesError
import org.arend.psi.ext.ArendFunctionalBody
import org.arend.psi.ext.ArendFunctionalDefinition
import kotlin.math.abs

class ImplementMissingClausesQuickFix(private val missingClausesError: MissingClausesError,
                                      private val causeRef: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    private val clauses = ArrayList<ArendClause>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causeRef.element != null

    override fun getText(): String = "Implement missing clauses"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        val element = causeRef.element ?: return
        clauses.clear()
        val definedVariables: List<Variable> = collectDefinedVariables(element)

        missingClausesError.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        for (clause in missingClausesError.limitedMissingClauses.reversed()) if (clause != null) {
            val filters = HashMap<CorePattern, List<Boolean>>()
            val previewResults = ArrayList<PatternKind>()
            val recursiveTypeUsagesInBindings = ArrayList<Int>()
            val elimMode = missingClausesError.isElim

            run {
                var parameter: CoreParameter? = if (!elimMode) missingClausesError.parameters else null
                val iterator = clause.iterator()
                var i = 0
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val recTypeUsagesInPattern = HashSet<CorePattern>()
                    val sampleParameter = if (elimMode) missingClausesError.eliminatedParameters[i] else parameter?.binding
                    previewResults.add(previewPattern(pattern, filters, if (parameter == null || parameter.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES, recTypeUsagesInPattern, (sampleParameter?.typeExpr as? DefCallExpression)?.definition))
                    recursiveTypeUsagesInBindings.add(recTypeUsagesInPattern.size)
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
                    i++
                }
            }

            val topLevelFilter = computeFilter(previewResults)

            val patternStrings = ArrayList<String>()
            var containsEmptyPattern = false
            run {
                val iterator = clause.iterator()
                val recursiveTypeUsagesInBindingsIterator = recursiveTypeUsagesInBindings.iterator()
                var parameter2: CoreParameter? = if (!elimMode) missingClausesError.parameters else null
                val clauseBindings: MutableList<Variable> = definedVariables.toMutableList()
                val eliminatedBindings = HashSet<CoreBinding>()
                var i = 0
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val nRecursiveBindings = recursiveTypeUsagesInBindingsIterator.next()
                    val braces = if (parameter2 == null || parameter2.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES
                    val sampleParameter = if (elimMode) missingClausesError.eliminatedParameters[i] else parameter2!!.binding
                    val patternData = doTransformPattern(pattern, element, project, filters, braces, clauseBindings, sampleParameter, nRecursiveBindings, eliminatedBindings, missingClausesError)
                    patternStrings.add(patternData.first)
                    containsEmptyPattern = containsEmptyPattern || patternData.second
                    parameter2 = if (parameter2 != null && parameter2.hasNext()) parameter2.next else null
                    i++
                }
            }

            clauses.add(psiFactory.createClause(concat(patternStrings, topLevelFilter), containsEmptyPattern))
        }

        insertClauses(psiFactory, element, clauses)
    }

    companion object {
        enum class Braces { NONE, PARENTHESES, BRACES }
        enum class PatternKind { IMPLICIT_ARG, IMPLICIT_EXPR, EXPLICIT }

        private fun insertPairOfBraces(psiFactory: ArendPsiFactory, anchor: PsiElement) {
            val braces = psiFactory.createPairOfBraces()
            anchor.parent.addAfter(braces.second, anchor)
            anchor.parent.addAfter(braces.first, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace(" "), anchor)
        }

        fun insertClauses(psiFactory: ArendPsiFactory, cause: ArendCompositeElement, clauses: List<ArendClause>) {
            var primerClause: ArendClause? = null// a "primer" clause which is needed only to simplify insertion of proper clauses

            fun doInsertClauses(cause: ArendFunctionalDefinition, fBody: ArendFunctionalBody?): PsiElement? {
                val fClauses = when (fBody) {
                    is ArendFunctionBody -> fBody.functionClauses
                    is ArendInstanceBody -> fBody.functionClauses
                    else -> null
                }

                if (fBody != null) {
                    val lastChild = fBody.lastChild
                    if (fClauses != null) {
                        return fClauses.clauseList.lastOrNull() ?: fClauses.lbrace
                        ?: fClauses.lastChild /* the last branch is meaningless */
                    } else {
                        val newClauses = psiFactory.createFunctionClauses()
                        val insertedClauses = lastChild.parent?.addAfter(newClauses, lastChild) as ArendFunctionClauses
                        lastChild.parent?.addAfter(psiFactory.createWhitespace(" "), lastChild)
                        primerClause = insertedClauses.lastChild as? ArendClause
                        return primerClause
                    }
                } else {
                    val newBody = psiFactory.createFunctionClauses(fBody is ArendInstanceBody).parent as ArendFunctionalBody
                    val lastChild = cause.lastChild
                    cause.addAfter(newBody, lastChild)
                    cause.addAfter(psiFactory.createWhitespace(" "), lastChild)
                    val newFunctionClauses = when (cause) {
                        is ArendDefInstance -> cause.instanceBody!!.functionClauses //legal to use; body was create in the above piece of code
                        is ArendDefFunction -> cause.functionBody!!.functionClauses
                        else -> null
                    }
                    primerClause = newFunctionClauses?.lastChild as? ArendClause
                    return primerClause
                }
            }

            val anchor = when (cause) {
                is ArendDefInstance -> {
                    doInsertClauses(cause, cause.instanceBody)
                }
                is ArendDefFunction -> {
                    doInsertClauses(cause, cause.functionBody)
                }
                is ArendCaseExpr -> {
                    val body = cause.withBody
                    val caseExprAnchor = cause.returnExpr ?: cause.caseArgList.lastOrNull()
                    body?.clauseList?.lastOrNull() ?: (body?.lbrace ?: (
                            body?.let {
                                insertPairOfBraces(psiFactory, it.withKw)
                                body.lbrace
                            } ?: (cause.addAfter(psiFactory.createWithBody(), caseExprAnchor) as ArendWithBody).lbrace))
                }
                is ArendCoClauseDef -> {
                    val coClauseBody = cause.coClauseBody
                            ?: (cause.addAfterWithNotification(psiFactory.createCoClauseBody(), cause.lastChild) as ArendCoClauseBody)
                    val elim = coClauseBody.elim
                            ?: coClauseBody.addWithNotification(psiFactory.createCoClauseBody().childOfType<ArendElim>()!!)
                    if (coClauseBody.lbrace == null)
                        insertPairOfBraces(psiFactory, elim)

                    coClauseBody.clauseList.lastOrNull() ?: coClauseBody.lbrace ?: cause.lastChild
                }
                is ArendLongName -> {
                    val newExpr = ((cause.parent as? ArendLongNameExpr
                            ?: cause.ancestor<ArendAtomFieldsAcc>())?.parent as? ArendArgumentAppExpr)?.ancestor<ArendNewExpr>()
                    findWithBodyAnchor(newExpr, psiFactory)
                }
                is ArendWithBody -> findWithBodyAnchor(cause.parent as? ArendNewExpr, psiFactory)
                else -> null
            }
            val anchorParent = anchor?.parent
            for (clause in clauses) if (anchorParent != null) {
                val pipe = clause.findPrevSibling()
                var currAnchor: PsiElement? = null
                if (pipe != null) currAnchor = anchorParent.addAfter(pipe, anchor)
                anchorParent.addBefore(psiFactory.createWhitespace("\n"), currAnchor)
                currAnchor = anchorParent.addAfterWithNotification(clause, currAnchor)
                anchorParent.addBefore(psiFactory.createWhitespace(" "), currAnchor)
            }

            primerClause?.let {
                it.findPrevSibling()?.delete()
                it.delete()
            }
        }

        private fun findWithBodyAnchor(newExpr: ArendNewExpr?, psiFactory: ArendPsiFactory): PsiElement? {
            if (newExpr == null) return null
            val body = newExpr.withBody
            return body?.clauseList?.lastOrNull() ?: body?.lbrace ?: body?.let {
                insertPairOfBraces(psiFactory, it.withKw)
                body.lbrace
            } ?: (newExpr.add(psiFactory.createWithBody()) as? ArendWithBody)?.lbrace
        }

        private fun computeFilter(input: List<PatternKind>): List<Boolean> {
            val result = ArrayList<Boolean>()
            var doNotSkipPatterns = false
            for (previewResult in input.reversed()) {
                when (previewResult) {
                    Companion.PatternKind.IMPLICIT_ARG -> {
                        result.add(0, doNotSkipPatterns)
                    }
                    Companion.PatternKind.IMPLICIT_EXPR -> {
                        result.add(0, true); doNotSkipPatterns = true
                    }
                    Companion.PatternKind.EXPLICIT -> {
                        result.add(0, true); doNotSkipPatterns = false
                    }
                }
            }
            return result
        }

        fun concat(input: List<String>, filter: List<Boolean>?, separator: String = ", "): String = buildString {
            val filteredInput = if (filter == null) input else input.filterIndexed { index, _ -> filter[index] }
            val iterator = filteredInput.iterator()
            while (iterator.hasNext()) {
                append(iterator.next())
                if (iterator.hasNext()) append(separator)
            }
        }

        fun previewPattern(pattern: CorePattern,
                           filters: MutableMap<CorePattern, List<Boolean>>,
                           paren: Braces,
                           recursiveTypeUsages: MutableSet<CorePattern>,
                           recursiveTypeDefinition: Definition?): PatternKind {
            if (!pattern.isAbsurd) {
                val binding = pattern.binding
                if (binding != null) {
                    val bindingType = binding.typeExpr
                    if (recursiveTypeDefinition != null && bindingType is DefCallExpression && bindingType.definition == recursiveTypeDefinition && binding.name == null) {
                        recursiveTypeUsages.add(pattern)
                    }
                    return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_ARG else Companion.PatternKind.EXPLICIT
                } else {
                    val definition: CoreDefinition? = pattern.constructor
                    val previewResults = ArrayList<PatternKind>()

                    val patternIterator = pattern.subPatterns.iterator()
                    var constructorArgument: CoreParameter? = definition?.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        previewResults.add(previewPattern(argumentPattern, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PARENTHESES else Companion.Braces.BRACES, recursiveTypeUsages, recursiveTypeDefinition))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    filters[pattern] = computeFilter(previewResults)
                }
            }

            return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_EXPR else Companion.PatternKind.EXPLICIT
        }

        private fun getIntegralNumber(pattern: CorePattern): Int? {
            val definition = pattern.constructor
            val isSuc = definition == Prelude.SUC
            val isPos = definition == Prelude.POS
            val isNeg = definition == Prelude.NEG
            if (isSuc || isPos || isNeg) {
                val number = getIntegralNumber(pattern.subPatterns.firstOrNull() ?: return null)
                if (isSuc && number != null) return number + 1
                if (isPos && number != null) return number
                if (isNeg && number != null && number != 0) return -number
                return null
            } else if (definition == Prelude.ZERO) return 0
            return null
        }

        fun doTransformPattern(pattern: CorePattern, cause: ArendCompositeElement, project: Project,
                               filters: Map<CorePattern, List<Boolean>>, paren: Braces,
                               occupiedNames: MutableList<Variable>,
                               sampleParameter: CoreBinding,
                               nRecursiveBindings: Int,
                               eliminatedBindings: MutableSet<CoreBinding>,
                               missingClausesError: MissingClausesError): Pair<String, Boolean> {
            var containsEmptyPattern = false

            val parameterName: String? = sampleParameter.name
            val recursiveTypeDefinition: Definition? = (sampleParameter.typeExpr as? DefCallExpression)?.definition

            fun getFreshName(binding: CoreBinding): String {
                val renamer = StringRenamer()
                if (recursiveTypeDefinition != null) renamer.setParameterName(recursiveTypeDefinition, parameterName)
                return renamer.generateFreshName(binding, calculateOccupiedNames(occupiedNames, parameterName, nRecursiveBindings))
            }

            val result = if (pattern.isAbsurd) {
                containsEmptyPattern = true
                "()"
            } else {
                val binding = pattern.binding
                if (binding != null) {
                    val result = getFreshName(binding)
                    occupiedNames.add(VariableImpl(result))
                    result
                } else {
                    eliminatedBindings.add(sampleParameter)
                    val definition = pattern.constructor as? Definition
                    val referable = if (definition != null) PsiLocatedReferable.fromReferable(definition.referable) else null
                    val integralNumber = getIntegralNumber(pattern)
                    val patternMatchingOnIdp = if (missingClausesError.generateIdpPatterns) {
                        admitsPatternMatchingOnIdp(sampleParameter.typeExpr, if (cause is ArendCaseExpr) missingClausesError.parameters else null, eliminatedBindings)
                    } else PatternMatchingOnIdpResult.INAPPLICABLE
                    if (patternMatchingOnIdp != PatternMatchingOnIdpResult.INAPPLICABLE) {
                        if (patternMatchingOnIdp == PatternMatchingOnIdpResult.IDP)
                            getCorrectPreludeItemStringReference(project, cause, Prelude.IDP)
                        else getFreshName(sampleParameter)
                    } else if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE) {
                        integralNumber.toString()
                    } else {
                        val tupleMode = definition == null
                        val argumentPatterns = ArrayList<String>()
                        run {
                            val patternIterator = pattern.subPatterns.iterator()
                            var constructorArgument: DependentLink? = definition?.parameters

                            while (patternIterator.hasNext()) {
                                val argumentPattern = patternIterator.next()
                                val argumentParen = when {
                                    tupleMode -> Companion.Braces.NONE
                                    constructorArgument == null || constructorArgument.isExplicit -> Companion.Braces.PARENTHESES
                                    else -> Companion.Braces.BRACES
                                }
                                val argPattern = doTransformPattern(argumentPattern, cause, project, filters, argumentParen, occupiedNames, sampleParameter, nRecursiveBindings, eliminatedBindings, missingClausesError)
                                argumentPatterns.add(argPattern.first)
                                containsEmptyPattern = containsEmptyPattern || argPattern.second
                                constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                            }
                        }

                        val filter = filters[pattern]
                        val arguments = concat(argumentPatterns, filter, if (tupleMode) "," else " ")
                        val result = buildString {
                            val defCall = if (referable != null) getTargetName(referable, cause)
                                    ?: referable.name else definition?.name
                            if (tupleMode) append("(") else {
                                append(defCall)
                                if (arguments.isNotEmpty()) append(" ")
                            }
                            append(arguments)
                            if (tupleMode) append(")")
                        }

                        if (paren == Companion.Braces.PARENTHESES && arguments.isNotEmpty()) "($result)" else result
                    }
                }
            }

            return Pair(if (paren == Companion.Braces.BRACES) "{$result}" else result, containsEmptyPattern)
        }


    }

}