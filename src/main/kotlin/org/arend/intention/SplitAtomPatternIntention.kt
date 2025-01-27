package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import com.intellij.util.containers.tail
import org.arend.ext.variable.Variable
import org.arend.core.context.param.DependentLink
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
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.reference.Precedence
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.*
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.util.ArendBundle
import java.util.*
import java.util.Collections.singletonList
import kotlin.collections.HashSet

class SplitAtomPatternIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, ArendBundle.message("arend.pattern.split")) {
    private var splitPatternEntries: List<SplitPatternEntry>? = null
    private var caseClauseParameters: DependentLink? = null

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor): Boolean {
        val defIdentifier = when (element) {
            is ArendPattern -> element.singleReferable?.refName
            else -> null
        }
        val project = editor.project
        this.splitPatternEntries = null

        if (!(element is ArendPattern && element.sequence.isEmpty())) return this.splitPatternEntries != null
        val type = getElementType(element, editor)?.let{ TypeConstructorExpression.unfoldType(it) }
        this.splitPatternEntries = when (type) {
            is DataCallExpression -> {
                val canDoPatternMatchingOnIdp = admitsPatternMatchingOnIdp(type, caseClauseParameters)
                if (project != null && canDoPatternMatchingOnIdp == PatternMatchingOnIdpResult.IDP) {
                    singletonList(IdpPatternEntry(project))
                } else if (canDoPatternMatchingOnIdp != PatternMatchingOnIdpResult.DO_NOT_ELIMINATE) {
                    val constructors = type.matchedConstructors ?: return false
                    constructors.map { ConstructorSplitPatternEntry(it.definition, defIdentifier, type.definition) }
                } else null
            }
            is SigmaExpression -> singletonList(TupleSplitPatternEntry(type.parameters))
            is ClassCallExpression -> {
                if (type.definition == Prelude.DEP_ARRAY) {
                    val isEmpty = ConstructorExpressionPattern.isArrayEmpty(type)
                    val result = ArrayList<SplitPatternEntry>()
                    for (p in arrayOf(Pair(true, Prelude.EMPTY_ARRAY), Pair(false, Prelude.ARRAY_CONS)))
                        if (isEmpty == null || isEmpty == p.first) result.add(ConstructorSplitPatternEntry(p.second, defIdentifier, type.definition))
                    result
                } else {
                    singletonList(ClassSplitPatternEntry(type))
                }
            }
            else -> null
        }
        return this.splitPatternEntries != null
    }

    override fun applyTo(element: PsiElement, project: Project, editor: Editor) {
        splitPatternEntries?.let { doSplitPattern(element, project, it) }
    }

    private fun getElementType(element: PsiElement, editor: Editor): Expression? {
        val project = editor.project
        caseClauseParameters = null
        if (project == null) return null
        var definition: TCDefinition? = null
        val (patternOwner, indexList) = locatePattern(element) ?: return null
        val ownerParent = (patternOwner as PsiElement).parent
        var abstractPatterns: List<Abstract.Pattern>? = null
        var coClauseName: String? = null

        var clauseIndex = -1
        if (patternOwner is ArendClause) {
            val body = ownerParent.ancestor<ArendFunctionBody>()?.let {
                if (it.kind == ArendFunctionBody.Kind.COCLAUSE) it.parent.ancestor() else it
            }
            val func = body?.parent
            abstractPatterns = patternOwner.patterns

            if (ownerParent is ArendFunctionClauses)
                clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
            else if (ownerParent is ArendFunctionBody && ownerParent.kind == ArendFunctionBody.Kind.COCLAUSE) {
                coClauseName = ownerParent.ancestor<ArendCoClause>()?.longName?.referenceName
                clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
            }

            if (body is ArendFunctionBody && func is ArendFunctionDefinition<*>) {
                definition = func
            }
        }
        if (patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
            /* val data = ownerParent.parent
            abstractPatterns = patternOwner.patterns
            if (data is ArendDefData) definition = data */
            return null // TODO: Implement some behavior for constructor clauses as well
        }

        /* TODO[server2]
        if (definition != null && clauseIndex != -1) {
            var typeCheckedDefinition = project.service<ArendServerService>().server.getTCReferable(definition)?.typechecked
            var concreteClauseOwner = (element.containingFile as? ArendFile)?.concreteDefinitions?.get(definition.refLongName) as? Concrete.FunctionDefinition ?: PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null).getConcreteFunction(definition) ?: return null
            if (typeCheckedDefinition is FunctionDefinition && definition is Abstract.ParametersHolder && definition is Abstract.EliminatedExpressionsHolder && abstractPatterns != null) {
                if (coClauseName != null) {
                  val classCallExpression = typeCheckedDefinition.resultType as? ClassCallExpression
                  val expr = classCallExpression?.implementations?.firstOrNull { it.key.name == coClauseName }?.value
                  typeCheckedDefinition = ((expr as? LamExpression)?.body as? FunCallExpression)?.definition ?: typeCheckedDefinition
                  concreteClauseOwner = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null).getConcrete(typeCheckedDefinition.ref.underlyingReferable as GlobalReferable) as? Concrete.FunctionDefinition ?: concreteClauseOwner
                }

                val elimBody = (((typeCheckedDefinition as? FunctionDefinition)?.actualBody as? IntervalElim)?.otherwise
                        ?: ((typeCheckedDefinition as? FunctionDefinition)?.actualBody as? ElimBody) ?: return null)

                val corePatterns = elimBody.clauses.getOrNull(clauseIndex)?.patterns?.let { Pattern.toExpressionPatterns(it, typeCheckedDefinition.parameters) }
                        ?: return null

                val parameters = ArrayList<Referable>(); for (pp in definition.parameters) parameters.addAll(pp.referableList)
                val elimVars = definition.eliminatedExpressions ?: emptyList()
                val isElim = elimVars.isNotEmpty()
                val elimVarPatterns: List<ExpressionPattern> = if (isElim) elimVars.map { reference ->
                    if (reference is ArendRefIdentifier) {
                        val parameterIndex = (reference.reference?.resolve() as? Referable)?.let { parameters.indexOf(it) }
                                ?: -1
                        if (parameterIndex < corePatterns.size && parameterIndex != -1) corePatterns[parameterIndex] else throw IllegalStateException()
                    } else throw IllegalStateException()
                } else corePatterns

                if (indexList.isNotEmpty()) {
                    val concreteClause = concreteClauseOwner.body.clauses[clauseIndex]
                    val index = patternOwner
                            .patterns
                            .filterIsInstance<ArendPattern>()
                            .indexOfFirst {
                                it.skipSingleTuples() == indexList[0]
                            }
                    val (typecheckedPattern, concrete) = (if (isElim) elimVarPatterns.getOrNull(index)?.let { it to  concreteClause.patterns.find { it.data == indexList[0] } }
                    else findMatchingPattern(concreteClause.patterns, typeCheckedDefinition.parameters, corePatterns, indexList[0])) ?: return null
                    if (concrete == null) return null
                    val patternPart = findPattern(indexList.drop(1), typecheckedPattern, concrete) as? BindingPattern
                            ?: return null
                    return patternPart.binding.typeExpr
                }
            }
        }
        */

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

                if (recursiveTypeDefinition != null && !parameterName.isNullOrEmpty() && !Character.isDigit(parameterName.last())) {
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
                val locatedReferable = PsiLocatedReferable.fromReferable(constructor.referable)
                val (constructorName, namespaceCommand) = if (locatedReferable != null) getTargetName(locatedReferable, location) else Pair(constructor.name, null)
                namespaceCommand?.execute()

                val isInfix = constructor.referable.precedence.isInfix
                return buildString {
                    if (params.isEmpty()) {
                        append("$constructorName ")
                        return@buildString
                    } else if (isInfix) {
                        append("${params[0]} $constructorName ")
                    } else {
                        append("$constructorName ${params[0]} ")
                    }
                    for (p in params.tail()) append("$p ")
                }.trim()
            }

            override fun expressionString(location: ArendCompositeElement): String {
                val locatedReferable = PsiLocatedReferable.fromReferable(constructor.referable)
                val (constructorName, namespaceCommand) = if (locatedReferable != null) getTargetName(locatedReferable, location) else Pair(constructor.name, null)
                namespaceCommand?.execute()

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

        class ClassSplitPatternEntry(private val classCall: ClassCallExpression) : SplitPatternEntry {
            val params: ArrayList<String> = ArrayList()

            override fun initParams(occupiedNames: MutableSet<Variable>) {
                params.clear()
                val renamer = StringRenamer()
                renamer.setForceTypeSCName(true)

                for (field in classCall.definition.notImplementedFields) {
                    if (!classCall.isImplementedHere(field)) {
                        val name = renamer.generateFreshName(field, occupiedNames)
                        occupiedNames.add(field)
                        params.add(name)
                    }
                }
            }

            override fun patternString(location: ArendCompositeElement): String = TupleSplitPatternEntry.printTuplePattern(params)

            override fun expressionString(location: ArendCompositeElement): String = buildString {
                append("\\new ")
                val locatedReferable = PsiLocatedReferable.fromReferable(classCall.definition.referable)
                val (recordName, namespaceCommand) = if (locatedReferable != null) getTargetName(locatedReferable, location) else Pair(classCall.definition.name, null)
                namespaceCommand?.execute()

                append("$recordName ")
                val expr = ToAbstractVisitor.convert(classCall, object : PrettyPrinterConfig {
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
            if (element !is ArendPattern) {
                return
            }
            val (localClause, localIndexList) = locatePattern(element) ?: return
            if (localClause !is ArendClause) return

            val topLevelPatterns = localClause.patterns.mapTo(mutableListOf()) {
                ConcreteBuilder.convertPattern(it, DummyErrorReporter.INSTANCE, null)
            }
            ExpressionResolveNameVisitor(localClause.scope, mutableListOf(), TypingInfo.EMPTY, DummyErrorReporter.INSTANCE, null).visitPatterns(topLevelPatterns, mutableMapOf())

            val localNames = HashSet<Variable>()
            localNames.addAll(findAllVariablePatterns(topLevelPatterns, element).map(::VariableImpl))

            val factory = ArendPsiFactory(project)
            if (splitPatternEntries.isEmpty()) {
                doReplacePattern(factory, element, "()", false)
                localClause.expression?.delete()
                localClause.fatArrow?.delete()
            } else {
                var first = true
                if (generateBody && localClause.fatArrow == null) {
                    var currAnchor = localClause.lastChild
                    val sampleClause = factory.createClause("()")
                    currAnchor = localClause.addAfter(sampleClause.fatArrow!!, currAnchor)
                    localClause.addBefore(factory.createWhitespace(" "), currAnchor)
                    localClause.addAfter(sampleClause.expression!!, currAnchor)
                    localClause.addAfter(factory.createWhitespace(" "), currAnchor)
                }

                val clauseCopy = localClause.copy()
                val pipe: PsiElement = factory.createClause("zero").findPrevSibling()!!
                var currAnchor: PsiElement = localClause

                val offsetOfReplaceablePsi = (findReplaceablePsiElement(localIndexList.drop(1), topLevelPatterns.firstNotNullOfOrNull { findDeepInArguments(it, localIndexList[0]) })?.data as? PsiElement)?.startOffset ?: return
                val offsetOfCurrentAnchor = currAnchor.startOffset
                val relativeOffsetOfReplaceablePsi = offsetOfReplaceablePsi - offsetOfCurrentAnchor


                for (splitPatternEntry in splitPatternEntries) {
                    splitPatternEntry.initParams(localNames)
                    val patternString = splitPatternEntry.patternString(localClause)
                    val expressionString = splitPatternEntry.expressionString(localClause)

                    if (first) {
                        doSubstituteUsages(project, element.descendantOfType(), currAnchor, expressionString)

                        var inserted = false
                        if (splitPatternEntry is ConstructorSplitPatternEntry && (splitPatternEntry.constructor == Prelude.ZERO || splitPatternEntry.constructor == Prelude.FIN_ZERO)) {
                            var number = 0
                            val concretePattern = topLevelPatterns.firstNotNullOfOrNull { findDeepInArguments(it, localIndexList[0]) }
                            var path = localIndexList.drop(1)
                            while (path.isNotEmpty()) {
                                var i = 0
                                while (i < path.size && path[path.lastIndex - i].skipSingleTuples() == path.last()) {
                                    i++
                                }
                                val patternPiece = findReplaceablePsiElement(path.dropLast(i), concretePattern)
                                if (patternPiece?.data !is PsiElement || !isSucPattern(patternPiece)) break
                                path = path.dropLast(i)
                                number += 1
                            }
                            val psiToReplace = localIndexList[path.size]
                            doReplacePattern(factory, psiToReplace, number.toString(), false)
                            inserted = true
                        }

                        if (!inserted) {
                            val referable = (splitPatternEntry as? ConstructorSplitPatternEntry)?.constructor?.referable
                            doReplacePattern(factory, element, patternString, splitPatternEntry.requiresParentheses(), insertedReferable = referable)
                        }
                    } else {
                        val anchorParent = currAnchor.parent
                        currAnchor = anchorParent.addAfter(pipe, currAnchor)
                        anchorParent.addBefore(factory.createWhitespace("\n"), currAnchor)
                        currAnchor = anchorParent.addAfter(clauseCopy, currAnchor)
                        anchorParent.addBefore(factory.createWhitespace(" "), currAnchor)

                        if (currAnchor is ArendClause) {
                            val elementCopy = currAnchor.findElementAt(relativeOffsetOfReplaceablePsi)?.parentOfType<ArendPattern>()/*?.goUpIfImplicit()*/

                            if (elementCopy != null) {
                                doSubstituteUsages(project, elementCopy.descendantOfType(), currAnchor, expressionString)
                                val referable = (splitPatternEntry as? ConstructorSplitPatternEntry)?.constructor?.referable
                                doReplacePattern(factory, elementCopy, patternString, splitPatternEntry.requiresParentheses(), insertedReferable = referable)
                            }
                        }
                    }

                    first = false
                }
            }
        }

        fun isSucPattern(pattern: Concrete.Pattern): Boolean {
            if (pattern !is Concrete.ConstructorPattern) return false
            val typechecked = (pattern.constructor as? TCDefReferable)?.typechecked
            return typechecked == Prelude.SUC || typechecked == Prelude.FIN_SUC
        }

        fun findAllVariablePatterns(patterns: List<Concrete.Pattern>, excludedPsi: ArendPattern?): HashSet<String> {
            val result = HashSet<String>()

            for (pattern in patterns) doFindVariablePatterns(result, pattern, excludedPsi)
            result.remove(excludedPsi?.singleReferable?.refName)

            val function = excludedPsi?.parentOfType<ArendFunctionDefinition<*>>() ?: return result
            val elim = function.body?.elim ?: return result
            if (elim.elimKw != null) {
                val allParams = function.parameters.flatMap { it.referableList.mapNotNull { it?.refName } }
                val eliminatedParams = elim.refIdentifierList.mapTo(HashSet()) { it.referenceName }
                result.addAll((allParams - eliminatedParams))
            }

            return result
        }

        private fun doFindVariablePatterns(variables: MutableSet<String>, pattern: Concrete.Pattern, excludedPsi: PsiElement?) {
            if (pattern is Concrete.NamePattern) {
                pattern.referable?.refName?.let { variables.add(it) }
            } else {
                for (subPattern in pattern.patterns)
                    doFindVariablePatterns(variables, subPattern, excludedPsi)
            }
        }

        /**
         * @return the owner of pattern alongside with a top-down path to this pattern
         */
        fun locatePattern(element: PsiElement): Pair<Abstract.Clause, List<ArendPattern>>? {
            var pattern: PsiElement? = element
            val indexList = ArrayList<ArendPattern>()

            while (pattern is ArendPattern) {
                if (pattern.skipSingleTuples() == pattern) {
                    indexList.add(pattern)
                }
                pattern = pattern.parent
            }

            if (pattern == null) return null
            val clause: Abstract.Clause = pattern as? Abstract.Clause ?: return null
            return Pair(clause, indexList.reversed())
        }

        private fun findPattern(indexList: List<ArendPattern>, typecheckedPattern: ExpressionPattern, concretePattern: Concrete.Pattern): ExpressionPattern? {
            if (indexList.isEmpty()) return typecheckedPattern
            if (typecheckedPattern is ConstructorExpressionPattern) {
                val (typecheckedPatternChild, concretePatternChild) = findMatchingPattern(concretePattern.patterns, typecheckedPattern.parameters, typecheckedPattern.subPatterns, indexList[0]) ?: return null
                var i = 0
                while (indexList[i] != indexList[0].skipSingleTuples()) i++
                return findPattern(indexList.drop(i + 1), typecheckedPatternChild, concretePatternChild)
            }
            return null
        }

        private fun findReplaceablePsiElement(indexList: List<ArendPattern>, concretePattern: Concrete.Pattern?): Concrete.Pattern? {
            if (indexList.isEmpty()) return concretePattern
            val concretePatternChild = concretePattern?.patterns?.firstNotNullOfOrNull { findDeepInArguments(it, indexList[0]) }
            var i = 0
            while (indexList[i] != indexList[0].skipSingleTuples()) i++
            if (concretePatternChild != null) return findReplaceablePsiElement(indexList.drop(i + 1), concretePatternChild)
            return null
        }

        private fun findMatchingPattern(concretePatterns: List<Concrete.Pattern>, parameters: DependentLink, typecheckedPatterns: List<ExpressionPattern>, requiredPsi: ArendPattern): Pair<ExpressionPattern, Concrete.Pattern>? {
            var link = parameters
            var i = 0
            var j = 0

            while (link.hasNext() && i < concretePatterns.size) {
                val isEqual = link.isExplicit == concretePatterns[i].isExplicit
                if (isEqual) {
                    when (matches(concretePatterns[i], requiredPsi)) {
                        MatchData.NO -> Unit
                        MatchData.DIRECT -> return typecheckedPatterns.getOrNull(j)?.let { it to concretePatterns[i] }
                        MatchData.DEEP -> {
                            val typechecked = typecheckedPatterns[j] as ConstructorExpressionPattern
                            return findMatchingPattern(concretePatterns[i].patterns, typechecked.parameters, typechecked.subPatterns, requiredPsi)
                        }
                    }
                }
                if (isEqual || link.isExplicit) i++
                if (isEqual || !link.isExplicit) {
                    link = link.next
                    j++
                }
            }

            return null
        }

        fun doReplacePattern(factory: ArendPsiFactory, elementToReplace: ArendPattern, patternLine: String, mayRequireParentheses: Boolean, asExpression: String = "", insertedReferable: GlobalReferable? = null): ArendPattern {
            val pLine = if (asExpression.isNotEmpty()) "$patternLine \\as $asExpression" else patternLine
            val replacementPattern: ArendPattern = factory.createPattern(
                    if (!elementToReplace.isExplicit) "{$pLine}"
                    else if (needParentheses(elementToReplace, mayRequireParentheses, asExpression, patternLine, insertedReferable)) "($pLine)"
                    else pLine
            )

            return elementToReplace.replace(replacementPattern) as ArendPattern
        }

        private fun needParentheses(elementToReplace: ArendPattern, mayRequireParentheses: Boolean, asExpression: String, patternLine: String, referable: GlobalReferable?) : Boolean {
            val enclosingPattern = elementToReplace.parent as? ArendPattern ?: return false
            if (asExpression.isNotEmpty()) return true
            if (!mayRequireParentheses) return false
            if (enclosingPattern.isTuplePattern) return false
            if (patternLine.startsWith("(") && patternLine.endsWith(")")) return false
            val patternList = mutableListOf(ConcreteBuilder.convertPattern(enclosingPattern, DummyErrorReporter.INSTANCE, null))
            ExpressionResolveNameVisitor(enclosingPattern.scope, mutableListOf(), TypingInfo.EMPTY, DummyErrorReporter.INSTANCE, null).visitPatterns(patternList, mutableMapOf())
            val parsedConcretePattern = patternList[0]
            val correspondingConcrete = findParentConcrete(parsedConcretePattern, elementToReplace)
            if (correspondingConcrete !is Concrete.ConstructorPattern) {
                return false
            }
            if ((parsedConcretePattern.patterns.firstOrNull()?.data as? ArendPattern)?.skipSingleTuples()?.startOffset != enclosingPattern.skipSingleTuples().startOffset) {
                // infix pattern may be written in a prefix form
                // this way we should wrap the call with parentheses
                return true
            }
            val enclosingPrecedence = (correspondingConcrete.constructor as? GlobalReferable)?.precedence ?: return false
            if (!enclosingPrecedence.isInfix) {
                // non-fix, argument of a function call should be parenthesized
                return true
            }
            val precedence = referable?.precedence ?: return false
            if (precedence.priority > enclosingPrecedence.priority) {
                return false
            } else if (precedence.priority < enclosingPrecedence.priority) {
                return true
            }
            if (correspondingConcrete.constructor != referable) {
                return true
            }
            val addedLeft = (correspondingConcrete.patterns.getOrNull(0)?.data as? ArendPattern)?.skipSingleTuples() == elementToReplace
            val addedRight = (correspondingConcrete.patterns.getOrNull(1)?.data as? ArendPattern)?.skipSingleTuples() == elementToReplace
            return when (precedence.associativity) {
                Precedence.Associativity.NON_ASSOC -> true
                Precedence.Associativity.LEFT_ASSOC -> !addedLeft
                Precedence.Associativity.RIGHT_ASSOC -> !addedRight
            }
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
                        factory.createExpression("($expressionToInsert)").descendantOfType() else substitutedExpression.descendantOfType<ArendAtom>()

                    if (arendNewExpr != null && atomFieldsAcc.fieldAccList.isEmpty() && argumentAppExpr.argumentList.isEmpty() &&
                            arendNewExpr.let { it.lbrace == null && it.rbrace == null }) {
                        arendNewExpr.replace(substitutedExpression)
                    } else if (substitutedAtom is PsiElement) {
                        atom.replace(substitutedAtom)
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
                val (fieldName, namespaceCommand) = getTargetName(fieldTarget, element)
                namespaceCommand?.execute()
                createFieldConstructorInvocation(element, longNameTail.drop(1), "$fieldName {${substitutedExpression}}")
            } else {
                if (longNameTail.isEmpty()) {
                    substitutedExpression
                } else {
                    "($substitutedExpression)${longNameTail.foldRight("") { ref, acc -> "$acc.${ref.referenceName}" }}"
                }
            }
        }
    }
}

private enum class MatchData {
    NO,
    DIRECT,
    DEEP
}

private fun matches(concreteNode: Concrete.Pattern, element: ArendPattern) : MatchData {
    return if (concreteNode.data == element.skipSingleTuples()) {
        MatchData.DIRECT
    } else if (findDeepInArguments(concreteNode, element) != null) {
        MatchData.DEEP
    } else {
        MatchData.NO
    }
}

private fun findDeepInArguments(node: Concrete.Pattern, element: ArendPattern) : Concrete.Pattern? {
    if (node.data == element.skipSingleTuples()) {
        return node
    }
    if (node !is Concrete.ConstructorPattern) {
        return node.takeIf { it.data == element.skipSingleTuples() }
    }
    return node.patterns.firstNotNullOfOrNull { findDeepInArguments(it, element) }
}

private fun findParentConcrete(node: Concrete.Pattern, element: ArendPattern) : Concrete.Pattern? {
    if (node.data == element.skipSingleTuples()) {
        error("Descended too deep")
    }
    if (node !is Concrete.ConstructorPattern) {
        return null
    }
    return if (node.patterns.any { it.data == element.skipSingleTuples() }) {
        node
    } else {
        node.patterns.firstNotNullOfOrNull { findParentConcrete(it, element) }
    }
}


private tailrec fun ArendPattern.skipSingleTuples() : ArendPattern {
    return if (this.type != null && this.sequence.size == 1) {
        this.sequence[0].skipSingleTuples()
    } else if (this.isTuplePattern && this.sequence.size == 1) {
        this.sequence[0].skipSingleTuples()
    } else {
        this
    }
}