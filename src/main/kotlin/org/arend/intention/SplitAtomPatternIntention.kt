package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.castSafelyTo
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
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.*
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.PsiConcreteProvider
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
            is ArendAtomPattern -> element.longName ?: element.ipName
            else -> null
        }
        val project = editor.project
        this.splitPatternEntries = null

        if (!(element is ArendPattern && element.atomPatternList.size == 0 || element is ArendAtomPattern)) return this.splitPatternEntries != null
        val type = getElementType(element, editor)?.let{ TypeConstructorExpression.unfoldType(it) }
        this.splitPatternEntries = when (type) {
            is DataCallExpression -> {
                val canDoPatternMatchingOnIdp = admitsPatternMatchingOnIdp(type, caseClauseParameters)
                if (project != null && canDoPatternMatchingOnIdp == PatternMatchingOnIdpResult.IDP) {
                    singletonList(IdpPatternEntry(project))
                } else if (canDoPatternMatchingOnIdp != PatternMatchingOnIdpResult.DO_NOT_ELIMINATE) {
                    val constructors = type.matchedConstructors ?: return false
                    constructors.map { ConstructorSplitPatternEntry(it.definition, defIdentifier?.referenceName, type.definition) }
                } else null
            }
            is SigmaExpression -> singletonList(TupleSplitPatternEntry(type.parameters))
            is ClassCallExpression -> {
                val definition = type.definition
                if (definition == Prelude.DEP_ARRAY) {
                    val isEmpty = ConstructorExpressionPattern.isArrayEmpty(type)
                    val result = ArrayList<SplitPatternEntry>()
                    for (p in arrayOf(Pair(true, Prelude.EMPTY_ARRAY), Pair(false, Prelude.ARRAY_CONS)))
                        if (isEmpty == null || isEmpty == p.first) result.add(ConstructorSplitPatternEntry(p.second, defIdentifier?.referenceName, type.definition))
                    result
                } else if (definition.isRecord)
                    singletonList(RecordSplitPatternEntry(type, definition))
                else null
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
            val body = ownerParent.ancestor<ArendFunctionBody>()
            val func = body?.parent
            abstractPatterns = patternOwner.patterns

            if (ownerParent is ArendFunctionClauses)
                clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
            else if (ownerParent is ArendCoClauseBody) {
                coClauseName = ownerParent.ancestor<ArendCoClause>()?.longName?.referenceName
                clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
            }

            if (body is ArendFunctionBody && func is ArendFunctionalDefinition) {
                definition = func as? TCDefinition
            }
        }
        if (patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
            /* val data = ownerParent.parent
            abstractPatterns = patternOwner.patterns
            if (data is ArendDefData) definition = data */
            return null // TODO: Implement some behavior for constructor clauses as well
        }

        if (definition != null && clauseIndex != -1) {
            var typeCheckedDefinition = definition.tcReferable?.typechecked
            val concreteFunction = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null).getConcreteFunction(definition) ?: return null
            var concreteClauseOwner = concreteFunction
            if (typeCheckedDefinition is FunctionDefinition && definition is Abstract.ParametersHolder && definition is Abstract.EliminatedExpressionsHolder && abstractPatterns != null) {
                if (coClauseName != null) {
                  val classCallExpression = typeCheckedDefinition.resultType as? ClassCallExpression
                  val expr = classCallExpression?.implementations?.firstOrNull { it.key.name == coClauseName }?.value
                  typeCheckedDefinition = ((expr as? LamExpression)?.body as? FunCallExpression)?.definition ?: typeCheckedDefinition
                  concreteClauseOwner = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null).getConcrete(typeCheckedDefinition.ref.underlyingReferable as GlobalReferable).castSafelyTo<Concrete.FunctionDefinition>() ?: concreteClauseOwner
                }

                val elimBody = (((typeCheckedDefinition as? FunctionDefinition)?.actualBody as? IntervalElim)?.otherwise
                        ?: ((typeCheckedDefinition as? FunctionDefinition)?.actualBody as? ElimBody) ?: return null)

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
                    val concreteClause = concreteClauseOwner.body.clauses[clauseIndex]
                    val index = patternOwner.patterns.filterIsInstance<PsiElement>().indexOfFirst { it == indexList[0] || it == indexList[0].parent }
                    val typecheckedPattern = if (isElim) elimVarPatterns.getOrNull(index) else findMatchingPattern(concreteClause.patterns, typeCheckedDefinition.parameters, patterns, indexList[0])
                    if (typecheckedPattern != null) {
                        val patternPart = findPattern(indexList.drop(1), typecheckedPattern, concreteClause.patterns.find { matches(it, indexList[0]) }!!) as? BindingPattern
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

            val topLevelPatterns = localClause.patterns.mapTo(mutableListOf()) {
                ConcreteBuilder.convertPattern(it, ArendReferableConverter, DummyErrorReporter.INSTANCE, null)
            }
            ExpressionResolveNameVisitor(ArendReferableConverter, localClause.scope, mutableListOf(), DummyErrorReporter.INSTANCE, null).visitPatterns(topLevelPatterns, mutableMapOf(), true)

            val localNames = HashSet<Variable>()
            localNames.addAll(findAllVariablePatterns(topLevelPatterns, element).map(::VariableImpl))

            if (element !is Abstract.Pattern) return

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

                val offsetOfReplaceablePsi = findReplaceablePsiElement(localIndexList.drop(1), topLevelPatterns.find { matches(it, localIndexList[0]) })?.data?.castSafelyTo<PsiElement>()?.startOffset ?: return
                val offsetOfCurrentAnchor = currAnchor.startOffset
                val relativeOffsetOfReplaceablePsi = offsetOfReplaceablePsi - offsetOfCurrentAnchor


                for (splitPatternEntry in splitPatternEntries) {
                    splitPatternEntry.initParams(localNames)
                    val patternString = splitPatternEntry.patternString(localClause)
                    val expressionString = splitPatternEntry.expressionString(localClause)

                    if (first) {
                        doSubstituteUsages(project, element.childOfType(), currAnchor, expressionString)

                        var inserted = false
                        if (splitPatternEntry is ConstructorSplitPatternEntry && (splitPatternEntry.constructor == Prelude.ZERO || splitPatternEntry.constructor == Prelude.FIN_ZERO)) {
                            var number = 0
                            val concretePattern = topLevelPatterns.find { matches(it, localIndexList[0]) }
                            var path = localIndexList.drop(1)
                            while (path.isNotEmpty()) {
                                val patternPiece = findReplaceablePsiElement(path.dropLast(1), concretePattern)
                                if (patternPiece?.data !is PsiElement || !isSucPattern(patternPiece)) break
                                path = path.dropLast(1)
                                number += 1
                            }
                            val psiToReplace = if (number == 0) {
                                localClause.findElementAt(relativeOffsetOfReplaceablePsi)?.parentOfType<ArendAtomPattern>()
                            } else {
                                localIndexList[path.size]
                            }
                            if (psiToReplace != null) {
                                doReplacePattern(factory, psiToReplace, number.toString(), false)
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
                            val elementCopy = currAnchor.findElementAt(relativeOffsetOfReplaceablePsi)?.parentOfType<ArendAtomPattern>()?.goUpIfImplicit()

//                            findReplaceablePsiElement(localIndexList.drop(1), topLevelPatterns.find { it.data == localIndexList[0] })
                            if (elementCopy != null) {
                                doSubstituteUsages(project, elementCopy.childOfType(), currAnchor, expressionString)
                                doReplacePattern(factory, elementCopy, patternString, splitPatternEntry.requiresParentheses())
                            }
                        }
                    }

                    first = false
                }
            }
        }

        private fun ArendAtomPattern.goUpIfImplicit() : ArendAtomPattern {
            if (parent.castSafelyTo<ArendPattern>()?.atomPatternList?.size != 1) {
                return this
            }
            val superParent = parent?.parent?.castSafelyTo<ArendAtomPattern>()?.takeIf { it.patternList.size == 1 } ?: return this
            return if (!superParent.isExplicit) {
                superParent
            } else {
                this
            }
        }

        fun isSucPattern(pattern: Concrete.Pattern): Boolean {
            if (pattern !is Concrete.ConstructorPattern) return false
            val typechecked = pattern.constructor.castSafelyTo<TCDefReferable>()?.typechecked
            return typechecked == Prelude.SUC || typechecked == Prelude.FIN_SUC
//            if (pattern !is ArendPatternImplMixin || pattern.sequence.size != 1) {
//                return false
//            }

//            val constructor = (pattern.singleReferable as? UnresolvedReference)?.resolve(pattern.ancestor<ArendDefinition>()?.scope ?: return false, null) as? ArendConstructor
//                    ?: return false
//            return constructor.name == Prelude.SUC.name && constructor.ancestor<ArendDefData>()?.tcReferable?.typechecked == Prelude.NAT ||
//                   constructor.name == Prelude.FIN_SUC.name && constructor.ancestor<ArendDefData>()?.tcReferable?.typechecked == Prelude.FIN

        }

        fun findAllVariablePatterns(patterns: List<Concrete.Pattern>, excludedPsi: PsiElement?): HashSet<String> {
            val result = HashSet<String>()

            for (pattern in patterns) doFindVariablePatterns(result, pattern, excludedPsi)
            result.remove(excludedPsi?.text)

            val function = excludedPsi?.parentOfType<ArendFunctionalDefinition>() ?: return result
            val elim = function.body?.elim ?: return result
            if (elim.elimKw != null) {
                val allParams = function.nameTeleList.flatMap { it.referableList.map(Referable::getRefName) }
                val eliminatedParams = elim.refIdentifierList.mapNotNullTo(HashSet()) { it.referenceName }
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
        fun locatePattern(element: PsiElement): Pair<Abstract.Clause, List<PsiElement>>? {
            var pattern: PsiElement? = null
            var patternOwner: PsiElement? = element
            val indexList = ArrayList<PsiElement>()

            indexList.add(element)

            while (patternOwner is ArendPattern || patternOwner is ArendAtomPattern || patternOwner is ArendAtomPattern) {
                pattern = patternOwner
                patternOwner = patternOwner.parent
                if (pattern is ArendPattern && (!(pattern.atomPatternList.flatMap { it.patternList }.size == 1 && pattern.isTuplePattern))) {
                    if (pattern.atomPatternList == listOf(indexList.last()) || pattern.atomPatternList.singleOrNull()?.patternList?.singleOrNull() == indexList.last()) {
                        continue
                    }
                    indexList.add(pattern)
                }
//                if (patternOwner is ArendPattern) {
//                    indexList.add(pattern)
//                }
//                if (patternOwner is ArendClause) indexList.add(patternOwner.patternList.indexOf(pattern))
//                if (patternOwner is ArendConstructorClause) indexList.add(0, patternOwner.patternList.indexOf(pattern))
//                if (patternOwner is ArendAtomPattern && patternOwner.patternList.size > 1) indexList.add(0, patternOwner.patternList.indexOf(pattern))
            }

            if (pattern == null) return null
            val clause: Abstract.Clause = patternOwner as? Abstract.Clause ?: return null
            return Pair(clause, indexList.reversed())
        }

        private fun findPattern(indexList: List<PsiElement>, typecheckedPattern: ExpressionPattern, concretePattern: Concrete.Pattern): ExpressionPattern? {
            if (indexList.isEmpty()) return typecheckedPattern
            if (typecheckedPattern is BindingPattern && !typecheckedPattern.binding.isExplicit && indexList.size == 1) {
                return typecheckedPattern
            }
            if (typecheckedPattern is ConstructorExpressionPattern) {
                val typecheckedPatternChild = findMatchingPattern(concretePattern.patterns, typecheckedPattern.parameters, typecheckedPattern.subPatterns, indexList[0])
                val abstractPatternChild = concretePattern.patterns.find { matches(it, indexList[0]) }
                if (typecheckedPatternChild != null && abstractPatternChild != null)
                    return findPattern(indexList.drop(1), typecheckedPatternChild, abstractPatternChild)
            }
            return null
        }

        private fun findReplaceablePsiElement(indexList: List<PsiElement>, concretePattern: Concrete.Pattern?): Concrete.Pattern? {
            if (indexList.isEmpty()) return concretePattern
            if (indexList.size == 1 && concretePattern is Concrete.NamePattern && !concretePattern.isExplicit) return concretePattern
            val concretePatternChild = concretePattern?.patterns?.find { matches(it, indexList[0]) }
            if (concretePatternChild != null) return findReplaceablePsiElement(indexList.drop(1), concretePatternChild)
            return null
        }

        private fun findMatchingPattern(concretePatterns: List<Concrete.Pattern>, parameters: DependentLink, typecheckedPatterns: List<ExpressionPattern>, requiredPsi: PsiElement): ExpressionPattern? {
            var link = parameters
            var i = 0
            var j = 0

            while (link.hasNext() && i < concretePatterns.size) {
                val isEqual = link.isExplicit == concretePatterns[i].isExplicit
                if (isEqual && matches(concretePatterns[i], requiredPsi)) return typecheckedPatterns.getOrNull(j)

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
            val replacementPattern: PsiElement? = when {
                elementToReplace is ArendPattern ->
                    factory.createClause(if (!elementToReplace.isExplicit) "{$pLine}" else if ((requiresParentheses || asExpression.isNotEmpty())) "($pLine)" else pLine).childOfType<ArendPattern>()
                elementToReplace.parent?.parent is Abstract.Clause && elementToReplace.parent.castSafelyTo<ArendPattern>()?.atomPatternList == listOf(elementToReplace) -> {
                    val replacement = factory.createClause(if (!(elementToReplace as ArendAtomPattern).isExplicit) "{$pLine}" else pLine).childOfType<ArendPattern>() ?: return null
                    return elementToReplace.parent.replaceWithNotification(replacement)
                }
                elementToReplace.parent?.parent?.let { it is ArendAtomPattern && !it.isExplicit && it.patternList.flatMap(ArendPattern::getAtomPatternList) == listOf(elementToReplace) } == true -> {
                    val replacement = factory.createClause(if (!(elementToReplace as ArendAtomPattern).isExplicit) "{$pLine}" else pLine).childOfType<ArendPattern>() ?: return null
                    return elementToReplace.parent.replaceWithNotification(replacement)
                }
                elementToReplace.parent?.parent?.castSafelyTo<ArendAtomPattern>()?.run { isTuplePattern && patternList.size > 1 } == true -> {
                    val replacement = factory.createClause(if (!(elementToReplace as ArendAtomPattern).isExplicit) "{$pLine}" else pLine).childOfType<ArendPattern>() ?: return null
                    return elementToReplace.parent.replaceWithNotification(replacement)
                }
                elementToReplace is ArendAtomPattern ->
                    factory.createAtomPattern(if (!elementToReplace.isExplicit) "{$pLine}" else if ((requiresParentheses || asExpression.isNotEmpty())) "($pLine)" else pLine)
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
                    "($substitutedExpression)${longNameTail.foldRight("") { ref, acc -> "$acc.${ref.referenceName}" }}"
                }
            }
        }
    }
}

private fun matches(concreteNode: Concrete.Pattern, element: PsiElement) : Boolean {
    return concreteNode.data == element ||
            (concreteNode is Concrete.TuplePattern && concreteNode.data.castSafelyTo<PsiElement>()?.parent == element) ||
            (!concreteNode.isExplicit && concreteNode.data.castSafelyTo<PsiElement>()?.parent?.parent?.let { it == element || it.parent == element } == true)
}