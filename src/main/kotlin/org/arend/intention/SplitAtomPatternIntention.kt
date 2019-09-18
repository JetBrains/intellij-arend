package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.Variable
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Constructor
import org.arend.core.expr.DataCallExpression
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorPattern
import org.arend.core.pattern.Pattern
import org.arend.error.ListErrorReporter
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.naming.renamer.Renamer
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.ListScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendDefIdentifierImplMixin
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendPatternImplMixin
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.LocationData
import org.arend.refactoring.computeAliases
import org.arend.refactoring.getDataTypeStartingCharacter
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.patternmatching.ElimTypechecking
import org.arend.typechecking.patternmatching.PatternTypechecking
import org.arend.typechecking.patternmatching.PatternTypechecking.Flag
import org.arend.typechecking.typecheckable.provider.EmptyConcreteProvider
import org.arend.typechecking.visitor.DesugarVisitor
import org.arend.util.LongName
import java.util.*

class SplitAtomPatternIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, "Split atomic pattern") {
    private var matchedConstructors: List<Constructor>? = null

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor?): Boolean {
        if (element is ArendPattern && element.atomPatternOrPrefixList.size == 0 || element is ArendAtomPatternOrPrefix) {
            val pattern = checkApplicability(element, editor?.project)
            if (pattern != null) {
                val type = pattern.toExpression().type //do we want to normalize this to whnf?
                if (type is DataCallExpression) {
                    val constructors = type.matchedConstructors
                    this.matchedConstructors = constructors.map { it.definition }
                    return constructors != null
                }
            }
        }
        return false
    }

    override fun applyTo(element: PsiElement, project: Project?, editor: Editor?) {
        val constructors = matchedConstructors
        if (constructors != null) doSplitPattern(element, project, editor, constructors)
    }

    private fun checkApplicability(element: PsiElement, project: Project?): BindingPattern? {
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
                            return findPattern(indexList.drop(1), typecheckedPattern, abstractPatterns[indexList[0]]) as? BindingPattern
                                    ?: return null
                        }
                    }
                }
            }
        }

        return null
    }

    companion object {
        fun doSplitPattern(element: PsiElement, project: Project?, editor: Editor?, localConstructors: Collection<Constructor>, generateBody: Boolean = false) {
            val (localClause, localIndexList) = locatePattern(element) ?: return
            if (localClause !is ArendClause) return

            val localNames = HashSet<Variable>()
            localNames.addAll(findAllVariablePatterns(localClause, element).map {
                object : Variable {
                    override fun getName(): String = it.name ?: ""
                }
            })

            if (project != null && element is Abstract.Pattern) {
                val factory = ArendPsiFactory(project)
                if (localConstructors.isEmpty()) {
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

                    for (constructor in localConstructors) {
                        val localSet = HashSet<Variable>()
                        localSet.addAll(localNames)
                        val renamer = StringRenamer()
                        val listParams = ArrayList<String>()
                        val containingFile = localClause.containingFile
                        val constructorReferable = PsiLocatedReferable.fromReferable(constructor.referable)
                        val locationData = if (constructorReferable != null) LocationData(constructorReferable) else null
                        val aliasData = if (locationData != null && containingFile is ArendFile) computeAliases(locationData, containingFile, localClause) else null

                        val constructorName = if (aliasData != null) {
                            aliasData.first?.execute(editor)
                            LongName(aliasData.second).toString()
                        } else constructor.name

                        val patternString = buildString {
                            var parameter = constructor.parameters
                            append("$constructorName ")
                            while (parameter.hasNext()) {
                                renamer.setUnnamed(getDataTypeStartingCharacter(parameter.type)?.toString() ?: Renamer.UNNAMED)
                                val name = renamer.generateFreshName(parameter, localNames)
                                localNames.add(parameter)
                                if (parameter.isExplicit) {
                                    listParams.add(name)
                                    append("$name ")
                                }
                                parameter = parameter.next
                            }
                        }.trim()

                        val expressionString = if (constructor.referable.precedence.isInfix && listParams.size == 2)
                            "${listParams[0]} $constructorName ${listParams[1]}" else patternString

                        if (first) {
                            replaceUsages(factory, element, currAnchor, expressionString, listParams.isNotEmpty())

                            var inserted = false
                            if (constructor == Prelude.ZERO) {
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
                                doReplacePattern(factory, element, patternString, listParams.isNotEmpty())
                        } else {
                            val anchorParent = currAnchor.parent
                            currAnchor = anchorParent.addAfter(pipe, currAnchor)
                            anchorParent.addBefore(factory.createWhitespace("\n"), currAnchor)
                            currAnchor = anchorParent.addAfterWithNotification(clauseCopy, currAnchor)
                            anchorParent.addBefore(factory.createWhitespace(" "), currAnchor)

                            if (currAnchor is ArendClause) {
                                val elementCopy = findAbstractPattern(localIndexList.drop(1), currAnchor.patternList.getOrNull(localIndexList[0]))
                                if (elementCopy is PsiElement) {
                                    replaceUsages(factory, elementCopy, currAnchor, expressionString, listParams.isNotEmpty())
                                    doReplacePattern(factory, elementCopy, patternString, listParams.isNotEmpty())
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
            ExpressionResolveNameVisitor(EmptyConcreteProvider.INSTANCE, ConvertingScope(project.service<TypeCheckingService>().newReferableConverter(true), scope), ArrayList(), listErrorReporter, null).visitPatterns(concretePatterns)
            val patternTypechecking = PatternTypechecking(listErrorReporter, EnumSet.of(Flag.ALLOW_INTERVAL, Flag.ALLOW_CONDITIONS), null)
            val patterns = patternTypechecking.typecheckPatterns(concretePatterns, parameters, elimParams)
            val builder = StringBuilder()
            for (error in listErrorReporter.errorList) builder.append(error)
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

        private fun replaceUsages(factory: ArendPsiFactory, elementToReplace: Abstract.Pattern, expression: PsiElement, expressionLine: String, requiresParentheses: Boolean) {
            if (elementToReplace is PsiElement) {
                val defIdentifier = elementToReplace.childOfType<ArendDefIdentifier>()
                val substitutedExpression = factory.createExpression(expressionLine) as ArendNewExpr
                val substitutedAtom = if (requiresParentheses) factory.createExpression("($expressionLine)").childOfType<ArendAtom>() else substitutedExpression.childOfType()
                if (defIdentifier != null) doSubstitute(defIdentifier, expression, substitutedExpression, substitutedAtom!!)
            }
        }

        private fun doSubstitute(elementToReplace: ArendDefIdentifier, element: PsiElement,
                                 substitutedExpression: ArendNewExpr, substitutedAtom: ArendAtom) {
            if (element is ArendRefIdentifier && element.reference?.resolve() == elementToReplace) {
                val atom = if (element.parent is ArendLongName && element.parent.parent is ArendLiteral) element.parent.parent.parent as? ArendAtom else null
                if (atom != null) {
                    if ((atom.parent as? ArendAtomFieldsAcc)?.fieldAccList?.isEmpty() == true &&
                            (atom.parent.parent as? ArendArgumentAppExpr)?.argumentList?.isEmpty() == true &&
                            atom.parent.parent.parent.let { it is ArendNewExpr && it.lbrace == null && it.rbrace == null }) {
                        (atom.parent.parent as ArendArgumentAppExpr).replaceWithNotification(substitutedExpression)
                    } else {
                        atom.replaceWithNotification(substitutedAtom)
                    }
                }
            } else for (child in element.children)
                doSubstitute(elementToReplace, child, substitutedExpression, substitutedAtom)
        }
    }
}