package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.Variable
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Constructor
import org.arend.core.expr.ClassCallExpression
import org.arend.core.expr.DataCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.SigmaExpression
import org.arend.core.expr.visitor.NormalizeVisitor
import org.arend.core.expr.visitor.ToAbstractVisitor
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.Pattern
import org.arend.error.ListErrorReporter
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.ListScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.VariableImpl
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.ArendTypecheckingListener
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.patternmatching.ElimTypechecking
import org.arend.typechecking.patternmatching.PatternTypechecking
import org.arend.typechecking.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import java.util.*
import java.util.Collections.singletonList

class SplitAtomPatternIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, "Split atomic pattern") {
    private var splitPatternEntries: List<SplitPatternEntry>? = null

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor?): Boolean {
        if (element is ArendPattern && element.atomPatternOrPrefixList.size == 0 || element is ArendAtomPatternOrPrefix) {
            val type = getCachedElementType(element) ?: getElementType(element, editor?.project)
            if (type is DataCallExpression) {
                val constructors = type.matchedConstructors ?: return false
                this.splitPatternEntries = constructors.map { ConstructorSplitPatternEntry(it.definition) }
                return true
            }
            if (type is SigmaExpression) {
                this.splitPatternEntries = singletonList(TupleSplitPatternEntry(type.parameters))
                return true
            }
            if (type is ClassCallExpression) {
                val definition = type.definition
                if (definition.isRecord) {
                    this.splitPatternEntries = singletonList(RecordSplitPatternEntry(type, definition))
                    return true
                }
            }
        }
        return false
    }

    override fun applyTo(element: PsiElement, project: Project?, editor: Editor?) {
        splitPatternEntries?.let { doSplitPattern(element, project, it) }
    }

    private fun getCachedElementType(element: PsiElement): Expression? {
        val defIdentifier = when (element) {
            is ArendPattern -> {
                element.defIdentifier
            }
            is ArendAtomPatternOrPrefix -> {
                element.defIdentifier
            }
            else -> null
        }
        val cachedBinding = if (defIdentifier != null) ArendTypecheckingListener.referableTypeCache[defIdentifier] else null
        return cachedBinding?.typeExpr
    }

    private fun getElementType(element: PsiElement, project: Project?): Expression? {
        if (project != null) {
            var definition: ArendDefinition? = null
            val (patternOwner, indexList) = locatePattern(element) ?: return null
            val ownerParent = (patternOwner as PsiElement).parent
            var abstractPatterns: List<Abstract.Pattern>? = null

            if (patternOwner is ArendClause && ownerParent is ArendFunctionClauses) {
                val body = ownerParent.parent
                val func = body?.parent
                if (body is ArendFunctionBody && func is ArendFunctionalDefinition) {
                    abstractPatterns = patternOwner.patterns
                    definition = func as? ArendDefinition
                }
            }
            if (patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
                /* val data = ownerParent.parent
                abstractPatterns = patternOwner.patterns
                if (data is ArendDefData) definition = data */
                return null // TODO: Implement some behavior for constructor clauses as well
            }

            if (definition != null) {
                val typeCheckedDefinition = project.service<TypeCheckingService>().getTypechecked(definition)
                if (typeCheckedDefinition != null && abstractPatterns != null) {
                    val (patterns, isElim) = computePatterns(abstractPatterns, typeCheckedDefinition.parameters, definition as? Abstract.EliminatedExpressionsHolder, definition.scope, project)
                    if (patterns != null && indexList.size > 0) {
                        val typecheckedPattern = if (isElim) patterns.getOrNull(indexList[0]) else findMatchingPattern(abstractPatterns, typeCheckedDefinition.parameters, patterns, indexList[0])
                        if (typecheckedPattern != null) {
                            val patternPart = findPattern(indexList.drop(1), typecheckedPattern, abstractPatterns[indexList[0]]) as? BindingPattern ?: return null
                            return patternPart.binding.typeExpr
                        }
                    }
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

        abstract class DependentLinkSplitPatternEntry : SplitPatternEntry {
            val params: ArrayList<String> = ArrayList()

            abstract fun getDependentLink(): DependentLink

            override fun initParams(occupiedNames: MutableSet<Variable>) {
                params.clear()
                var parameter = getDependentLink()
                val renamer = StringRenamer()

                while (parameter.hasNext()) {
                    val name = renamer.generateFreshName(parameter, occupiedNames)
                    occupiedNames.add(parameter)
                    if (parameter.isExplicit)
                        params.add(name)
                    parameter = parameter.next
                }
            }
        }

        class ConstructorSplitPatternEntry(val constructor: Constructor) : DependentLinkSplitPatternEntry() {
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

        class TupleSplitPatternEntry(private val link: DependentLink) : DependentLinkSplitPatternEntry() {
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
                renamer.forceTypeSCName = true

                for (field in record.fields.filter { record.isGoodField(it) }) {
                    val name = renamer.generateFreshName(field, occupiedNames)
                    occupiedNames.add(field)
                    params.add(name)
                }
            }

            override fun patternString(location: ArendCompositeElement): String = TupleSplitPatternEntry.printTuplePattern(params)

            override fun expressionString(location: ArendCompositeElement): String = buildString {
                append("\\new ")
                val recordName = getTargetName(PsiLocatedReferable.fromReferable(record.referable), location) ?: record.name
                append("$recordName ")
                val expr = ToAbstractVisitor.convert(dataCall, object : PrettyPrinterConfig { override fun getNormalizationMode(): NormalizeVisitor.Mode? { return null } })
                if (expr is Concrete.AppExpression) {
                    PrettyPrintVisitor.printArguments(PrettyPrintVisitor(this, 0), expr.arguments, false)
                    append(" ")
                }
                for (p in params) append("$p ")
            }.trim()

            override fun requiresParentheses(): Boolean = true
        }

        fun doSplitPattern(element: PsiElement, project: Project?, splitPatternEntries: Collection<SplitPatternEntry>, generateBody: Boolean = false) {
            val (localClause, localIndexList) = locatePattern(element) ?: return
            if (localClause !is ArendClause) return

            val localNames = HashSet<Variable>()
            localNames.addAll(findAllVariablePatterns(localClause, element).map {
                VariableImpl(it.name ?: "")
            })

            if (project != null && element is Abstract.Pattern) {
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
                            doSubstituteUsages(factory, element.childOfType(), currAnchor, expressionString, splitPatternEntry.requiresParentheses())

                            var inserted = false
                            if (splitPatternEntry is ConstructorSplitPatternEntry && splitPatternEntry.constructor == Prelude.ZERO) {
                                var number = 0
                                val abstractPattern = localClause.patternList[localIndexList[0]]
                                var path = localIndexList.drop(1)
                                while (path.isNotEmpty()) {
                                    val patternPiece = findAbstractPattern(path.dropLast(1), abstractPattern)
                                    if (patternPiece !is PsiElement || !isSucPattern(patternPiece, project)) break
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
                                    doSubstituteUsages(factory, elementCopy.childOfType(), currAnchor, expressionString, splitPatternEntry.requiresParentheses())
                                    doReplacePattern(factory, elementCopy, patternString, splitPatternEntry.requiresParentheses())
                                }
                            }
                        }

                        first = false
                    }
                }
            }
        }

        private fun isSucPattern(pattern: PsiElement, project: Project): Boolean {
            if (pattern !is ArendPatternImplMixin || pattern.arguments.size != 1) {
                return false
            }

            val constructor = (pattern.headReference as? UnresolvedReference)?.resolve(pattern.scope, null) as? ArendConstructor
                    ?: return false
            return constructor.name == Prelude.SUC.name && project.service<TypeCheckingService>().getTypechecked(constructor.ancestor<ArendDefData>()
                    ?: return false) == Prelude.NAT
        }

        private fun findAllVariablePatterns(clause: ArendClause, element: PsiElement): HashSet<ArendDefIdentifier> {
            val result = HashSet<ArendDefIdentifier>()
            for (pattern in clause.patternList) doFindVariablePatterns(result, pattern, element)

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

        private fun doFindVariablePatterns(variables: MutableSet<ArendDefIdentifier>, node: PsiElement, element: PsiElement) {
            if (node is ArendDefIdentifierImplMixin && node.reference.resolve() == node) {
                variables.add(node)
            } else if (node != element)
                for (child in node.children)
                    doFindVariablePatterns(variables, child, element)
        }

        private fun locatePattern(element: PsiElement): Pair<Abstract.Clause, ArrayList<Int>>? {
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
            }

            if (pattern == null) return null
            val clause: Abstract.Clause = patternOwner as? Abstract.Clause ?: return null
            return Pair(clause, indexList)
        }

        private fun computePatterns(abstractPatterns: List<Abstract.Pattern>, parameters: DependentLink, elimExprHolder: Abstract.EliminatedExpressionsHolder?, scope: Scope, project: Project): Pair<List<Pattern>?, Boolean> {
            val listErrorReporter = ListErrorReporter()
            val concreteParameters = elimExprHolder?.parameters?.let { params ->
                ConcreteBuilder.convert(IdReferableConverter.INSTANCE, true) { it.buildTelescopeParameters(params) }
            }
            val elimVars = elimExprHolder?.eliminatedExpressions?.let { vars ->
                ConcreteBuilder.convert(IdReferableConverter.INSTANCE, true) { it.buildReferences(vars) }
            }
            val elimParams = if (concreteParameters != null && elimVars != null) {
                val context = LinkedHashMap<Referable, Binding>()
                var link = parameters
                loop@ for (parameter in concreteParameters) {
                    for (referable in parameter.referableList) {
                        if (!link.hasNext()) break@loop

                        context[referable] = link
                        link = link.next
                    }
                }
                val listScope = ListScope(context.keys.toList())
                for (elimVar in elimVars) ExpressionResolveNameVisitor.resolve(elimVar, listScope, null)

                ElimTypechecking.getEliminatedParameters(elimVars, emptyList(), parameters, listErrorReporter, context)
                        ?: return Pair(null, true)
            } else {
                emptyList()
            }

            val concretePatterns = ConcreteBuilder.convert(IdReferableConverter.INSTANCE, true) { it.buildPatterns(abstractPatterns) }
            DesugarVisitor.desugarPatterns(concretePatterns, listErrorReporter)
            val service = project.service<TypeCheckingService>()
            ExpressionResolveNameVisitor(EmptyConcreteProvider.INSTANCE, ConvertingScope(service.newReferableConverter(true), scope), ArrayList(), listErrorReporter, null).visitPatterns(concretePatterns)
            // TODO: This won't work with defined constructors
            val patternTypechecking = PatternTypechecking(listErrorReporter, PatternTypechecking.Mode.CONSTRUCTOR, service.typecheckerState)
            val patterns = patternTypechecking.typecheckPatterns(concretePatterns, parameters, elimParams)
            return Pair(if (listErrorReporter.errorList.isEmpty()) patterns else null, elimParams.isNotEmpty())
        }

        private fun findPattern(indexList: List<Int>, typecheckedPattern: Pattern, abstractPattern: Abstract.Pattern): Pattern? {
            if (indexList.isEmpty()) return typecheckedPattern
            if (typecheckedPattern is ConstructorPattern) {
                val typecheckedPatternChild = findMatchingPattern(abstractPattern.arguments, typecheckedPattern.parameters, typecheckedPattern.patterns.patternList, indexList[0])
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

        private fun findMatchingPattern(abstractPatterns: List<Abstract.Pattern>, parameters: DependentLink, typecheckedPatterns: List<Pattern>, index: Int): Pattern? {
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

        private fun doReplacePattern(factory: ArendPsiFactory, elementToReplace: PsiElement, patternLine: String, requiresParentheses: Boolean) {
            val replacementPattern: PsiElement? = when (elementToReplace) {
                is ArendPattern ->
                    factory.createClause(if (!elementToReplace.isExplicit) "{$patternLine}" else patternLine).childOfType<ArendPattern>()
                is ArendAtomPatternOrPrefix ->
                    factory.createAtomPattern(if (!elementToReplace.isExplicit) "{$patternLine}" else if (requiresParentheses) "($patternLine)" else patternLine)
                else -> null
            }

            if (replacementPattern != null) {
                elementToReplace.replaceWithNotification(replacementPattern)
            }
        }

        fun doSubstituteUsages(factory: ArendPsiFactory, elementToReplace: ArendReferenceElement?, element: PsiElement,
                                       expressionLine: String, requiresParentheses: Boolean) {
            if (elementToReplace == null || element is ArendWhere) return
            if (element is ArendRefIdentifier && element.reference?.resolve() == elementToReplace) {
                val longName = element.parent as? ArendLongName
                val field = if (longName != null && longName.refIdentifierList.size > 1) longName.refIdentifierList[1] else null
                val fieldTarget = field?.reference?.resolve()
                val expressionToInsert = if (longName != null && fieldTarget is ArendClassField) createFieldConstructorInvocation(element, longName.refIdentifierList.drop(1), expressionLine) else expressionLine

                val literal = longName?.parent as? ArendLiteral
                val atom = literal?.parent as? ArendAtom
                if (atom != null) {
                    val atomFieldsAcc = atom.parent as? ArendAtomFieldsAcc
                    val argumentAppExpr = atomFieldsAcc?.parent as? ArendArgumentAppExpr
                    val arendNewExpr = argumentAppExpr?.parent as? ArendNewExpr

                    val substitutedExpression = factory.createExpression(expressionToInsert) as ArendNewExpr
                    val substitutedAtom = if (requiresParentheses) factory.createExpression("($expressionToInsert)").childOfType() else substitutedExpression.childOfType<ArendAtom>()

                    if (arendNewExpr != null && atomFieldsAcc.fieldAccList.isEmpty() && argumentAppExpr.argumentList.isEmpty() &&
                            arendNewExpr.let { it.lbrace == null && it.rbrace == null }) {
                        arendNewExpr.replaceWithNotification(substitutedExpression)
                    } else if (substitutedAtom is PsiElement) {
                        atom.replaceWithNotification(substitutedAtom)
                    }
                }
            } else for (child in element.children)
                doSubstituteUsages(factory, elementToReplace, child, expressionLine, requiresParentheses)
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