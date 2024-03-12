package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.param.EmptyDependentLink
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.SmallIntegerExpression
import org.arend.core.pattern.ConstructorExpressionPattern
import org.arend.ext.core.body.CorePattern
import org.arend.ext.core.context.CoreBinding
import org.arend.ext.core.context.CoreParameter
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.*
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.ext.error.MissingClausesError
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ext.*
import org.arend.util.ArendBundle
import java.lang.IllegalStateException
import kotlin.math.abs

class ImplementMissingClausesQuickFix(private val missingClausesError: MissingClausesError,
                                      private val causeRef: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    private val clauses = ArrayList<ArendClause>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causeRef.element != null

    override fun getText(): String = ArendBundle.message("arend.clause.implement")

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

            val anchor = when (cause) {
                is ArendCoClauseDef -> {
                    val coClauseBody = cause.body
                            ?: (cause.addAfter(psiFactory.createCoClauseBody(), cause.lastChild) as ArendFunctionBody)
                    val elim = coClauseBody.elim
                            ?: coClauseBody.add(psiFactory.createCoClauseBody().descendantOfType<ArendElim>()!!)
                    if (coClauseBody.lbrace == null)
                        insertPairOfBraces(psiFactory, elim)

                    coClauseBody.clauseList.lastOrNull() ?: coClauseBody.lbrace ?: cause.lastChild
                }
                is ArendFunctionDefinition<*> -> {
                    val fBody = cause.body
                    val fClauses = if (fBody != null && fBody.kind != ArendFunctionBody.Kind.COCLAUSE) fBody.functionClauses else null

                    if (fBody != null) {
                        val lastChild = fBody.lastChild
                        if (fClauses != null) {
                            fClauses.clauseList.lastOrNull() ?: fClauses.lbrace ?: throw IllegalStateException()
                        } else {
                            val newClauses = psiFactory.createFunctionClauses()
                            val insertedClauses = lastChild.parent?.addAfter(newClauses, lastChild) as ArendFunctionClauses
                            lastChild.parent?.addAfter(psiFactory.createWhitespace(" "), lastChild)
                            primerClause = insertedClauses.lastChild as? ArendClause
                            primerClause
                        }
                    } else {
                        val newBody = psiFactory.createFunctionClauses().parent as ArendFunctionBody
                        val lastChild = cause.returnExpr ?: cause.parameters.lastOrNull() ?: throw IllegalStateException()
                        cause.addAfter(newBody, lastChild)
                        cause.addAfter(psiFactory.createWhitespace(" "), lastChild)
                        primerClause = cause.body?.functionClauses?.lastChild as? ArendClause
                        primerClause
                    }
                }
                is ArendCaseExpr -> {
                    val body = cause.withBody
                    val caseExprAnchor = cause.returnExpr ?: cause.caseArguments.lastOrNull()
                    body?.clauseList?.lastOrNull() ?: (body?.lbrace ?: (
                            body?.let {
                                insertPairOfBraces(psiFactory, it.withKw)
                                body.lbrace
                            } ?: (cause.addAfter(psiFactory.createWithBody(), caseExprAnchor) as ArendWithBody).lbrace))
                }
                is ArendLongName -> {
                    findWithBodyAnchor(cause.ancestor(), psiFactory)
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
                currAnchor = anchorParent.addAfter(clause, currAnchor)
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
                    val previewResults = ArrayList<PatternKind>()

                    val patternIterator = pattern.subPatterns.iterator()
                    var constructorArgument: CoreParameter = pattern.parameters ?: EmptyDependentLink.getInstance()

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        previewResults.add(previewPattern(argumentPattern, filters,
                                if (constructorArgument.isExplicit) Companion.Braces.PARENTHESES else Companion.Braces.BRACES, recursiveTypeUsages, recursiveTypeDefinition))
                        if (constructorArgument.hasNext()) constructorArgument = constructorArgument.next
                    }

                    filters[pattern] = computeFilter(previewResults)
                }
            }

            return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_EXPR else Companion.PatternKind.EXPLICIT
        }

        fun getIntegralNumber(pattern: CorePattern): Int? {
            val definition = pattern.constructor
            val isSuc = definition == Prelude.SUC
            val isFinSuc = definition == Prelude.FIN_SUC
            val isPos = definition == Prelude.POS
            val isNeg = definition == Prelude.NEG
            val firstArgument = (pattern as? ConstructorExpressionPattern)?.dataTypeArguments?.firstOrNull()
            if (isSuc || isFinSuc || isPos || isNeg) {
                val number = if (isFinSuc && (firstArgument as? SmallIntegerExpression)?.isOne == true) 0 else
                    getIntegralNumber(pattern.subPatterns.firstOrNull() ?: return null)
                if ((isSuc || isFinSuc) && number != null) return number + 1
                if (isPos && number != null) return number
                if (isNeg && number != null && number != 0) return -number
                return null
            } else if (definition == Prelude.ZERO || definition == Prelude.FIN_ZERO) return 0
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
                    val infixMode = (referable as? GlobalReferable)?.precedence?.isInfix ?: false
                    val integralNumber = getIntegralNumber(pattern)
                    val patternMatchingOnIdp = if (missingClausesError.generateIdpPatterns) {
                        admitsPatternMatchingOnIdp(sampleParameter.typeExpr, if (cause is ArendCaseExpr) missingClausesError.parameters else null, eliminatedBindings)
                    } else PatternMatchingOnIdpResult.INAPPLICABLE
                    if (patternMatchingOnIdp != PatternMatchingOnIdpResult.INAPPLICABLE) {
                        if (patternMatchingOnIdp == PatternMatchingOnIdpResult.IDP)
                            getCorrectPreludeItemStringReference(project, cause, Prelude.IDP)
                        else {
                            val fN = getFreshName(sampleParameter)
                            occupiedNames.add(VariableImpl(fN))
                            fN
                        }
                    } else if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE) {
                        integralNumber.toString()
                    } else {
                        val tupleMode = definition == null
                        val argumentPatterns = ArrayList<String>()
                        var nExplicit = 0
                        val explicitPatterns = ArrayList<String>()
                        val implicitPatterns = ArrayList<String>()

                        run {
                            val patternIterator = pattern.subPatterns.iterator()
                            var constructorArgument: CoreParameter = pattern.parameters ?: EmptyDependentLink.getInstance()

                            while (patternIterator.hasNext()) {
                                val argumentPattern = patternIterator.next()
                                val argumentParen = when {
                                    tupleMode -> Companion.Braces.NONE
                                    constructorArgument.isExplicit -> Companion.Braces.PARENTHESES
                                    else -> Companion.Braces.BRACES
                                }

                                val argPattern = doTransformPattern(argumentPattern, cause, project, filters, argumentParen, occupiedNames, sampleParameter, nRecursiveBindings, eliminatedBindings, missingClausesError)
                                argumentPatterns.add(argPattern.first)
                                if (constructorArgument.isExplicit) {
                                    nExplicit++
                                    explicitPatterns.add(argPattern.first)
                                } else
                                    implicitPatterns.add(argPattern.first)
                                containsEmptyPattern = containsEmptyPattern || argPattern.second
                                if (constructorArgument.hasNext()) constructorArgument = constructorArgument.next
                            }
                        }

                        val filter = filters[pattern]
                        val arguments = concat(argumentPatterns, filter, if (tupleMode) "," else " ")
                        val result = buildString {
                            val (defCall, namespaceCommand) = if (referable != null) getTargetName(referable, cause) else Pair(definition?.name, null)
                            namespaceCommand?.execute()
                            if (infixMode && nExplicit == 2) {
                                append(explicitPatterns[0])
                                append(" ")
                                append(defCall)
                                append(" ")
                                append(concat(implicitPatterns, filter, " "))
                                append(" ")
                                append(explicitPatterns[1])
                                return@buildString
                            }
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