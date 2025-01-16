package org.arend.codeInsight

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import org.arend.core.definition.ClassDefinition
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.resolving.DataLocatedReferable
import org.arend.resolving.FieldDataLocatedReferable
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import org.arend.util.patternToConcrete
import java.util.Collections.singletonList
import kotlin.IllegalStateException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ArendCodeInsightUtils {
    companion object {
        data class ParameterInfo(
            val parameters: List<ParameterDescriptor>,
            val parameterOwner: ArendReferenceContainer?,
            val parameterIndex: Int,
            val externalParametersOk: Boolean
        )

        fun getThisParameter(def: PsiReferable): ParameterDescriptor? {
            if (def is ArendDefClass || def is ArendDefModule) return null //Classes and modules never have leading this parameters
            val defAncestors = (def as? PsiElement)?.ancestors?.toList()?.filterIsInstance<PsiReferable>()

            val containingClass = (def as? PsiElement)?.ancestor<ArendDefClass>()?.let { defClass ->
                if (def is ArendClassField || def is ArendFieldDefIdentifier || defAncestors != null && defAncestors.any {
                        defClass.dynamicSubgroups.contains<PsiElement>(
                            it
                        )
                    }) defClass else null
            }

            return containingClass?.let { DefaultParameterDescriptorFactory.createThisParameter(it) }
        }

        fun getThisParameterAsList(def: PsiReferable): List<ParameterDescriptor> {
            val option = getThisParameter(def)
            if (option == null) return emptyList() else {
                val result = ArrayList<ParameterDescriptor>()
                result.add(option)
                return result
            }
        }

        /**
         * Calculates the list of implicit data parameters associated with a constructor.
         * This list starts with the list of external parameters of the parent datatype
         * Optionally, it can precompute the list of implicit data parameters after ChangeSignatureRefactoring is applied to the constructor's parent datatype.
         *
         * @param constructor            constructor, whose implicit data parameters are to be computed
         * @param dataOldParameters      list of parameters of the parent datatype starting with external ones, it is allowed to omit all internal parameters if dataNewParameters == null
         * @param dataNewParameters      (optional) list of parameters of constructor's datatype after change signature refactoring
         *                                 Each NewParameter.oldParameter reference should point to one of the elements of dataOldParameters list.
         **
         * @return a Pair<List, List?> whose first component is the calculated list of data parameters.
         *         The second list contains the precalculated data parameters after ChangeSignatureRefactoring is performed upon the datatype.
         *         It is guaranteed that NewParameter.oldParameter of every item in this second list refers to some element of the first list.
         */
        fun getPartialExpectedConstructorSignature(
            constructor: ArendConstructor,
            dataOldParameters: List<ParameterDescriptor>,
            dataNewParameters: List<ParameterDescriptor>? = null
        ): Pair<List<ParameterDescriptor>, List<ParameterDescriptor>?> {
            val params = ArrayList<ParameterDescriptor>()
            val newParameters = ArrayList<ParameterDescriptor>()
            val data =
                (constructor.parent?.parent as? ArendDefData) ?: (constructor.parent?.parent?.parent as? ArendDefData)
                ?: throw java.lang.IllegalStateException()
            val patternsMap =
                HashMap<ParameterDescriptor /* one of dataOldParameters */, List<ParameterDescriptor> /* newly created constructor parameters with isDataParameter=true */>()

            //Since external parameters can not be eliminated, they should appear first in both cases (simple constructor/constructor defined via PM)
            var firstInternalIndex = -1
            for ((index, eP) in dataOldParameters.withIndex()) {
                if (eP.isExternal()) {
                    if (firstInternalIndex != -1) throw AssertionError() // external parameters should occur as a starting segment of dataOldParameters
                    val referable =
                        eP.getReferable() as Referable // safe to write since external parameters should all have this field initialised
                    val newPD = DefaultParameterDescriptorFactory.createNamedDataParameter(referable, data)
                    patternsMap[eP] = singletonList(newPD)
                    params.add(newPD)
                } else if (firstInternalIndex == -1) firstInternalIndex = index
            }
            if (firstInternalIndex == -1) firstInternalIndex = dataOldParameters.size

            if (constructor.parent?.parent is ArendDefData) { //Case I: Simple constructor
                val containingData = constructor.parent.parent as ArendDefData
                for (tele in containingData.parameters)
                    for (p in tele.referableList)
                        params.add(
                            if (p is PsiReferable) DefaultParameterDescriptorFactory.createNamedDataParameter(p, containingData) else
                                DefaultParameterDescriptorFactory.createUnnamedDataParameter(tele.type?.text)
                        )

                if (dataNewParameters != null) {
                    assert(params.size == dataOldParameters.size)
                    for ((p1, p2) in dataOldParameters.zip(params).drop(firstInternalIndex))
                        if (p1.getReferable() != p2.getReferable()) throw AssertionError()

                    firstInternalIndex = -1
                    for ((index, dataNewParameter) in dataNewParameters.withIndex()) {
                        if (!dataNewParameter.isExternal()) firstInternalIndex = index
                        if (dataNewParameter.isExternal() && firstInternalIndex != -1) throw AssertionError()

                        val oldIndex = if (dataNewParameter.oldParameter == null) -1 else {
                            val i = dataOldParameters.indexOf(dataNewParameter.oldParameter)
                            if (i == -1) throw AssertionError() // oldParameter if specified should be an element of dataOldParameters
                            i
                        }
                        val oldParameter = if (oldIndex != -1) params[oldIndex] else null
                        newParameters.add(DefaultParameterDescriptorFactory.createDataParameter(oldParameter, dataNewParameter.getExternalScope(), dataNewParameter.getNameOrNull(), dataNewParameter.typeGetter, dataNewParameter.getReferable() as? PsiReferable))
                    }
                }
            } else { // Case 2: Constructor defined via pattern matching
                val constructorClause = constructor.parent as? ArendConstructorClause
                val dataBody = constructorClause?.parent as? ArendDataBody
                val elim = dataBody?.elim ?: throw IllegalStateException()

                val concreteData: Concrete.GeneralDefinition = ConcreteBuilder.convert(data, null, CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE))
                if (concreteData !is Concrete.DataDefinition) throw IllegalStateException()

                val clause = concreteData.constructorClauses.firstOrNull { clause ->
                    clause.constructors.map { constructor ->
                        (constructor.data as? DataLocatedReferable)?.data?.element
                    }.contains(constructor)
                }
                val clausePatterns = clause?.patterns?.run {
                    val newList = ArrayList(this)
                    ExpressionResolveNameVisitor(
                        data.scope,
                        mutableListOf(),
                        DummyErrorReporter.INSTANCE,
                        null
                    ).visitPatterns(newList, mutableMapOf())
                    newList
                } ?: throw IllegalStateException()

                fun collectNamePatterns(pattern: Concrete.Pattern): List<Concrete.NamePattern> =
                    if (pattern is Concrete.NamePattern) singletonList(pattern) else pattern.patterns.map {
                        collectNamePatterns(
                            it
                        )
                    }.flatten()

                fun collectNamePatternDescriptors(pattern: Concrete.Pattern): List<ParameterDescriptor> =
                    collectNamePatterns(pattern).map { nP ->
                        val psiReferable = nP.referable?.underlyingReferable as? PsiReferable
                        if (psiReferable != null)
                            DefaultParameterDescriptorFactory.createNamedDataParameter(psiReferable, data) else
                            DefaultParameterDescriptorFactory.createUnnamedDataParameter(null) // In principle, we could try to `guess` names of these unnamed parameters
                    }

                when {
                    elim.withKw != null -> { //Case 2.1 pattern matching via `\with` construction
                        val dataArgs =
                            data.parameters.map { it.referableList.map { r -> Pair(r, it.isExplicit) } }.flatten()
                        var i = 0
                        var j = 0; while (i < clausePatterns.size && j < dataArgs.size) {
                            val argument = dataArgs[j]
                            val pattern = clausePatterns[i]
                            val oldArgument = dataOldParameters.getOrNull(i + firstInternalIndex)
                            if (pattern.isExplicit == argument.second) {
                                val patternReferablesDescriptors = collectNamePatternDescriptors(pattern)
                                if (oldArgument != null) patternsMap[oldArgument] = patternReferablesDescriptors
                                params.addAll(patternReferablesDescriptors)

                                i++
                                j++
                            } else if (!argument.second) {
                                j++
                            } else break
                        }

                        while (i < j) {
                            val oldArgument = dataOldParameters.getOrNull(i + firstInternalIndex)
                            if (oldArgument != null) {
                                val referable = oldArgument.getReferable()
                                val parameter = if (referable is Referable)
                                    DefaultParameterDescriptorFactory.createNamedDataParameter(referable, data) else
                                    DefaultParameterDescriptorFactory.createUnnamedDataParameter(oldArgument.getType())
                                patternsMap[oldArgument] = singletonList(parameter)
                                params.add(parameter)
                            }
                            i++
                        }

                    }

                    elim.elimKw != null -> { //Case 2.2 pattern matching via `\elim` construction
                        val dataParams = data.parameters.map { it.referableList }.flatten()
                        val eliminatedParams = elim.refIdentifierList.map { it.resolve as ArendDefIdentifier }

                        dataParams.withIndex().forEach { (index, referable) ->
                            val oldParameter = dataOldParameters.getOrNull(index + firstInternalIndex)
                            if (oldParameter != null && oldParameter.getReferable() != referable) throw AssertionError()

                            val elimIndex = if (referable != null) eliminatedParams.indexOf(referable) else -1
                            val patternParameterDescriptors =
                                if (elimIndex == -1 || elimIndex >= clausePatterns.size) { //Not eliminated parameter
                                    singletonList(
                                        if (referable is PsiReferable)
                                            DefaultParameterDescriptorFactory.createNamedDataParameter(
                                                referable,
                                                data
                                            ) else //Named not eliminated parameter
                                            DefaultParameterDescriptorFactory.createUnnamedDataParameter(null)
                                    ) //Unnamed not eliminated parameter
                                } else collectNamePatternDescriptors(clausePatterns[elimIndex]) // Eliminated parameter
                            params.addAll(patternParameterDescriptors)
                            if (oldParameter != null) patternsMap[oldParameter] = patternParameterDescriptors
                        }
                    }
                }

                if (dataNewParameters != null) {
                    firstInternalIndex = -1
                    for ((index, nP) in dataNewParameters.withIndex()) {
                        if (!nP.isExternal()) firstInternalIndex = index
                        if (firstInternalIndex != -1 && nP.isExternal()) throw AssertionError()

                        val oldParameter = nP.oldParameter
                        if (oldParameter == null) { // newly created internal data parameter, there will be an autogenerated "_" dummy pattern for it
                            newParameters.add(DefaultParameterDescriptorFactory.createDataParameter(null, nP.getExternalScope(), nP.getNameOrNull(), nP.typeGetter, null))
                        } else {
                            if (!dataOldParameters.contains(oldParameter)) throw AssertionError()
                            val patternParameterDescriptors = patternsMap[oldParameter] ?: emptyList()
                            newParameters.addAll(patternParameterDescriptors.map {
                                DefaultParameterDescriptorFactory.createDataParameter(it, nP.getExternalScope(), nP.getNameOrNull(), nP.typeGetter, nP.oldParameter.getReferable() as? PsiReferable)})
                        }
                    }
                }
            }

            return Pair(params, if (dataNewParameters == null) null else newParameters)
        }

        fun getParameterList(def: Abstract.ParametersHolder,
                             addTailParameters: Boolean = false,
                             predefinedExternalParameters: List<ParameterDescriptor>? = null): Pair<List<ParameterDescriptor>?, Boolean> {
            val externalParameters = predefinedExternalParameters ?: if (def is PsiLocatedReferable) getExternalParameters(def) else null
            val externalParametersOrEmpty = externalParameters ?: emptyList()

            val parameters = when (def) {
                is ArendDefFunction -> getThisParameterAsList(def) + externalParametersOrEmpty + DefaultParameterDescriptorFactory.createFromTeles(def.parameters) + (if (addTailParameters) getTailParameters(def.resultType) else emptyList())

                is ArendDefInstance -> getThisParameterAsList(def) + externalParametersOrEmpty + DefaultParameterDescriptorFactory.createFromTeles(def.parameters) + (if (addTailParameters) getTailParameters(def.resultType) else emptyList())

                is ArendConstructor -> getThisParameterAsList(def) + getPartialExpectedConstructorSignature(def, externalParametersOrEmpty).first + DefaultParameterDescriptorFactory.createFromTeles(def.parameters)

                is ArendDefData -> getThisParameterAsList(def) + externalParametersOrEmpty + DefaultParameterDescriptorFactory.createFromTeles(def.parameters)

                is ArendClassField -> getThisParameterAsList(def) + DefaultParameterDescriptorFactory.createFromTeles(def.parameters) + (if (addTailParameters) getTailParameters(def.resultType) else emptyList())

                is ArendDefClass -> return getClassParameterList(def, externalParameters, DefaultParameterDescriptorFactory)

                is ArendFieldDefIdentifier -> getThisParameterAsList(def) + (if (addTailParameters) getTailParameters(def.resultType) else emptyList())

                else -> null
            }
            return Pair(parameters, externalParameters != null || def is ArendClassField || def is ArendFieldDefIdentifier)
        }

        fun getExternalParameters(def: PsiLocatedReferable): List<ParameterDescriptor>? {
            val tcDef = (def.tcReferable as? TCDefReferable)?.typechecked
            if (tcDef == null)
                for (p in def.ancestors)
                    if (p is ArendDefinition<*> /* TODO[server2]: && p.externalParameters.isNotEmpty() */)
                        return null

            if (tcDef != null) {
                return tcDef.parametersOriginalDefinitions.map {
                    val definitionContainingExternalParameter = ((it.proj1?.data as? SmartPsiElementPointer<*>)?.element as? Abstract.ParametersHolder)
                    val externalParameters = definitionContainingExternalParameter?.parameters?.map { tele ->
                        when (tele) {
                            is ArendTypeTele ->
                                if (tele.typedExpr?.identifierOrUnknownList.isNullOrEmpty()) singletonList(null) else
                                    tele.typedExpr?.identifierOrUnknownList!!.map { iou -> iou.defIdentifier?.let { defIdentifier ->
                                        DefaultParameterDescriptorFactory.createExternalParameter(defIdentifier) }
                                    }

                            is ArendNameTele ->
                                if (tele.identifierOrUnknownList.isEmpty()) singletonList(null) else
                                    tele.identifierOrUnknownList.map { iou -> iou.defIdentifier?.let { defIdentifier ->
                                        DefaultParameterDescriptorFactory.createExternalParameter(defIdentifier)
                                    } }

                            else ->
                                throw java.lang.IllegalArgumentException()
                        }
                    }?.flatten()
                    val externalParameter = externalParameters?.getOrNull(it.proj2) ?: return null

                    externalParameter
                }
            }

            return emptyList()
        }

        fun computeParameterInfo(file: PsiFile, caretOffset: Int, parameterOwner: Any? = null): ParameterInfo? {
            val offset = adjustOffset(file, caretOffset)
            var currentNode = skipWhitespaces(file, offset)
            var applicationConcrete: Concrete.SourceNode? = null
            val rangeData = HashMap<Concrete.SourceNode, TextRange>()

            var argumentTextRange: TextRange? = null
            var data: Pair<List<ParameterDescriptor>, Boolean>? = null
            var afterExpression = false

            //Stage 1: Determine appropriate expression roots for the selected concrete
            do {
                val errorReporter = CountingErrorReporter(GeneralError.Level.ERROR, DummyErrorReporter.INSTANCE)
                val curNodeParent = currentNode?.parent

                if (currentNode != null && curNodeParent is CoClauseBase && curNodeParent.implementation != currentNode && (parameterOwner == null || parameterOwner == curNodeParent.longName))
                    return computeCoClauseParameterInfo(currentNode, offset)

                val rootConcrete = when (currentNode) {
                    is ArendArgumentAppExpr -> appExprToConcrete(currentNode, false, errorReporter)?.let {
                        if (errorReporter.errorsNumber == 0) it else null
                    }

                    is ArendPattern -> patternToConcrete(currentNode, errorReporter)?.let {
                        if (errorReporter.errorsNumber == 0) it else null
                    }

                    else -> null
                }

                if (currentNode is CoClauseBase) break

                if (currentNode != null &&
                    (rootConcrete is Concrete.AppExpression ||
                            rootConcrete is Concrete.ConstructorPattern ||
                            rootConcrete is Concrete.ReferenceExpression && rootConcrete.referent is GlobalReferable ||
                            rootConcrete is Concrete.LamExpression && rootConcrete.parameters.size == 1)) {
                    rangeData.clear()
                    getBounds(rootConcrete, currentNode.node.getChildren(null).toList(), rangeData)

                    val rangeDataWithSpaces = HashMap<Concrete.SourceNode, TextRange>()
                    rangeDataWithSpaces.putAll(rangeData)
                    for (k in rangeData.entries) {
                        val psi = file.findElementAt(k.value.startOffset)
                        val ancestors = ArrayList<PsiElement>()
                        if (psi != null) for (a in psi.ancestors) if (a.startOffset == psi.startOffset) ancestors.add(a) else break
                        val whitespace = ancestors.map { it.getWhitespace(SpaceDirection.LeadingSpace) }.firstOrNull{ !it.isNullOrEmpty() }
                        if (whitespace != null && whitespace.length > 1) rangeDataWithSpaces[k.key] = TextRange(k.value.startOffset - whitespace.length + 1, k.value.endOffset)
                    }

                    for (concrete in rangeDataWithSpaces.keys.toList())
                        if (concrete is Concrete.AppExpression)
                            correctDynamicUsage(concrete, rangeDataWithSpaces)

                    val orderedCaretNeighborhoods = rangeDataWithSpaces.toList().filter{ it.second.startOffset <= caretOffset && caretOffset <= it.second.endOffset }.sortedBy { it.second.length }
                    afterExpression = rangeData.all { it.value.endOffset < caretOffset }
                    val parameterOwnerIsValid = parameterOwner != null && orderedCaretNeighborhoods.mapNotNull { getData(it.first) }.contains(parameterOwner)

                    if (afterExpression) {
                        data = getParameters(rootConcrete)
                        argumentTextRange = null
                        applicationConcrete = rootConcrete
                    } else {
                        for (entry in orderedCaretNeighborhoods) if (entry.first !is Concrete.LamExpression) {
                            applicationConcrete = entry.first
                            data = getParameters(applicationConcrete)
                            val isProperOverlap = rangeData[entry.first]?.let { it.startOffset <= caretOffset && caretOffset <= it.endOffset } ?: false
                            if ((data?.first?.isNotEmpty() == true && isProperOverlap || data?.second == false) &&
                                (!parameterOwnerIsValid || getData(applicationConcrete) == parameterOwner)) break
                            argumentTextRange = rangeData[applicationConcrete]
                        }
                    }

                    if (data?.first?.isNotEmpty() == true && (parameterOwner == null || getData(applicationConcrete) == parameterOwner))
                        break
                }
                currentNode = curNodeParent
            } while (currentNode != null)


            return doComputeParameterInfo(applicationConcrete, data?.first ?: return null, data.second, afterExpression, argumentTextRange, rangeData)
        }

        fun getParameters(concrete: Concrete.SourceNode): Pair<List<ParameterDescriptor>, Boolean>? {
            val concreteReferent = when (concrete) {
                is Concrete.AppExpression -> (concrete.function as? Concrete.ReferenceExpression)?.referent
                is Concrete.ConstructorPattern -> (concrete.constructor as DataLocatedReferable).data?.element
                is Concrete.ReferenceExpression -> concrete.referent
                else -> null
            }
            return if (concreteReferent != null)
                getAllParametersForReferable(concreteReferent, getData(concrete) as ArendCompositeElement, addTailParameters = true) else
                null
        }

        fun getData(concrete: Concrete.SourceNode?): ArendReferenceContainer? = when (concrete) {
            is Concrete.AppExpression -> (concrete.function as? Concrete.ReferenceExpression)?.data as? ArendReferenceContainer
            is Concrete.ConstructorPattern -> (concrete.constructorData as ArendPattern).childOfType<ArendReferenceContainer>()
            is Concrete.ReferenceExpression -> concrete.data as? ArendReferenceContainer
            else -> null
        }

        fun getAllParametersForReferable(referable: Referable, anchor: ArendCompositeElement?, addTailParameters: Boolean = false): Pair<List<ParameterDescriptor>, Boolean>? {
            val def: Referable = if (referable is RedirectingReferable) referable.originalReferable else referable
            val result = ArrayList<ParameterDescriptor>()
            var resType: ArendExpr? = null
            if (def !is Abstract.ParametersHolder) {
                if (def is ArendDefIdentifier && def.parent is ArendIdentifierOrUnknown) {
                    val gParent = def.parent.parent
                    val tele = if (gParent is ArendTypedExpr) gParent.parent else gParent
                    resType = when (tele) {
                        is ArendNameTele -> tele.type
                        is ArendTypeTele -> tele.type
                        else -> null
                    }
                } else if (def is ArendDefIdentifier && def.parent is ArendLetClause) {
                    val clause = def.parent as ArendLetClause
                    resType = clause.resultType
                    result.addAll(DefaultParameterDescriptorFactory.createFromTeles(clause.parameters))
                }

                if (addTailParameters) result.addAll(getTailParameters(resType))
                return Pair(result, true)
            } else {
                val data = getParameterList(def, addTailParameters)
                val parametersList = data.first ?: return null
                if (anchor == null) result.addAll(parametersList) else
                    result.addAll(SignatureUsageContext.getParameterContext(anchor).filterParameters(parametersList))
                return Pair(result, data.second)
            }

        }

        private fun getClassParameterList(def: ArendDefClass, externalParameters: List<ParameterDescriptor>?, parameterDescriptorFactory: ParameterDescriptor.Companion.Factory = DefaultParameterDescriptorFactory): Pair<List<ParameterDescriptor>, Boolean> {
            val externalParametersMap = HashMap<String, ParameterDescriptor>()
            if (externalParameters != null) for (eP in externalParameters) eP.name?.let{ externalParametersMap[it] = eP }

            val result = (def.tcReferable?.typechecked as? ClassDefinition)?.notImplementedFields?.map {
                val psiReferable = (it.referable as? FieldDataLocatedReferable)?.data?.element as? PsiReferable
                val classParameterKind = when {
                    psiReferable is ArendClassField -> ClassParameterKind.CLASS_FIELD
                    (it.parentClass.referable as? DataLocatedReferable)?.data?.element == def -> ClassParameterKind.OWN_PARAMETER
                    else -> ClassParameterKind.INHERITED_PARAMETER
                }

                val sampleDescriptor = externalParametersMap[it.name]
                if (sampleDescriptor != null)
                    parameterDescriptorFactory.createExternalParameter(sampleDescriptor.getReferable() as Referable, sampleDescriptor.typeGetter, classParameterKind)
                else {
                    if (psiReferable != null)
                        parameterDescriptorFactory.createFromReferable(psiReferable, it.referable.isExplicitField, classParameterKind = classParameterKind)
                    else
                        ParameterDescriptor(
                            it.name,
                            it.referable.isExplicitField,
                            it.resultType.toString(),
                            classParameterKind
                        )
                }
            }

            return if (result != null) Pair(result, true) else Pair(ClassReferable.Helper.getNotImplementedFields(def).map {
                val classParameterKind = when {
                    it is ArendClassField -> ClassParameterKind.CLASS_FIELD
                    (it as? ArendFieldDefIdentifier)?.ancestor<ArendDefClass>() == def -> ClassParameterKind.OWN_PARAMETER
                    else -> ClassParameterKind.INHERITED_PARAMETER
                }
                parameterDescriptorFactory.createFromReferable(it, classParameterKind = classParameterKind)
            }.toList(), false)
        }

        private fun computeCoClauseParameterInfo(node: PsiElement, offset: Int): ParameterInfo? {
            val localCoClause = node.parent as CoClauseBase
            val arguments = localCoClause.lamParameters
            val referable = localCoClause.longName?.resolve as? Referable ?: return null
            val data = getAllParametersForReferable(referable, localCoClause.longName, addTailParameters = true)
            val parameters = data?.first

            var paramIndex = 0
            var argIndex = 0
            var resultingParamIndex = -1

            if (parameters == null) return null

            while ((paramIndex < parameters.size) && argIndex < arguments.size) {
                val param = parameters[paramIndex]
                val arg = arguments[argIndex]
                val argIsExplicit = when (arg) {
                    is ArendNameTele -> arg.isExplicit
                    is ArendPattern -> arg.isExplicit
                    else -> !(arg as PsiElement).text.trim().startsWith("{")
                }

                if (argIsExplicit == param.isExplicit) {
                    if ((arg as PsiElement).textRange.startOffset <= offset && offset <= arg.textRange.endOffset) {
                        resultingParamIndex = paramIndex
                        break
                    }
                    paramIndex++
                    argIndex++
                } else if (!param.isExplicit) {
                    paramIndex++
                }
                else return null
            }

            return ParameterInfo(parameters, localCoClause.longName, resultingParamIndex, true)
        }

        private fun correctDynamicUsage(concrete: Concrete.AppExpression, receiver: HashMap<Concrete.SourceNode, TextRange>) {
            val dynamicUsage = concrete.arguments.firstOrNull()?.let { arg -> !arg.isExplicit && arg.expression.data == concrete.function.data } == true

            val classFieldTextRange = if (dynamicUsage) (concrete.function.data as? ArendLongName)?.refIdentifierList?.last()?.textRange else null
            val dotExprTextRange = receiver[concrete.function]
            val classInstanceTextRange = if (classFieldTextRange != null && dotExprTextRange != null && dotExprTextRange.startOffset < classFieldTextRange.startOffset)
                TextRange(dotExprTextRange.startOffset, classFieldTextRange.startOffset) else null
            if (dynamicUsage && classFieldTextRange != null && classInstanceTextRange != null) {
                receiver[concrete.function] = classFieldTextRange
                receiver[concrete.arguments.first().expression] = classInstanceTextRange
            }
        }

        private fun isClosingElement(element: PsiElement?) =
            when (element.elementType) {
                null, ArendElementTypes.RPAREN, ArendElementTypes.RBRACE, ArendElementTypes.COMMA -> true
                else -> false
            }

        private fun adjustOffset(file: PsiFile, offset: Int) =
            if (isClosingElement(file.findElementAt(offset))) offset - 1 else offset

        private fun skipWhitespaces(file: PsiFile, offset: Int): PsiElement? {
            var shiftedOffset = offset
            var res:PsiElement?

            do {
                res = file.findElementAt(shiftedOffset)
                --shiftedOffset
            } while (res is PsiWhiteSpace)

            if (res?.parentOfType<ArendSourceNode>(false) is ArendDefFunction) {
                shiftedOffset = offset
                do {
                    res = file.findElementAt(shiftedOffset)
                    ++shiftedOffset
                } while (res is PsiWhiteSpace)
            }

            return res
        }

        private fun doComputeParameterInfo(applicationConcreteVal: Concrete.SourceNode?,
                                           parameters: List<ParameterDescriptor>,
                                           externalParametersOk: Boolean,
                                           afterExpression: Boolean,
                                           argumentTextRange: TextRange?,
                                           rangeDataReceiver: Map<Concrete.SourceNode, TextRange>): ParameterInfo? {
            val arguments: List<Any> = when (applicationConcreteVal) {
                is Concrete.AppExpression -> applicationConcreteVal.arguments
                is Concrete.ConstructorPattern -> applicationConcreteVal.patterns
                else -> emptyList()
            }

            var paramIndex = 0
            var argIndex = 0
            var resultingParamIndex = -1

            fun getExpression(arg: Any) =  when (arg) {
                is Concrete.Argument -> arg.expression
                is Concrete.Pattern -> arg
                else -> null
            }


            while ((afterExpression || paramIndex < parameters.size) && argIndex < arguments.size) {
                val param = parameters.getOrNull(paramIndex)
                val arg = arguments[argIndex]
                val argIsExplicit = when (arg) {
                    is Concrete.Argument -> arg.isExplicit
                    is Concrete.Pattern -> arg.isExplicit
                    else -> return null
                }

                val argExpression = getExpression(arg) ?: return null

                if (argIsExplicit == param?.isExplicit) {
                    if (rangeDataReceiver[argExpression] == argumentTextRange) {
                        resultingParamIndex = paramIndex
                        break
                    }
                    paramIndex++
                    argIndex++
                } else if (param?.isExplicit == false) {
                    paramIndex++
                } else if (!externalParametersOk && !argIsExplicit && param?.isExplicit == true) {
                    if (rangeDataReceiver[argExpression] == argumentTextRange) {
                        resultingParamIndex = -2
                        break
                    }
                    argIndex++
                } else
                    break
            }

            if (afterExpression && resultingParamIndex == -1)
                resultingParamIndex = paramIndex

            if (resultingParamIndex == -1 && !externalParametersOk && parameters.isEmpty()) {
                val isSomeArgumentSelected = arguments.isNotEmpty() &&
                        arguments.any { arg -> getExpression(arg)?.let{ rangeDataReceiver[it] == argumentTextRange} == true }
                if (isSomeArgumentSelected) resultingParamIndex = -2
            }

            return ParameterInfo(parameters, getData(applicationConcreteVal), resultingParamIndex, externalParametersOk)
        }

        private fun getTailParameters(resType: ArendExpr?): List<ParameterDescriptor> {
            var resTypeVar: ArendExpr? = resType
            val result = ArrayList<ParameterDescriptor>()
            while (resTypeVar != null) {
                resTypeVar = when (resTypeVar) {
                    is ArendArrExpr -> {
                        result.add(ParameterDescriptor(null, true, resTypeVar.domain?.text, classParameterKind = null))
                        resTypeVar.codomain
                    }
                    is ArendPiExpr -> {
                        result.addAll(resTypeVar.parameters.map { tele -> tele.referableList.map {
                            if (it != null) DefaultParameterDescriptorFactory.createFromReferable(it) else DefaultParameterDescriptorFactory.createUnnamedParameter(tele) }
                        }.flatten())
                        resTypeVar.codomain
                    }

                    is ArendAtomFieldsAcc -> resTypeVar.atom.tuple?.tupleExprList?.firstOrNull()?.exprIfSingle
                    else -> null
                }
            }
            return result
        }

    }

}