package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.arend.core.context.binding.Variable
import org.arend.core.context.param.DependentLink
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.typechecking.error.local.MissingClausesError
import org.arend.core.pattern.Pattern
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable

class ImplementMissingClausesQuickFix(private val missingClausesError: MissingClausesError, private val cause: ArendCompositeElement) : IntentionAction {
    private val clauses = ArrayList<ArendClause>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun getText(): String = "Implement missing clauses"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        clauses.clear()

        for (clause in missingClausesError.missingClauses) {
            val filters = HashMap<ConstructorPattern, List<Boolean>>()
            val previewResults = ArrayList<Pair<PatternKind, List<Variable>>>()
            run {
                var parameter: DependentLink? = if (!missingClausesError.isCase && !missingClausesError.isElim) missingClausesError.parameters else null
                val iterator = clause.iterator()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    previewResults.add(previewPattern(pattern, filters, if (parameter == null || parameter.isExplicit) Companion.Braces.NO else Companion.Braces.BRACES))
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
                }
            }

            val topLevelFilter = computeFilter(previewResults.map { it.first })
            val resultingBindings = mergePreviewResults(topLevelFilter, previewResults.map { it.second })
            val renamer = StringRenamer()
            renamer.generateFreshNames(resultingBindings.reversed())

            val patternStrings = ArrayList<String>()
            run {
                val iterator = clause.iterator()
                var parameter2: DependentLink? = if (!missingClausesError.isCase && !missingClausesError.isElim) missingClausesError.parameters else null
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    patternStrings.add(doTransformPattern(pattern, renamer, filters, if (parameter2 == null || parameter2.isExplicit) Companion.Braces.NO else Companion.Braces.BRACES))
                    parameter2 = if (parameter2 != null && parameter2.hasNext()) parameter2.next else null
                }
            }

            clauses.add(psiFactory.createClause(concat(patternStrings, topLevelFilter)))
        }

        val anchor = when (cause) {
            is ArendDefFunction -> cause.functionBody?.functionClauses?.lastChild
            is ArendCaseExpr -> cause.clauseList.lastOrNull() ?: cause.lbrace
            else -> null
        }

        if (anchor != null) insertClauses(psiFactory, anchor, clauses)
    }

    companion object {
        enum class Braces { NO, PAREN, BRACES }
        enum class PatternKind { IMPLICIT_ARG, IMPLICIT_EXPR, EXPLICIT }

        fun insertClauses(psiFactory: ArendPsiFactory, anchor: PsiElement, clauses: List<ArendClause>) {
            val anchorParent = anchor.parent
            if (anchorParent != null) for (clause in clauses) {
                val pipe = clause.findPrevSibling()
                var currAnchor: PsiElement? = null
                if (pipe != null) currAnchor = anchorParent.addAfter(pipe, anchor)
                anchorParent.addBefore(psiFactory.createWhitespace("\n"), currAnchor)
                currAnchor = anchorParent.addAfterWithNotification(clause, currAnchor)
                anchorParent.addBefore(psiFactory.createWhitespace(" "), currAnchor)
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
            if (filter.size != previewResults.size)
                throw IllegalStateException()

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
                    if (definition == null) {
                        resultingBindings = ArrayList()
                        for (previewResult in previewResults) resultingBindings.addAll(previewResult.second)
                    } else {
                        val filter = computeFilter(previewResults.map { it.first })
                        filters[pattern] = filter
                        resultingBindings = mergePreviewResults(filter, previewResults.map { it.second })
                    }

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

        fun doTransformPattern(pattern: Pattern, renamer: StringRenamer, filters: Map<ConstructorPattern, List<Boolean>>, paren: Braces): String {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition = pattern.definition
                    val argumentPatterns = ArrayList<String>()

                    val patternIterator = pattern.patterns.patternList.iterator()
                    var constructorArgument: DependentLink? = definition.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        argumentPatterns.add(doTransformPattern(argumentPattern, renamer, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PAREN else Companion.Braces.BRACES))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    val returnString = if (definition == null) {
                        buildString {
                            append("(")
                            append(concat(argumentPatterns, null))
                            append(")")
                        }
                    } else {
                        val filter = filters[pattern]
                        val referable = PsiLocatedReferable.fromReferable(definition.referable)
                        val result = buildString {
                            append(if (referable != null) referable.name else definition.name)
                            val arguments = concat(argumentPatterns, filter, " ")
                            if (arguments.isNotEmpty()) append(" ")
                            append(arguments)
                        }
                        if (paren == Companion.Braces.PAREN) "($result)" else result
                    }

                    return if (paren == Companion.Braces.BRACES) "{$returnString}" else returnString
                }

                is BindingPattern -> {
                    val name = pattern.binding.name ?: "_"
                    val result = if (name != "_") renamer.getNewName(pattern.binding) else name
                    return if (paren == Companion.Braces.BRACES) "{$result}" else result
                }
            }

            throw IllegalStateException()
        }

    }

}