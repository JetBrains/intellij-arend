package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.ext.variable.Variable
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.elimtree.ElimBody
import org.arend.core.elimtree.IntervalElim
import org.arend.core.expr.*
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorExpressionPattern
import org.arend.core.pattern.ExpressionPattern
import org.arend.core.pattern.Pattern
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.*
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import java.util.*
import java.util.Collections.singletonList

class SplitAtomPatternIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, "Split atomic pattern") {
    private var splitPatternEntries: List<SplitPatternEntry>? = null
    private var caseClauseParameters: DependentLink? = null

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor): Boolean {
        val defIdentifier = when (element) {
            is ArendPattern -> element.defIdentifier
            is ArendAtomPatternOrPrefix -> element.defIdentifier
            else -> null
        }
        val project = editor.project
        this.splitPatternEntries = null

        if (element is ArendPattern && element.atomPatternOrPrefixList.size == 0 || element is ArendAtomPatternOrPrefix) {
            val type = getElementType(element, editor)?.let{ TypeCoerceExpression.unfoldType(it) }
            this.splitPatternEntries = when (type) {
                is DataCallExpression -> {
                    val canDoPatternMatchingOnIdp = admitsPatternMatchingOnIdp(type, caseClauseParameters)
                    if (project != null && canDoPatternMatchingOnIdp == PatternMatchingOnIdpResult.IDP) {
                        singletonList(IdpPatternEntry(project))
                    } else if (canDoPatternMatchingOnIdp != PatternMatchingOnIdpResult.DO_NOT_ELIMINATE) {
                        val constructors = type.matchedConstructors ?: return false
                        constructors.map { ConstructorSplitPatternEntry(it.definition, defIdentifier?.name, type.definition) }
                    } else null
                }
                is SigmaExpression -> singletonList(TupleSplitPatternEntry(type.parameters))
                is ClassCallExpression -> {
                    val definition = type.definition
                    if (definition.isRecord) singletonList(RecordSplitPatternEntry(type, definition)) else null
                }
                else -> null
            }
        }
        return this.splitPatternEntries != null
    }

    override fun applyTo(element: PsiElement, project: Project, editor: Editor) {
        splitPatternEntries?.let { doSplitPattern(element, project, it) }
    }

    private fun getElementType(element: PsiElement, editor: Editor): Expression? {
        val project = editor.project
        caseClauseParameters = null
        if (project != null) {
            var definition: TCDefinition? = null
            val (patternOwner, indexList) = locatePattern(element) ?: return null
            val ownerParent = (patternOwner as PsiElement).parent
            var abstractPatterns: List<Abstract.Pattern>? = null

            var clauseIndex = -1
            if (patternOwner is ArendClause && ownerParent is ArendFunctionClauses) {
                val body = ownerParent.parent
                val func = body?.parent
                if (body is ArendFunctionBody && func is ArendFunctionalDefinition) {
                    abstractPatterns = patternOwner.patterns
                    definition = func as? TCDefinition
                    clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
                }
            }
            if (patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
                /* val data = ownerParent.parent
                abstractPatterns = patternOwner.patterns
                if (data is ArendDefData) definition = data */
                return null // TODO: Implement some behavior for constructor clauses as well
            }

            if (definition != null && clauseIndex != -1) {
                val typeCheckedDefinition = definition.tcReferable?.typechecked
                if (typeCheckedDefinition is FunctionDefinition && definition is Abstract.ParametersHolder && definition is Abstract.EliminatedExpressionsHolder && abstractPatterns != null) {
                    val elimBody = (typeCheckedDefinition.actualBody as? IntervalElim)?.otherwise
                            ?: (typeCheckedDefinition.actualBody as? ElimBody) ?: return null
                    val patterns = elimBody.clauses.getOrNull(clauseIndex)?.patterns?.let { Pattern.toExpressionPatterns(it, typeCheckedDefinition.parameters) }
                            ?: return null

                    val parameters = ArrayList<Referable>(); for (pp in definition.parameters) parameters.addAll(pp.referableList)
                    val elimVars = definition.eliminatedExpressions ?: emptyList()
                    val isElim = elimVars.isNotEmpty()
                    val elimVarPatterns: List<ExpressionPattern> = if (isElim) elimVars.map { reference ->
                        if (reference is ArendRefIdentifier) {
                            val parameterIndex = (reference.reference?.resolve() as? Referable)?.let { parameters.indexOf(it) }
                                    ?: -1
                            if (parameterIndex < patterns.size && parameterIndex != -1) patterns[parameterIndex] else throw IllegalStateException()
                        } else throw IllegalStateException()
                    } else patterns

                    if (indexList.size > 0) {
                        val typecheckedPattern = if (isElim) elimVarPatterns.getOrNull(indexList[0]) else findMatchingPattern(abstractPatterns, typeCheckedDefinition.parameters, patterns, indexList[0])
                        if (typecheckedPattern != null) {
                            val patternPart = findPattern(indexList.drop(1), typecheckedPattern, abstractPatterns[indexList[0]]) as? BindingPattern
                                    ?: return null
                            return patternPart.binding.typeExpr
                        }
                    }
                }
            }

            if (ownerParent is ArendWithBody && patternOwner is ArendClause) {
                val clauseIndex2 = ownerParent.clauseList.indexOf(patternOwner)
                val caseExprData = tryCorrespondedSubExpr(ownerParent.textRange, patternOwner.containingFile, project, editor, false)
                val coreCaseExpr = caseExprData?.subCore
                if (coreCaseExpr is CaseExpression) {
                    val coreClause = coreCaseExpr.elimBody.clauses.getOrNull(clauseIndex2)
                    caseClauseParameters = coreClause?.parameters
                    val bindingData = caseExprData.findBinding(element.textRange)
                    return bindingData?.second
                }
            }
        }

        return null
    }

    companion object {
        interface SplitPatternEntry {
            fun initParams(occupiedNames: MutableSet<Variable>)
            fun patternString(location: ArendCompositeElement): String
            fun expressionString(location: ArendCompositeElement): String
            fun requiresParentheses(): Boolean
        }

        abstract class DependentLinkSplitPatternEntry(private val parameterName: String?,
                                                      private val recursiveTypeDefinition: Definition?) : SplitPatternEntry {
            val params: ArrayList<String> = ArrayList()

            abstract fun getDependentLink(): DependentLink

            override fun initParams(occupiedNames: MutableSet<Variable>) {
                params.clear()
                var parameter = getDependentLink()
                var nRecursiveBindings = 0

                if (recursiveTypeDefinition != null && parameterName != null && parameterName.isNotEmpty() && !Character.isDigit(parameterName.last())) {
                    while (parameter.hasNext()) {
                        val parameterType = parameter.type
                        if (parameterType is DataCallExpression && parameterType.definition == recursiveTypeDefinition && parameter.name == null) nRecursiveBindings += 1
                        parameter = parameter.next
                    }
                    parameter = getDependentLink()
                }

                val renamer = StringRenamer()
                renamer.setParameterName(recursiveTypeDefinition, parameterName)

                while (parameter.hasNext()) {
                    val name = renamer.generateFreshName(parameter, calculateOccupiedNames(occupiedNames, parameterName, nRecursiveBindings))
                    occupiedNames.add(parameter)
                    if (parameter.isExplicit)
                        params.add(name)
                    parameter = parameter.next
                }
            }
        }

        class ConstructorSplitPatternEntry(val constructor: Definition,
                                           parameterName: String?,
                                           recursiveTypeDefinition: Definition?) : DependentLinkSplitPatternEntry(parameterName, recursiveTypeDefinition) {
            override fun getDependentLink(): DependentLink = constructor.parameters

            override fun patternString(location: ArendCompositeElement): String {
                val constructorName = getTargetName(PsiLocatedReferable.fromReferable(constructor.referable), location)
                        ?: constructor.name

                return buildString {
                    append("$constructorName ")
                    for (p in params) append("$p ")
                }.trim()
            }

            override fun expressionString(location: ArendCompositeElement): String {
                val constructorName = getTargetName(PsiLocatedReferable.fromReferable(constructor.referable), location)
                        ?: constructor.name

                return if (constructor.referable.precedence.isInfix && params.size == 2)
                    "${params[0]} $constructorName ${params[1]}" else patternString(location)
            }

            override fun requiresParentheses(): Boolean = params.isNotEmpty()
        }

        class TupleSplitPatternEntry(private val link: DependentLink) : DependentLinkSplitPatternEntry(null, null) {
            override fun getDependentLink(): DependentLink = link

            override fun patternString(location: ArendCompositeElement): String = printTuplePattern(params)

            override fun expressionString(location: ArendCompositeElement): String = patternString(location)

            override fun requiresParentheses(): Boolean = false

            companion object {
                fun printTuplePattern(params: ArrayList<String>) = buildString {
                    append("(")
                    for (p in params.withIndex()) {
                        append(p.value)
                        if (p.index < params.size - 1)
                            append(",")
                    }
                    append(")")
                }
            }
        }

        class RecordSplitPatternEntry(private val dataCall: ClassCallExpression, val record: ClassDefinition) : SplitPatternEntry {
            val params: ArrayList<String> = ArrayList()

            override fun initParams(occupiedNames: MutableSet<Variable>) {
                params.clear()
                val renamer = StringRenamer()
                renamer.setForceTypeSCName(true)

                for (field in record.fields.filter { record.isGoodField(it) }) {
                    val name = renamer.generateFreshName(field, occupiedNames)
                    occupiedNames.add(field)
                    params.add(name)
                }
            }

            override fun patternString(location: ArendCompositeElement): String = TupleSplitPatternEntry.printTuplePattern(params)

            override fun expressionString(location: ArendCompositeElement): String = buildString {
                append("\\new ")
                val recordName = getTargetName(PsiLocatedReferable.fromReferable(record.referable), location)
                        ?: record.name
                append("$recordName ")
                val expr = ToAbstractVisitor.convert(dataCall, object : PrettyPrinterConfig {
                    override fun getNormalizationMode(): NormalizationMode? {
                        return null
                    }
                })
                if (expr is Concrete.AppExpression) {
                    PrettyPrintVisitor.printArguments(PrettyPrintVisitor(this, 0), expr.arguments, false)
                    append(" ")
                }
                for (p in params) append("$p ")
            }.trim()

            override fun requiresParentheses(): Boolean = true
        }

        class IdpPatternEntry(val project: Project): SplitPatternEntry {
            override fun initParams(occupiedNames: MutableSet<Variable>) { }

            override fun patternString(location: ArendCompositeElement): String = getCorrectPreludeItemStringReference(project, location, Prelude.IDP)

            override fun expressionString(location: ArendCompositeElement): String = patternString(location)

            override fun requiresParentheses(): Boolean = false
        }

        fun doSplitPattern(element: PsiElement, project: Project, splitPatternEntries: Collection<SplitPatternEntry>, generateBody: Boolean = false) {
            val (localClause, localIndexList) = locatePattern(element) ?: return
            if (localClause !is ArendClause) return

            val localNames = HashSet<Variable>()
            localNames.addAll(findAllVariablePatterns(localClause, element).map { VariableImpl(it.name ?: "") })

            if (element is Abstract.Pattern) {
                val factory = ArendPsiFactory(project)
                if (splitPatternEntries.isEmpty()) {
                    doReplacePattern(factory, element, "()", false)
                    localClause.expr?.deleteWithNotification()
                    localClause.fatArrow?.delete()
                } else {
                    var first = true
                    if (generateBody && localClause.fatArrow == null) {
                        var currAnchor = localClause.lastChild
                        val sampleClause = factory.createClause("()")
                        currAnchor = localClause.addAfter(sampleClause.fatArrow!!, currAnchor)
                        localClause.addBefore(factory.createWhitespace(" "), currAnchor)
                        localClause.addAfterWithNotification(sampleClause.expr!!, currAnchor)
                        localClause.addAfter(factory.createWhitespace(" "), currAnchor)
                    }

                    val clauseCopy = localClause.copy()
                    val pipe: PsiElement = factory.createClause("zero").findPrevSibling()!!
                    var currAnchor: PsiElement = localClause

                    for (splitPatternEntry in splitPatternEntries) {
                        splitPatternEntry.initParams(localNames)
                        val patternString = splitPatternEntry.patternString(localClause)
                        val expressionString = splitPatternEntry.expressionString(localClause)

                        if (first) {
                            doSubstituteUsages(project, element.childOfType(), currAnchor, expressionString)

                            var inserted = false
                            if (splitPatternEntry is ConstructorSplitPatternEntry && (splitPatternEntry.constructor == Prelude.ZERO || splitPatternEntry.constructor == Prelude.FIN_ZERO)) {
                                var number = 0
                                val abstractPattern = localClause.patternList[localIndexList[0]]
                                var path = localIndexList.drop(1)
                                while (path.isNotEmpty()) {
                                    val patternPiece = findAbstractPattern(path.dropLast(1), abstractPattern)
                                    if (patternPiece !is PsiElement || !isSucPattern(patternPiece)) break
                                    path = path.dropLast(1)
                                    number += 1
                                }
                                val patternToReplace = findAbstractPattern(path, abstractPattern)
                                if (patternToReplace is PsiElement) {
                                    doReplacePattern(factory, patternToReplace, number.toString(), false)
                                    inserted = true
                                }
                            }

                            if (!inserted)
                                doReplacePattern(factory, element, patternString, splitPatternEntry.requiresParentheses())
                        } else {
                            val anchorParent = currAnchor.parent
                            currAnchor = anchorParent.addAfter(pipe, currAnchor)
                            anchorParent.addBefore(factory.createWhitespace("\n"), currAnchor)
                            currAnchor = anchorParent.addAfterWithNotification(clauseCopy, currAnchor)
                            anchorParent.addBefore(factory.createWhitespace(" "), currAnchor)

                            if (currAnchor is ArendClause) {
                                val elementCopy = findAbstractPattern(localIndexList.drop(1), currAnchor.patternList.getOrNull(localIndexList[0]))
                                if (elementCopy is PsiElement) {
                                    doSubstituteUsages(project, elementCopy.childOfType(), currAnchor, expressionString)
                                    doReplacePattern(factory, elementCopy, patternString, splitPatternEntry.requiresParentheses())
                                }
                            }
                        }

                        first = false
                    }
                }
            }
        }

        fun isSucPattern(pattern: PsiElement): Boolean {
            if (pattern !is ArendPatternImplMixin || pattern.arguments.size != 1) {
                return false
            }

            val constructor = (pattern.headReference as? UnresolvedReference)?.resolve(pattern.ancestor<ArendDefinition>()?.scope ?: return false, null) as? ArendConstructor
                    ?: return false
            return constructor.name == Prelude.SUC.name && constructor.ancestor<ArendDefData>()?.tcReferable?.typechecked == Prelude.NAT ||
                   constructor.name == Prelude.FIN_SUC.name && constructor.ancestor<ArendDefData>()?.tcReferable?.typechecked == Prelude.FIN

        }

        fun findAllVariablePatterns(clause: Abstract.Clause, excludedPsi: PsiElement?): HashSet<ArendDefIdentifier> {
            val result = HashSet<ArendDefIdentifier>()
            if (clause !is PsiElement) return result

            for (pattern in clause.patterns) doFindVariablePatterns(result, pattern as PsiElement, excludedPsi)

            val functionClauses = clause.parent
            val functionBody = functionClauses?.parent
            val defFunction = functionBody?.parent
            if (functionClauses is ArendFunctionClauses && functionBody is ArendFunctionBody && defFunction is ArendFunctionalDefinition) {
                val elim = functionBody.elim
                if (elim?.elimKw != null) {
                    val allParams = defFunction.nameTeleList.flatMap { it.referableList }
                    val eliminatedParams = elim.refIdentifierList.mapNotNull { it.reference?.resolve() as Referable }
                    result.addAll((allParams - eliminatedParams).filterIsInstance<ArendDefIdentifier>())
                }
            }

            return result
        }

        private fun doFindVariablePatterns(variables: MutableSet<ArendDefIdentifier>, node: PsiElement, excludedPsi: PsiElement?) {
            if (node is ArendDefIdentifierImplMixin && node.reference.resolve() == node) {
                variables.add(node)
            } else if (node != excludedPsi)
                for (child in node.children)
                    doFindVariablePatterns(variables, child, excludedPsi)
        }

        fun locatePattern(element: PsiElement): Pair<Abstract.Clause, ArrayList<Int>>? {
            var pattern: PsiElement? = null
            var patternOwner: PsiElement? = element
            val indexList = ArrayList<Int>()

            while (patternOwner is ArendPattern || patternOwner is ArendAtomPattern || patternOwner is ArendAtomPatternOrPrefix) {
                pattern = patternOwner
                patternOwner = patternOwner.parent
                if (patternOwner is ArendPattern) {
                    val i = patternOwner.atomPatternOrPrefixList.indexOf(pattern)
                    if (i != -1) indexList.add(0, i)
                }
                if (patternOwner is ArendClause) indexList.add(0, patternOwner.patternList.indexOf(pattern))
                if (patternOwner is ArendConstructorClause) indexList.add(0, patternOwner.patternList.indexOf(pattern))
                if (patternOwner is ArendAtomPattern && patternOwner.patternList.size > 1) indexList.add(0, patternOwner.patternList.indexOf(pattern))
            }

            if (pattern == null) return null
            val clause: Abstract.Clause = patternOwner as? Abstract.Clause ?: return null
            return Pair(clause, indexList)
        }

        private fun findPattern(indexList: List<Int>, typecheckedPattern: ExpressionPattern, abstractPattern: Abstract.Pattern): ExpressionPattern? {
            if (indexList.isEmpty()) return typecheckedPattern
            if (typecheckedPattern is ConstructorExpressionPattern) {
                val typecheckedPatternChild = findMatchingPattern(abstractPattern.arguments, typecheckedPattern.parameters, typecheckedPattern.subPatterns, indexList[0])
                val abstractPatternChild = abstractPattern.arguments.getOrNull(indexList[0])
                if (typecheckedPatternChild != null && abstractPatternChild != null)
                    return findPattern(indexList.drop(1), typecheckedPatternChild, abstractPatternChild)
            }
            return null
        }

        private fun findAbstractPattern(indexList: List<Int>, abstractPattern: Abstract.Pattern?): Abstract.Pattern? {
            if (indexList.isEmpty()) return abstractPattern
            val abstractPatternChild = abstractPattern?.arguments?.getOrNull(indexList[0])
            if (abstractPatternChild != null) return findAbstractPattern(indexList.drop(1), abstractPatternChild)
            return null
        }

        private fun findMatchingPattern(abstractPatterns: List<Abstract.Pattern>, parameters: DependentLink, typecheckedPatterns: List<ExpressionPattern>, index: Int): ExpressionPattern? {
            var link = parameters
            var i = 0
            var j = 0

            while (link.hasNext() && i < abstractPatterns.size) {
                val isEqual = link.isExplicit == abstractPatterns[i].isExplicit
                if (isEqual && index == i) return typecheckedPatterns.getOrNull(j)

                if (isEqual || link.isExplicit) i++
                if (isEqual || !link.isExplicit) {
                    link = link.next
                    j++
                }
            }

            return null
        }

        fun doReplacePattern(factory: ArendPsiFactory, elementToReplace: PsiElement, patternLine: String, requiresParentheses: Boolean, asExpression: String = ""): PsiElement? {
            val pLine = if (asExpression.isNotEmpty()) "$patternLine \\as $asExpression" else patternLine
            val replacementPattern: PsiElement? = when (elementToReplace) {
                is ArendPattern ->
                    factory.createClause(if (!elementToReplace.isExplicit) "{$pLine}" else pLine).childOfType<ArendPattern>()
                is ArendAtomPatternOrPrefix ->
                    factory.createAtomPattern(if (!elementToReplace.isExplicit) "{$pLine}" else if (requiresParentheses || asExpression.isNotEmpty()) "($pLine)" else pLine)
                else -> null
            }

            if (replacementPattern != null) {
                return elementToReplace.replaceWithNotification(replacementPattern)
            }

            return null
        }

        fun doSubstituteUsages(project: Project, elementToReplace: ArendReferenceElement?, element: PsiElement, expressionLine: String, resolver: (ArendRefIdentifier) -> PsiElement? = { it.reference?.resolve() }) {
            if (elementToReplace == null || element is ArendWhere) return
            if (element is ArendRefIdentifier && resolver(element) == elementToReplace) {
                val longName = element.parent as? ArendLongName
                val field = if (longName != null && longName.refIdentifierList.size > 1) longName.refIdentifierList[1] else null
                val fieldTarget = field?.reference?.resolve()
                val expressionToInsert = if (longName != null && fieldTarget is ArendClassField) createFieldConstructorInvocation(element, longName.refIdentifierList.drop(1), expressionLine) else expressionLine
                val factory = ArendPsiFactory(project)

                val literal = longName?.parent as? ArendLiteral
                val atom = literal?.parent as? ArendAtom
                if (atom != null) {
                    val atomFieldsAcc = atom.parent as? ArendAtomFieldsAcc
                    val argumentAppExpr = atomFieldsAcc?.parent as? ArendArgumentAppExpr
                    val arendNewExpr = argumentAppExpr?.parent as? ArendNewExpr

                    val substitutedExpression = factory.createExpression(expressionToInsert) as ArendNewExpr
                    val substitutedAtom = if (needParentheses(element, element.textRange, substitutedExpression, null))
                        factory.createExpression("($expressionToInsert)").childOfType() else substitutedExpression.childOfType<ArendAtom>()

                    if (arendNewExpr != null && atomFieldsAcc.fieldAccList.isEmpty() && argumentAppExpr.argumentList.isEmpty() &&
                            arendNewExpr.let { it.lbrace == null && it.rbrace == null }) {
                        arendNewExpr.replaceWithNotification(substitutedExpression)
                    } else if (substitutedAtom is PsiElement) {
                        atom.replaceWithNotification(substitutedAtom)
                    }
                }
            } else for (child in element.children)
                doSubstituteUsages(project, elementToReplace, child, expressionLine, resolver)
        }

        private fun createFieldConstructorInvocation(element: ArendRefIdentifier,
                                                     longNameTail: List<ArendRefIdentifier>,
                                                     substitutedExpression: String): String {
            val field = if (longNameTail.isNotEmpty()) longNameTail[0] else null
            val fieldTarget = field?.reference?.resolve()
            return if (fieldTarget is ArendClassField) {
                val fieldName = getTargetName(fieldTarget, element)
                createFieldConstructorInvocation(element, longNameTail.drop(1), "$fieldName {${substitutedExpression}}")
            } else {
                if (longNameTail.isEmpty()) {
                    substitutedExpression
                } else {
                    "($substitutedExpression)${longNameTail.foldRight("", {ref, acc -> "$acc.${ref.referenceName}"})}"
                }
            }
        }
    }
}