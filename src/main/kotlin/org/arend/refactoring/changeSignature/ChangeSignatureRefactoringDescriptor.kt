package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.codeInsight.ParameterDescriptor
import org.arend.psi.ArendElementTypes
import org.arend.psi.childrenWithLeaves
import org.arend.psi.ext.*
import org.arend.psi.findPrevSibling
import org.arend.refactoring.changeSignature.ArendParametersInfo.Companion.getParameterInfo
import org.arend.term.abs.Abstract
import org.arend.term.group.AccessModifier

/**
 * This class encodes changes that need to be performed upon a definition during ChangeSignatureRefactoring
 * Possible changes to a definition include:
 *  - Modifying signature of the definition (parameter list + its elimination tree)
 *  - Modifying usages of the definition
 *  - Modifying name of the definition
 * */
class ChangeSignatureRefactoringDescriptor(val affectedDefinition: PsiReferable,
                                           val oldParameters: List<ParameterDescriptor>,
                                           val newParameters: List<ParameterDescriptor>,
                                           val newName: String? = null) {
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

    fun isSetOrOrderPreserved(): Boolean = compare(distinguishByExplicitness = false)

    fun fixEliminator() {
        when (val def = affectedDefinition) {
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
        val properOldParameters = oldParameters.filter { !it.isThis() && !it.isExternal() }
        val properNewParameters = newParameters.filter { !it.isThis() && !it.isExternal() }
        val parameterInfo = getParameterInfo(affectedDefinition as? PsiLocatedReferable ?: return null)
        if (properOldParameters.size != parameterInfo.size) return null
        val newParameterInfo = ArrayList<ArendTextualParameter>()

        for (nP in properNewParameters) {
            val oldIndex = if (nP.oldParameter != null) properOldParameters.indexOf(nP.oldParameter) else -1
            val isClassifying = if (oldIndex == -1) false else parameterInfo[oldIndex].isClassifying
            val isCoerce = if (oldIndex == -1) false else parameterInfo[oldIndex].isCoerce
            val isProperty = if (oldIndex == -1) false else parameterInfo[oldIndex].isCoerce
            val accessModifier = if (oldIndex == -1) AccessModifier.PUBLIC else parameterInfo[oldIndex].accessModifier
            val correspondingReferable = if (oldIndex == -1) null else parameterInfo[oldIndex].correspondingReferable
            newParameterInfo.add(ArendTextualParameter(nP.getNameOrUnderscore(), nP.getType1(), oldIndex, nP.isExplicit, isClassifying, isCoerce, isProperty, accessModifier, correspondingReferable))
        }

        return ArendParametersInfo(affectedDefinition, newParameterInfo)
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

            val currentlyEliminatedParameters: List<Pair<Boolean, ParameterDescriptor?>> =
                if (elim == null || elim.withKw != null) {
                    oldParameters.map { Pair(it.isExplicit, it) }
                } else elim.refIdentifierList.map {
                    val defIdentiier = it.reference.resolve() as? ArendDefIdentifier
                    val pd = oldParametersFiltered.firstOrNull { it.getReferable() == defIdentiier }
                    Pair(true, pd)
                }.toList()

            val newEliminatedParameters: List<Pair<Boolean, Any?>> =
                if (withMode) newParametersFiltered.map { Pair(it.isExplicit, it.oldParameter ?: it.getNameOrUnderscore()) }
                    .toList()
                else currentlyEliminatedParameters

            val preservedEliminatedParameters =
                newParametersFiltered.filter { it.oldParameter != null }.map { Pair(it.isExplicit, it.oldParameter) }
                    .filter { p -> currentlyEliminatedParameters.any { it.second == p.second } }
            val indicesOfDeletedParametersInCEP =
                currentlyEliminatedParameters.filter { p -> !preservedEliminatedParameters.any { it.second == p.second } }
                    .map { p -> currentlyEliminatedParameters.indexOfFirst { p.second == it.second } }.sorted()

            val template = ArrayList<TemplateData>()
            if (withMode) {
                template.addAll(newEliminatedParameters.map { p ->
                    TemplateData(
                        if (p.second is ParameterDescriptor) oldParametersFiltered.indexOf(
                            p.second
                        ) else -1, p.first, false
                    )
                })
                for (d in indicesOfDeletedParametersInCEP) template.add(
                    TemplateData(
                        d,
                        isExplicit = true,
                        isCommentedOut = true
                    )
                )
            } else {
                template.addAll(preservedEliminatedParameters.map { p ->
                    TemplateData(
                        currentlyEliminatedParameters.indexOfFirst { p.second == it.second },
                        isExplicit = true,
                        isCommentedOut = false
                    )
                })
                for (d in indicesOfDeletedParametersInCEP) if (d <= template.size) template.add(
                    d,
                    TemplateData(d, isExplicit = true, isCommentedOut = true)
                )
            }

            val correctedElim =
                if (withMode) (if (newEliminatedParameters.isEmpty()) (if (elim == null) "" else "{-${elim.text}-}") else ArendElementTypes.WITH_KW.toString())
                else "${ArendElementTypes.ELIM_KW} ${
                    printWithComments(
                        currentlyEliminatedParameters,
                        template,
                        ""
                    ) { (_, d), _ -> d?.getNameOrUnderscore() ?: "_" }
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
                    if (pattern != null && pattern.isExplicit == param.first) {
                        j++
                    } else if (!param.first) {
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

        private data class TemplateData(val oldIndex: Int, val isExplicit: Boolean, val isCommentedOut: Boolean)

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
                builder.append(list.getOrNull(tt.oldIndex)?.let { converter.invoke(it, tt.isExplicit) } ?: "_")
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
    }
}