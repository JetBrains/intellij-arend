package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Variable
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorExpressionPattern
import org.arend.core.pattern.EmptyPattern
import org.arend.core.pattern.ExpressionPattern
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.VariableImpl
import org.arend.refactoring.calculateOccupiedNames
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.MissingClausesError
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

        missingClausesError.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        for (clause in missingClausesError.limitedMissingClauses.reversed()) if (clause != null) {
            val filters = HashMap<ConstructorExpressionPattern, List<Boolean>>()
            val previewResults = ArrayList<PatternKind>()
            val recursiveTypeUsagesInBindings = ArrayList<Int>()
            run {
                var parameter: DependentLink? = if (!missingClausesError.isElim) missingClausesError.parameters else null
                val iterator = clause.iterator()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val recTypeUsagesInPattern = HashSet<BindingPattern>()
                    previewResults.add(previewPattern(pattern, filters, if (parameter == null || parameter.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES, recTypeUsagesInPattern, (parameter?.type as? DefCallExpression)?.definition))
                    recursiveTypeUsagesInBindings.add(recTypeUsagesInPattern.size)
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
                }
            }

            val topLevelFilter = computeFilter(previewResults)

            val patternStrings = ArrayList<String>()
            var containsEmptyPattern = false
            run {
                val iterator = clause.iterator()
                val recursiveTypeUsagesInBindingsIterator = recursiveTypeUsagesInBindings.iterator()
                var parameter2: DependentLink? = if (!missingClausesError.isElim) missingClausesError.parameters else null
                val clauseBindings = ArrayList<Variable>()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val nRecursiveBindings = recursiveTypeUsagesInBindingsIterator.next()
                    val braces = if (parameter2 == null || parameter2.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES
                    val patternData = doTransformPattern(pattern, element, editor, filters, braces, clauseBindings, parameter2?.name, (parameter2?.type as? DefCallExpression)?.definition, nRecursiveBindings)
                    patternStrings.add(patternData.first)
                    containsEmptyPattern = containsEmptyPattern || patternData.second
                    parameter2 = if (parameter2 != null && parameter2.hasNext()) parameter2.next else null
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
            var primerClause: ArendClause? = null // a "primer" clause which is needed only to simplify insertion of proper clauses
            val anchor = when (cause) {
                is ArendDefFunction -> {
                    val fBody = cause.functionBody
                    if (fBody != null) {
                        val fClauses = fBody.functionClauses
                        val lastChild = fBody.lastChild
                        if (fClauses != null) {
                            fClauses.clauseList.lastOrNull() ?: fClauses.lbrace
                            ?: fClauses.lastChild /* the last branch is meaningless */
                        } else {
                            val newClauses = psiFactory.createFunctionClauses()
                            val insertedClauses = lastChild.parent?.addAfter(newClauses, lastChild) as ArendFunctionClauses
                            lastChild.parent?.addAfter(psiFactory.createWhitespace(" "), lastChild)
                            primerClause = insertedClauses.lastChild as? ArendClause
                            primerClause
                        }
                    } else {
                        val newBody = psiFactory.createFunctionClauses().parent as ArendFunctionBody
                        val lastChild = cause.lastChild
                        cause.addAfter(newBody, lastChild)
                        cause.addAfter(psiFactory.createWhitespace(" "), lastChild)
                        primerClause = cause.functionBody!!.functionClauses!!.lastChild as ArendClause
                        primerClause
                    }
                }
                is ArendCaseExpr -> {
                    cause.clauseList.lastOrNull() ?: (cause.lbrace ?: (cause.withKw ?: (cause.returnExpr
                            ?: cause.caseArgList.lastOrNull())?.let { withAnchor ->
                        cause.addAfterWithNotification((psiFactory.createExpression("\\case 0 \\with") as ArendCaseExpr).withKw!!,
                                cause.addAfter(psiFactory.createWhitespace(" "), withAnchor))
                    })?.let {
                        insertPairOfBraces(psiFactory, it)
                        cause.lbrace
                    })
                }
                is ArendCoClauseDef -> {
                    val elim = cause.elim ?: run {
                        val withKw = cause.addAfter(psiFactory.createWith(), cause.lastChild)
                        cause.addBefore(psiFactory.createWhitespace(" "), withKw)
                        withKw
                    }
                    if (cause.lbrace == null)
                        insertPairOfBraces(psiFactory, elim)

                    cause.clauseList.lastOrNull() ?: cause.lbrace ?: cause.lastChild
                }
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

            if (primerClause != null) {
                primerClause.findPrevSibling()?.delete()
                primerClause.delete()
            }

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

        private fun previewPattern(pattern: ExpressionPattern,
                                   filters: MutableMap<ConstructorExpressionPattern, List<Boolean>>,
                                   paren: Braces,
                                   recursiveTypeUsages: MutableSet<BindingPattern>,
                                   recursiveTypeDefinition: Definition?): PatternKind {
            when (pattern) {
                is ConstructorExpressionPattern -> {
                    val definition: Definition? = pattern.definition
                    val previewResults = ArrayList<PatternKind>()

                    val patternIterator = pattern.subPatterns.iterator()
                    var constructorArgument: DependentLink? = definition?.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        previewResults.add(previewPattern(argumentPattern, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PARENTHESES else Companion.Braces.BRACES, recursiveTypeUsages, recursiveTypeDefinition))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    filters[pattern] = computeFilter(previewResults)
                }
                is BindingPattern -> {
                    val bindingType = pattern.binding.type
                    if (recursiveTypeDefinition != null && bindingType is DefCallExpression && bindingType.definition == recursiveTypeDefinition) {
                        recursiveTypeUsages.add(pattern)
                    }
                    return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_ARG else Companion.PatternKind.EXPLICIT
                }
                is EmptyPattern -> {
                }
                else -> throw IllegalStateException()
            }

            return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_EXPR else Companion.PatternKind.EXPLICIT
        }


        private fun getIntegralNumber(pattern: ConstructorExpressionPattern): Int? {
            val isSuc = pattern.definition == Prelude.SUC
            val isPos = pattern.definition == Prelude.POS
            val isNeg = pattern.definition == Prelude.NEG
            if (isSuc || isPos || isNeg) {
                val argumentList = pattern.subPatterns
                if (argumentList.size != 1) return null
                val firstArgument = argumentList.first() as? ConstructorExpressionPattern
                        ?: return null
                val number = getIntegralNumber(firstArgument)
                if (isSuc && number != null) return number + 1
                if (isPos && number != null) return number
                if (isNeg && number != null && number != 0) return -number
                return null
            } else if (pattern.definition == Prelude.ZERO) return 0
            return null
        }

        fun doTransformPattern(pattern: ExpressionPattern, cause: ArendCompositeElement, editor: Editor?,
                               filters: Map<ConstructorExpressionPattern, List<Boolean>>, paren: Braces,
                               occupiedNames: MutableList<Variable>,
                               parameterName: String?,
                               recursiveTypeDefinition: Definition?,
                               nRecursiveBindings: Int): Pair<String, Boolean> {
            var containsEmptyPattern = false
            val result = when (pattern) {
                is ConstructorExpressionPattern -> {
                    val definition: Definition? = pattern.definition
                    val referable = if (definition != null) PsiLocatedReferable.fromReferable(definition.referable) else null
                    val integralNumber = getIntegralNumber(pattern)

                    if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE) {
                        integralNumber.toString()
                    } else {
                        val tupleMode = definition == null || definition is ClassDefinition && definition.isRecord
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
                                val argPattern = doTransformPattern(argumentPattern, cause, editor, filters, argumentParen, occupiedNames, parameterName, recursiveTypeDefinition, nRecursiveBindings)
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

                is BindingPattern -> {
                    val binding = pattern.binding
                    val renamer = StringRenamer()
                    if (recursiveTypeDefinition != null && parameterName != null) renamer.setParameterName(recursiveTypeDefinition, parameterName)
                    val result = renamer.generateFreshName(binding, calculateOccupiedNames(occupiedNames, parameterName, nRecursiveBindings))
                    occupiedNames.add(VariableImpl(result))
                    result
                }

                is EmptyPattern -> {
                    containsEmptyPattern = true
                    "()"
                }
                else -> throw IllegalStateException()
            }

            return Pair(if (paren == Companion.Braces.BRACES) "{$result}" else result, containsEmptyPattern)
        }


    }

}