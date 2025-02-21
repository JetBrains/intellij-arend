package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.codeInsight.*
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendElementTypes
import org.arend.psi.ancestor
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.changeSignature.ArendChangeInfo.Companion.getDefinitionsWithExternalParameters
import org.arend.refactoring.changeSignature.ArendParametersInfo.Companion.getParameterInfo
import org.arend.refactoring.move.MoveRefactoringSignatureContext
import org.arend.search.ClassDescendantsSearch
import org.arend.term.abs.Abstract
import org.arend.term.abs.Abstract.ParametersHolder
import org.arend.term.group.AccessModifier
import java.util.*
import kotlin.collections.ArrayList

/**
 * This class encodes changes that need to be performed upon a definition during ChangeSignatureRefactoring
 * Possible changes to a definition include:
 *  - Modifying signature of the definition (parameter list + its elimination tree)
 *  - Modifying usages of the definition
 *  - Modifying name of the definition
 * */
class ChangeSignatureRefactoringDescriptor private constructor(val affectedDefinitionLink: SmartPsiElementPointer<PsiReferable>,
                                           val oldParameters: List<ParameterDescriptor>,
                                           val newParameters: List<ParameterDescriptor>,
                                           val newName: String? = null,
                                           val moveRefactoringContext: MoveRefactoringSignatureContext? = null) {
    constructor(affectedDefinition: PsiReferable,
                oldParameters: List<ParameterDescriptor>,
                newParameters: List<ParameterDescriptor>,
                newName: String? = null,
                moveRefactoringContext: MoveRefactoringSignatureContext? = null):
            this(createSmartLink(affectedDefinition)!!, oldParameters, newParameters, newName, moveRefactoringContext)

    private fun compare(distinguishByExplicitness: Boolean): Boolean {
        if (oldParameters.size != newParameters.size) return false

        for ((oldParam, newParam) in oldParameters.zip(newParameters)) {
            if (newParam.oldParameter != oldParam ||
                (distinguishByExplicitness && newParam.isExplicit != oldParam.isExplicit) ||
                (newParam.getExternalScope() != oldParam.getExternalScope())) return false
        }

        return true
    }

    fun isTrivial(): Boolean = compare(distinguishByExplicitness = true)

    fun getAffectedDefinition(): PsiReferable? = affectedDefinitionLink.element

    fun isSetOrOrderPreserved(): Boolean = compare(distinguishByExplicitness = false)

    fun fixEliminator() {
        when (val def = getAffectedDefinition()) {
            is ArendConstructor -> {
                val elim = def.elim
                if (elim != null && def.clauses.isNotEmpty())
                    fixElim(def.elim, elim.findPrevSibling()!!, def.clauses, oldParameters, newParameters)
            }
            is ArendDefData -> {
                val body = def.dataBody
                if (body != null && body.constructorClauseList.isNotEmpty())
                    fixElim(body.elim, body.findPrevSibling()!!, body.constructorClauseList, oldParameters, newParameters)
            }
            is ArendDefFunction -> {
                val body = def.body
                if (body != null && body.clauseList.isNotEmpty())
                    fixElim(body.elim, body.findPrevSibling()!!, body.clauseList, oldParameters, newParameters)
            }
        }
    }

    fun toParametersInfo(): ArendParametersInfo? {
        val properOldParameters = oldParameters.filter {
            !it.isThis() && !it.isExternal() && !it.isDataParameter && it.getReferable() !is ArendClassField && (it.classParameterKind == null || it.classParameterKind == ClassParameterKind.OWN_PARAMETER)
        }
        val properNewParameters = newParameters.filter {
            val referable = it.oldParameter?.getReferable()
            !it.isThis() && !it.isExternal() && !it.isDataParameter && referable !is ArendClassField && (it.classParameterKind == null || it.classParameterKind == ClassParameterKind.OWN_PARAMETER)
        }
        val parameterInfo = getParameterInfo(getAffectedDefinition() as? PsiLocatedReferable ?: return null)
        val newParameterInfo = ArrayList<ArendTextualParameter>()

        for (nP in properNewParameters) {
            val oldIndex = if (nP.oldParameter != null) properOldParameters.indexOf(nP.oldParameter) else -1
            val isClassifying = if (oldIndex == -1) false else parameterInfo[oldIndex].isClassifying
            val isCoerce = if (oldIndex == -1) false else parameterInfo[oldIndex].isCoerce
            val isProperty = if (oldIndex == -1) false else parameterInfo[oldIndex].isCoerce
            val accessModifier = if (oldIndex == -1) AccessModifier.PUBLIC else parameterInfo[oldIndex].accessModifier
            val correspondingReferable = if (oldIndex == -1) null else parameterInfo[oldIndex].correspondingReferable
            newParameterInfo.add(ArendTextualParameter(nP.getNameOrUnderscore(), nP.getType(), oldIndex, nP.isExplicit, isClassifying, isCoerce, isProperty, accessModifier, correspondingReferable))
        }

        return ArendParametersInfo(getAffectedDefinition() as PsiLocatedReferable, newParameterInfo)
    }

    companion object {
        private fun fixElim(
            elim: ArendElim?,
            whitespaceBeforeBody: PsiElement,
            clauses: List<Abstract.Clause>,
            oldParameters: List<ParameterDescriptor>,
            newParameters: List<ParameterDescriptor>
        ) {
            val newParametersFiltered = newParameters.filter { !it.isExternal() && it.getThisDefClass() == null }
            val oldParametersFiltered = oldParameters.filter { !it.isExternal() && !it.isThis() }
            val withMode = elim == null || elim.withKw != null

            val currentlyEliminatedParameters: List<ParameterDescriptor?> =
                if (elim == null || elim.withKw != null) {
                    oldParametersFiltered
                } else elim.refIdentifierList.map { refId ->
                    val defIdentiier = refId.reference?.resolve() as? ArendDefIdentifier
                    val pd = oldParametersFiltered.firstOrNull { it.getReferable() == defIdentiier }
                    pd
                }.toList()

            val newEliminatedParameters: List<ParameterDescriptor?> =
                if (withMode) newParametersFiltered else currentlyEliminatedParameters

            val preservedEliminatedParameters =
                newParametersFiltered.filter { it.oldParameter != null }.map { Pair(it.isExplicit, it.oldParameter) }
                    .filter { p -> currentlyEliminatedParameters.any { it == p.second } }
            val indicesOfDeletedParametersInCEP =
                currentlyEliminatedParameters.filter { p -> !preservedEliminatedParameters.any { it.second == p } }
                    .map { p -> currentlyEliminatedParameters.indexOf(p) }.sorted()

            val template = ArrayList<TemplateData>()
            if (withMode) {
                template.addAll(newEliminatedParameters.map { p ->
                    TemplateData(
                        if (p != null) oldParametersFiltered.indexOf(p.oldParameter) else -1,
                        p?.name, p?.isExplicit ?: true, false
                    )
                })
                for (d in indicesOfDeletedParametersInCEP) template.add(
                    TemplateData(
                        d,
                        null,
                        isExplicit = true,
                        isCommentedOut = true
                    )
                )
            } else {
                template.addAll(preservedEliminatedParameters.map { p ->
                    TemplateData(
                        currentlyEliminatedParameters.indexOfFirst { p.second == it },
                        null,
                        isExplicit = true,
                        isCommentedOut = false
                    )
                })
                for (d in indicesOfDeletedParametersInCEP) if (d <= template.size) template.add(
                    d,
                    TemplateData(d, null, isExplicit = true, isCommentedOut = true)
                )
            }

            val correctedElim =
                if (withMode) (if (newEliminatedParameters.isEmpty()) (if (elim == null) "" else "{-${elim.text}-}") else ArendElementTypes.WITH_KW.toString())
                else "${ArendElementTypes.ELIM_KW} ${
                    printWithComments(
                        currentlyEliminatedParameters,
                        template,
                        ""
                    ) { d, _ -> d.getNameOrUnderscore() }
                }"

            for (clause in clauses) if (clause.patterns.isNotEmpty()) {
                val lastPatternOffset = (clause.patterns.last() as PsiElement).endOffset
                val arrowEndOffset =
                    (clause as PsiElement).childrenWithLeaves.firstOrNull { (it as? PsiElement).elementType == ArendElementTypes.FAT_ARROW }?.endOffset
                        ?: lastPatternOffset
                val suffix = clause.containingFile.text.substring(lastPatternOffset, arrowEndOffset)

                var j = 0
                val clausePatternsWithHoles = ArrayList<ArendPattern?>()

                for (param in currentlyEliminatedParameters) {
                    var pattern = clause.patterns.getOrNull(j)
                    val paramIsExplicit = if (withMode) param?.isExplicit else true
                    if (pattern != null && pattern.isExplicit == paramIsExplicit) {
                        j++
                    } else if (param?.isExplicit == false) {
                        pattern = null
                    }
                    clausePatternsWithHoles.add(pattern as? ArendPattern)
                }

                val newPatterns = printWithComments(clausePatternsWithHoles, template, suffix) { p, b ->
                    if (b == p.isExplicit) p.text
                    else if (!b && p.isExplicit) "{${p.text}}" else {
                        val lbrace = p.childrenWithLeaves.first { it.elementType == ArendElementTypes.LBRACE }
                        val rbrace = p.childrenWithLeaves.first { it.elementType == ArendElementTypes.RBRACE }
                        p.containingFile.text.substring(lbrace.endOffset, rbrace.startOffset)
                    }
                }

                performTextModification(
                    clause,
                    newPatterns,
                    (clause.patterns.first() as PsiElement).startOffset,
                    arrowEndOffset
                )
            }
            if (elim != null) performTextModification(elim, correctedElim) else {
                performTextModification(
                    whitespaceBeforeBody,
                    " $correctedElim",
                    whitespaceBeforeBody.endOffset,
                    whitespaceBeforeBody.endOffset
                )
            }
        }

        private data class TemplateData(val oldIndex: Int, val name: String?, val isExplicit: Boolean, val isCommentedOut: Boolean)

        private fun <T> printWithComments(
            list: List<T?>,
            t: List<TemplateData>,
            suffix: String,
            converter: (T, Boolean) -> String
        ): String {
            val builder = StringBuilder()
            var isInComment = false
            var isAbsolutelyFirst = true
            var isFirstUncommented = true
            val hasAtLeastOneUncommented = !t.all { it.isCommentedOut }
            for (tt in t) {
                if (tt.isCommentedOut && !isInComment) {
                    builder.append("{-"); isInComment = true
                }
                if (!isAbsolutelyFirst && isFirstUncommented) builder.append(", ")
                if (isInComment && !tt.isCommentedOut) {
                    builder.append("-}"); isInComment = false
                }
                if (!isAbsolutelyFirst && !isFirstUncommented) builder.append(", ")
                builder.append(list.getOrNull(tt.oldIndex)?.let { converter.invoke(it, tt.isExplicit) }
                    ?:
                    if (tt.isExplicit) tt.name ?: "_" else
                        "{${tt.name ?: "_"}}"
                )
                isAbsolutelyFirst = false
                if (!tt.isCommentedOut) isFirstUncommented = false
            }
            if (isInComment) {
                if (!hasAtLeastOneUncommented) builder.append(suffix)
                builder.append("-}")
            }
            if (hasAtLeastOneUncommented) builder.append(suffix)
            return builder.toString()
        }

        /**
         *  Calculates the list of refactoring descriptors associated with a ChangeSignatureRefactoring operation.
         *
         *  Trivia: In Arend one change signature refactoring operation may require changes in the signatures of multiple definitions (and/or their usages).
         *  For example if the signature of a class is changed, the signatures of its descendants need to be changed as well.
         *  Alternatively, if parameter of a definition is changed, some change may be required also for all sub-definitions which use this parameter as an external parameter
         *
         *  @param locatedReferable
         *  @param oldParameters  List of parameters of a definition upon which the refactoring is originally invoked.
         *                        The starting segment of the list should be the list of external parameters of the definition.
         *                        The ending segment of the list is the list of own parameters of the definition
         *                        (e.g. own parameters of a constructor in the case of a constructor, list of fieldDefIdentifiers in the case of a class, etc.)
         *  @param newParameters  The list of parameters of the definition after the refactoring operation.
         *                        The list should start with the list of (after refactoring) external parameters.
         *                        NewParameter.oldParameter references of elements in this list should refer to elements of the first list
         *  @return the set of descriptors specifying modifications of individual definitions.
         * */
        fun getRefactoringDescriptors(
            locatedReferable: PsiLocatedReferable,
            newName: String,
            oldParameters: List<ParameterDescriptor>,
            newParameters: List<ParameterDescriptor>
        ): List<ChangeSignatureRefactoringDescriptor> {
            val refactoringDescriptors = ArrayList<ChangeSignatureRefactoringDescriptor>()
            when (locatedReferable) {
                is ArendClassField -> {
                    val thisParameter = ArendCodeInsightUtils.getThisParameter(locatedReferable) ?: throw IllegalStateException()
                    refactoringDescriptors.add(
                        ChangeSignatureRefactoringDescriptor(
                            locatedReferable,
                            Collections.singletonList(thisParameter) + oldParameters,
                            Collections.singletonList(
                                DefaultParameterDescriptorFactory.createThisParameter(
                                    thisParameter
                                )
                            ) + newParameters,
                            newName = if (locatedReferable.name != newName) newName else null
                        )
                    )
                }

                is ArendConstructor -> {
                    val thisParameter = ArendCodeInsightUtils.getThisParameterAsList(locatedReferable)
                    val thisNewParameter = thisParameter.firstOrNull()?.let {
                        Collections.singletonList(DefaultParameterDescriptorFactory.createThisParameter(it))
                    } ?: emptyList()
                    val data = locatedReferable.ancestor<ArendDefData>() ?: throw java.lang.IllegalStateException()
                    val dataParameters = (ArendCodeInsightUtils.getExternalParameters(data)
                        ?: emptyList()) + DefaultParameterDescriptorFactory.createFromTeles(data.parameters)
                    val calculatedSignature = ArendCodeInsightUtils.getPartialExpectedConstructorSignature(
                        locatedReferable,
                        dataParameters,
                        DefaultParameterDescriptorFactory.identityTransform(dataParameters)
                    )

                    refactoringDescriptors.add(
                        ChangeSignatureRefactoringDescriptor(
                            locatedReferable,
                            thisParameter + calculatedSignature.first + oldParameters,
                            thisNewParameter + calculatedSignature.second!! + newParameters,
                            newName = if (locatedReferable.name != newName) newName else null
                        )
                    )
                }

                is ArendDefClass -> {
                    val classDescendants = ClassDescendantsSearch(locatedReferable.project).getAllDescendants(
                        locatedReferable
                    )
                    for (classDescendant in classDescendants.filterIsInstance<ArendDefClass>().union(Collections.singletonList(
                        locatedReferable
                    ))) {
                        var modifiedArgumentStart = -1
                        var modifiedArgumentEnd = -1
                        val typecheckedNotImplementedFields = (classDescendant.tcReferable?.typechecked as? org.arend.core.definition.ClassDefinition)?.notImplementedFields
                        val externalParameterData = HashMap<String, ArendGroup>()
                        ArendCodeInsightUtils.getExternalParameters(classDescendant)?.forEach {
                            val externalScope = it.getExternalScope()
                            if (it.name != null && externalScope != null) externalParameterData[it.name] = externalScope
                        }

                        val descendantOldParameters: List<ParameterDescriptor>
                        val notImplementedFields = LinkedHashMap<String, ParameterDescriptor>()

                        if (typecheckedNotImplementedFields != null) {
                            descendantOldParameters = typecheckedNotImplementedFields.map {
                                val psiReferable = it.referable.data as? Referable
                                val (classParameterKind, externalScope) = when {
                                    psiReferable is ArendClassField -> Pair(ClassParameterKind.CLASS_FIELD, null)
                                    it.parentClass.referable?.data == classDescendant ->
                                        Pair(ClassParameterKind.OWN_PARAMETER, externalParameterData[it.name])
                                    else -> Pair(ClassParameterKind.INHERITED_PARAMETER, null)
                                }
                                if (psiReferable != null)
                                    DefaultParameterDescriptorFactory.createFromReferable(psiReferable, externalScope = externalScope, classParameterKind = classParameterKind)
                                else
                                    ParameterDescriptor(
                                        it.referable.refName,
                                        it.referable.isExplicitField,
                                        it.resultType.toString(),
                                        classParameterKind = classParameterKind,
                                        externalScope = externalScope
                                    )
                            }
                            /* TODO[server2]
                            for ((index, field) in typecheckedNotImplementedFields.withIndex())
                                if ((field.parentClass.referable as? DataLocatedReferable)?.data?.element == locatedReferable && (field.referable !is FieldDataLocatedReferable || field.referable.isParameterField)) {
                                    notImplementedFields[field.name] = descendantOldParameters[index]
                                    if (modifiedArgumentStart == -1) modifiedArgumentStart = index
                                    modifiedArgumentEnd = index
                                }
                            */
                        } else { // Fallback code for dumb mode
                            descendantOldParameters = emptyList() /* TODO[server2]: ClassReferable.Helper.getNotImplementedFields(classDescendant).filterIsInstance<PsiElement>().withIndex().map { (index, field) ->
                                val classParent = field.ancestor<ArendDefClass>()!!
                                val descriptor = DefaultParameterDescriptorFactory.createFromReferable(field as FieldReferable)
                                if (classParent == locatedReferable) {
                                    notImplementedFields[field.refName] = descriptor
                                    if (modifiedArgumentStart == -1) modifiedArgumentStart = index
                                    modifiedArgumentEnd = index
                                }
                                descriptor
                            } */
                        }

                        val prefix = if (modifiedArgumentStart > 0) descendantOldParameters.subList(0, modifiedArgumentStart) else emptyList()
                        val suffix = if (modifiedArgumentEnd + 1 < descendantOldParameters.size) descendantOldParameters.subList(modifiedArgumentEnd + 1, descendantOldParameters.size) else emptyList()

                        val centerPiece = newParameters.filter {parameterDescriptor ->
                            parameterDescriptor.oldParameter == null ||
                                    parameterDescriptor.isExternal() ||
                                    parameterDescriptor.oldParameter.isExternal() ||
                                    descendantOldParameters.mapNotNull { it.getReferable() }.toSet().contains( parameterDescriptor.oldParameter.getReferable() )
                        }.map {
                            val oldDescriptor: ParameterDescriptor? = it.oldParameter?.let { descriptor -> notImplementedFields[descriptor.name] }
                            val parameterKind =
                                if (classDescendant == locatedReferable) ClassParameterKind.OWN_PARAMETER else ClassParameterKind.INHERITED_PARAMETER
                            DefaultParameterDescriptorFactory.createNewParameter(it.isExplicit, oldDescriptor, oldDescriptor?.getExternalScope(), it.name, it.typeGetter, parameterKind)
                        }

                        val clazzNewParameters = prefix.map {
                            DefaultParameterDescriptorFactory.createNewParameter(it.isExplicit, it, it.getExternalScope(), it.getNameOrUnderscore(), it.typeGetter, it.classParameterKind)
                        } + centerPiece + suffix.map { DefaultParameterDescriptorFactory.createNewParameter(it.isExplicit, it, it.getExternalScope(), it.getNameOrUnderscore(), it.typeGetter, classParameterKind = it.classParameterKind) }
                        refactoringDescriptors.add(
                            ChangeSignatureRefactoringDescriptor(classDescendant, descendantOldParameters, clazzNewParameters, newName = if (locatedReferable == classDescendant) newName else null)
                        )
                    }
                }

                is ArendDefData, is ArendFunctionDefinition<*> -> {
                    val thisParameter = ArendCodeInsightUtils.getThisParameterAsList(locatedReferable)
                    val thisNewParameter =
                        thisParameter.firstOrNull()?.let { Collections.singletonList(DefaultParameterDescriptorFactory.createThisParameter(it)) }
                            ?: emptyList()
                    val mainRefactoringDescriptor = ChangeSignatureRefactoringDescriptor(
                        locatedReferable,
                        thisParameter + oldParameters,
                        thisNewParameter + newParameters,
                        newName = if (locatedReferable.name != newName) newName else null
                    )

                    refactoringDescriptors.add(mainRefactoringDescriptor)
                    val childDefinitions = getDefinitionsWithExternalParameters(locatedReferable as ParametersHolder)
                    for (childDef in childDefinitions) {
                        val childDefOldParameters =
                            ArendCodeInsightUtils.getParameterList(childDef as ParametersHolder).first!!
                        modifyExternalParameters(oldParameters, newParameters, childDef, childDefOldParameters)?.let {
                            refactoringDescriptors.add(it)
                        }
                    }

                    if (locatedReferable is ArendDefData && !mainRefactoringDescriptor.isSetOrOrderPreserved()) for (cons in locatedReferable.internalReferables) {
                        val constructorDataParameters =
                            ArendCodeInsightUtils.getPartialExpectedConstructorSignature(
                                cons,
                                oldParameters,
                                newParameters
                            )
                        val ownParameters = DefaultParameterDescriptorFactory.createFromTeles(cons.parameters)
                        val newOwnParameters = DefaultParameterDescriptorFactory.identityTransform(ownParameters)
                        refactoringDescriptors.add(
                            ChangeSignatureRefactoringDescriptor(
                                cons,
                                thisParameter + constructorDataParameters.first + ownParameters,
                                thisNewParameter + constructorDataParameters.second!! + newOwnParameters,
                            )
                        )
                    }

                }

                else -> throw NotImplementedError()
            }
            return refactoringDescriptors
        }
    }
}