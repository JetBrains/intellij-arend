package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.core.context.binding.Variable
import org.arend.core.context.param.DependentLink
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.Pattern
import org.arend.naming.renamer.Renamer
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.LocationData
import org.arend.refactoring.computeAliases
import org.arend.refactoring.getDataTypeStartingCharacter
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.MissingClausesError
import org.arend.util.LongName
import kotlin.math.abs

class ImplementMissingClausesQuickFix(private val missingClausesError: MissingClausesError, private val cause: ArendCompositeElement) : IntentionAction {
    private val clauses = ArrayList<ArendClause>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun getText(): String = "Implement missing clauses"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        clauses.clear()

        missingClausesError.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        for (clause in missingClausesError.limitedMissingClauses.reversed()) if (clause != null) {
            val filters = HashMap<ConstructorPattern, List<Boolean>>()
            val previewResults = ArrayList<Pair<PatternKind, List<Variable>>>()
            run {
                var parameter: DependentLink? = if (!missingClausesError.isElim) missingClausesError.parameters else null
                val iterator = clause.iterator()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    previewResults.add(previewPattern(pattern, filters, if (parameter == null || parameter.isExplicit) Companion.Braces.NO else Companion.Braces.BRACES))
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
                }
            }

            val topLevelFilter = computeFilter(previewResults.map { it.first })
            val resultingBindings = mergePreviewResults(topLevelFilter, previewResults.map { it.second }).reversed()
            val renamer = StringRenamer()
            renamer.generateFreshNames(resultingBindings)

            val patternStrings = ArrayList<String>()
            run {
                val iterator = clause.iterator()
                var parameter2: DependentLink? = if (!missingClausesError.isElim) missingClausesError.parameters else null
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val braces = if (parameter2 == null || parameter2.isExplicit) Companion.Braces.NO else Companion.Braces.BRACES
                    patternStrings.add(doTransformPattern(pattern, cause, editor, renamer, filters, braces, resultingBindings))
                    parameter2 = if (parameter2 != null && parameter2.hasNext()) parameter2.next else null
                }
            }

            clauses.add(psiFactory.createClause(concat(patternStrings, topLevelFilter)))
        }

        insertClauses(psiFactory, cause, clauses)
    }

    companion object {
        enum class Braces { NO, PAREN, BRACES }
        enum class PatternKind { IMPLICIT_ARG, IMPLICIT_EXPR, EXPLICIT }

        fun insertClauses(psiFactory: ArendPsiFactory, cause: ArendCompositeElement, clauses: List<ArendClause>) {
            var sampleClause: ArendClause? = null
            val anchor = when (cause) {
                is ArendDefFunction -> {
                    val fBody = cause.functionBody
                    if (fBody != null) {
                        val fClauses = fBody.functionClauses
                        if (fClauses != null) {
                            fClauses.lastChild
                        } else {
                            val newClauses = psiFactory.createFunctionClauses()
                            val lastChild = fBody.lastChild
                            lastChild.parent?.addAfter(newClauses, lastChild)
                            lastChild.parent?.addAfter(psiFactory.createWhitespace(" "), lastChild)
                            sampleClause = fBody.functionClauses!!.lastChild as ArendClause
                            sampleClause
                        }
                    } else {
                        val newBody = psiFactory.createFunctionClauses().parent as ArendFunctionBody
                        val lastChild = cause.lastChild
                        cause.addAfter(newBody, lastChild)
                        cause.addAfter(psiFactory.createWhitespace(" "), lastChild)
                        sampleClause = cause.functionBody!!.functionClauses!!.lastChild as ArendClause
                        sampleClause
                    }
                }
                is ArendCaseExpr -> cause.clauseList.lastOrNull() ?: cause.lbrace
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

            if (sampleClause != null) {
                sampleClause.findPrevSibling()?.delete()
                sampleClause.delete()
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

        private fun mergePreviewResults(filter: List<Boolean>, previewResults: List<List<Variable>>): List<Variable> {
            check(filter.size == previewResults.size)

            val filteredIterator = filter.iterator()
            val previewResultsIterator = previewResults.iterator()
            val result = ArrayList<Variable>()
            while (previewResultsIterator.hasNext()) {
                val set = previewResultsIterator.next()
                if (filteredIterator.next()) result.addAll(set)
            }

            return result
        }

        fun concat(input: List<String>, filter: List<Boolean>?, separator: String = ", "): String = buildString {
            val filteredInput = if (filter == null) input else input.filterIndexed { index, _ ->  filter[index] }
            val iterator = filteredInput.iterator()
            while (iterator.hasNext()) {
                append(iterator.next())
                if (iterator.hasNext()) append(separator)
            }
        }

        fun previewPattern(pattern: Pattern, filters: MutableMap<ConstructorPattern, List<Boolean>>, paren: Braces): Pair<PatternKind, List<Variable>> {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition = pattern.definition
                    val previewResults = ArrayList<Pair<PatternKind, List<Variable>>>()

                    val patternIterator = pattern.patterns.patternList.iterator()
                    var constructorArgument: DependentLink? = definition.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        previewResults.add(previewPattern(argumentPattern, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PAREN else Companion.Braces.BRACES))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    val resultingBindings : List<Variable>
                    if (definition != null) {
                        val filter = computeFilter(previewResults.map { it.first })
                        filters[pattern] = filter
                        resultingBindings = mergePreviewResults(filter, previewResults.map { it.second })
                    } else throw IllegalStateException()

                    return Pair(if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_EXPR else Companion.PatternKind.EXPLICIT, resultingBindings)
                }

                is BindingPattern -> {
                    val resultingBindings = ArrayList<Variable>()
                    val name = pattern.binding.name ?: "_"
                    if (name != "_") resultingBindings.add(pattern.binding)

                    return if (paren == Companion.Braces.BRACES)
                        Pair(Companion.PatternKind.IMPLICIT_ARG, resultingBindings) else
                        Pair(Companion.PatternKind.EXPLICIT, resultingBindings)
                }
            }

            throw IllegalStateException()
        }

        private fun getIntegralNumber(pattern: ConstructorPattern): Int? {
            val isSuc = pattern.definition == Prelude.SUC
            val isPos = pattern.definition == Prelude.POS
            val isNeg = pattern.definition == Prelude.NEG
            if (isSuc || isPos || isNeg) {
                val argumentList = pattern.patterns.patternList
                if (argumentList.size != 1) return null
                val firstArgument = argumentList.first() as? ConstructorPattern ?: return null
                val number = getIntegralNumber(firstArgument)
                if (isSuc && number != null) return number + 1
                if (isPos && number != null) return number
                if (isNeg && number != null && number != 0) return -number
                return null
            } else if (pattern.definition == Prelude.ZERO) return 0
            return null
        }

        fun doTransformPattern(pattern: Pattern, cause: ArendCompositeElement, editor: Editor?,
                               renamer: StringRenamer, filters: Map<ConstructorPattern, List<Boolean>>, paren: Braces, bindings: List<Variable>): String {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition = pattern.definition!!
                    val integralNumber = getIntegralNumber(pattern)
                    if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE) {
                        val result = integralNumber.toString()
                        return if (paren == Companion.Braces.BRACES) "{$result}" else result
                    }
                    val argumentPatterns = ArrayList<String>()

                    val patternIterator = pattern.patterns.patternList.iterator()
                    var constructorArgument: DependentLink? = definition.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        argumentPatterns.add(doTransformPattern(argumentPattern, cause, editor, renamer, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PAREN else Companion.Braces.BRACES, bindings))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    val filter = filters[pattern]
                    val referable = PsiLocatedReferable.fromReferable(definition.referable)

                    val arguments = concat(argumentPatterns, filter, " ")
                    val result = buildString {
                        append(if (referable != null) { getTargetName(referable, cause) ?: referable.name } else definition.name)
                        if (arguments.isNotEmpty()) append(" ")
                        append(arguments)
                    }

                    val returnString = if (paren == Companion.Braces.PAREN && arguments.isNotEmpty()) "($result)" else result
                    return if (paren == Companion.Braces.BRACES) "{$returnString}" else returnString
                }

                is BindingPattern -> {
                    val binding = pattern.binding
                    val result = renamer.getNewName(binding) ?: {
                        renamer.setUnnamed(getDataTypeStartingCharacter(binding.type)?.toString() ?: Renamer.UNNAMED)
                        renamer.generateFreshName(binding, bindings)
                    }.invoke()

                    return if (paren == Companion.Braces.BRACES) "{$result}" else result
                }
            }

            throw IllegalStateException()
        }

    }

}