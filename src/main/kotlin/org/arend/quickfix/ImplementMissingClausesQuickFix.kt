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
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.Pattern
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.VariableImpl
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
            val filters = HashMap<ConstructorPattern, List<Boolean>>()
            val previewResults = ArrayList<PatternKind>()
            run {
                var parameter: DependentLink? = if (!missingClausesError.isElim) missingClausesError.parameters else null
                val iterator = clause.iterator()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    previewResults.add(previewPattern(pattern, filters, if (parameter == null || parameter.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES))
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
                }
            }

            val topLevelFilter = computeFilter(previewResults)

            val patternStrings = ArrayList<String>()
            run {
                val iterator = clause.iterator()
                var parameter2: DependentLink? = if (!missingClausesError.isElim) missingClausesError.parameters else null
                val clauseBindings = ArrayList<Variable>()
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val braces = if (parameter2 == null || parameter2.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES
                    patternStrings.add(doTransformPattern(pattern, element, editor, filters, braces, clauseBindings))
                    parameter2 = if (parameter2 != null && parameter2.hasNext()) parameter2.next else null
                }
            }

            clauses.add(psiFactory.createClause(concat(patternStrings, topLevelFilter)))
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
                            fClauses.clauseList.lastOrNull() ?: fClauses.lbrace ?: fClauses.lastChild /* the last branch is meaningless */
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

        fun previewPattern(pattern: Pattern, filters: MutableMap<ConstructorPattern, List<Boolean>>, paren: Braces): PatternKind {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition: Definition? = pattern.definition
                    val previewResults = ArrayList<PatternKind>()

                    val patternIterator = pattern.patterns.patternList.iterator()
                    var constructorArgument: DependentLink? = definition?.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        previewResults.add(previewPattern(argumentPattern, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PARENTHESES else Companion.Braces.BRACES))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    filters[pattern] = computeFilter(previewResults)
                    return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_EXPR else Companion.PatternKind.EXPLICIT
                }

                is BindingPattern -> return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_ARG else Companion.PatternKind.EXPLICIT
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
                               filters: Map<ConstructorPattern, List<Boolean>>, paren: Braces,
                               occupiedNames: MutableList<Variable>): String {
            when (pattern) {
                is ConstructorPattern -> {
                    val definition: Definition? = pattern.definition
                    val referable = if (definition != null) PsiLocatedReferable.fromReferable(definition.referable) else null

                    val integralNumber = getIntegralNumber(pattern)
                    if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE) {
                        val result = integralNumber.toString()
                        return if (paren == Companion.Braces.BRACES) "{$result}" else result
                    }
                    val tupleMode = definition == null || definition is ClassDefinition && definition.isRecord

                    val argumentPatterns = ArrayList<String>()
                    run {
                        val patternIterator = pattern.patterns.patternList.iterator()
                        var constructorArgument: DependentLink? = definition?.parameters

                        while (patternIterator.hasNext()) {
                            val argumentPattern = patternIterator.next()
                            val argumentParen = when {
                                tupleMode -> Companion.Braces.NONE
                                constructorArgument == null || constructorArgument.isExplicit -> Companion.Braces.PARENTHESES
                                else -> Companion.Braces.BRACES
                            }
                            argumentPatterns.add(doTransformPattern(argumentPattern, cause, editor, filters, argumentParen, occupiedNames))
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

                    val returnString = if (paren == Companion.Braces.PARENTHESES && arguments.isNotEmpty()) "($result)" else result
                    return if (paren == Companion.Braces.BRACES) "{$returnString}" else returnString
                }

                is BindingPattern -> {
                    val binding = pattern.binding
                    val renamer = StringRenamer()
                    val result = renamer.generateFreshName(binding, occupiedNames)
                    occupiedNames.add(VariableImpl(result))

                    return if (paren == Companion.Braces.BRACES) "{$result}" else result
                }
            }

            throw IllegalStateException()
        }

    }

}