package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.intention.NameFieldApplier
import org.arend.intention.NewParameter
import org.arend.intention.Parameter
import org.arend.intention.RefactoringDescriptor
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.rename.ArendRenameProcessor
import java.util.Collections.singletonList


fun processFunction(project: Project, changeInfo: ArendChangeInfo, function: ArendDefFunction) {
    changeInfo.addNamespaceCommands()
    if (changeInfo.isParameterSetOrOrderChanged) {
        fixElim(function, changeInfo)
        modifyFunctionUsages(project, function, changeInfo.newParameters.toList().map { it as ArendParameterInfo })
    }

    if (changeInfo.isParameterNamesChanged)
        renameFunctionParameters(project, changeInfo, function)

    if (changeInfo.isNameChanged) {
        val renameProcessor = ArendRenameProcessor(project, function, changeInfo.newName, function.refName, false, null)
        val usages = renameProcessor.findUsages()
        renameProcessor.executeEx(usages)
    }

    if (changeInfo.isParameterNamesChanged || changeInfo.isParameterSetOrOrderChanged || changeInfo.isParameterTypesChanged)
        modifyFunctionSignature(function, changeInfo)
}
private fun fixElim(function: ArendFunctionDefinition<*>, changeInfo: ArendChangeInfo) {
    val body = function.body ?: return
    val functionClauses = body.clauseList
    if (body.elim == null && functionClauses.isEmpty()) return
    val elim = body.elim
    val eliminatedParameters = if (elim == null || elim.withKw != null) {
        function.parameters.map { tele -> if (tele.isExplicit) tele.identifierOrUnknownList.map { iou -> iou.defIdentifier } else emptyList() }.flatten()
    } else elim.refIdentifierList.map { it.reference.resolve() as? ArendDefIdentifier }.toList()
    val allParameters = function.parameters.map { tele -> tele.identifierOrUnknownList.map { iou -> iou.defIdentifier } }.flatten()

    val parameters = changeInfo.newParameters.filter { it.oldIndex != -1 }.map { allParameters[it.oldIndex]!! }.filter { eliminatedParameters.contains(it) }
    val deletedParameters = eliminatedParameters.minus(parameters.toSet()).asSequence().map { eliminatedParameters.indexOf(it) }.sorted()
    val template = ArrayList<Pair<Int, Boolean>>(); template.addAll(parameters.map { Pair(eliminatedParameters.indexOf(it), false) })
    for (d in deletedParameters) if (d <= template.size) template.add(d, Pair(d, true))

    val correctedElim = "\\elim ${printWithComments(eliminatedParameters, template, "_") { d -> d?.name ?: "_" }}"
    if (elim != null) performTextModification(elim, correctedElim) else {
        val insertPosition = body.findPrevSibling()!!
        performTextModification(body, " $correctedElim", insertPosition.endOffset, insertPosition.endOffset)
    }
    for (clause in function.body!!.clauseList) if (clause.patterns.isNotEmpty()) {
        val newPatterns = printWithComments(clause.patterns, template, "_") { p -> p.text }
        performTextModification(clause, newPatterns, clause.patterns.first().startOffset, clause.patterns.last().endOffset)
    }
}
private fun renameFunctionParameters(project: Project, changeInfo: ArendChangeInfo, function: ArendDefFunction) {
    val defIdentifiers = function.parameters.map { tele -> tele.identifierOrUnknownList.mapNotNull { iou -> iou.defIdentifier } }.flatten()
    val processors = ArrayList<Pair<List<SmartPsiElementPointer<PsiElement>>, ArendRenameProcessor>>()
    for (p in changeInfo.newParameters) {
        val d = if (p.oldIndex != -1) defIdentifiers[p.oldIndex] else null
        if (d != null && p.name != d.name) {
            val renameProcessor = ArendRenameProcessor(project, d, p.name, d.name, false, null)
            val usages = renameProcessor.findUsages()
            processors.add(Pair(usages.mapNotNull{ it.element }.map { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) }.toList(), renameProcessor))
        }
    }
    for (p in processors) {
        val renameProcessor = p.second
        val actualUsages = p.first.mapNotNull { it.element }.toHashSet()
        val usages = renameProcessor.findUsages().filter { actualUsages.contains(it.element) }.toTypedArray()
        renameProcessor.executeEx(usages)
    }
}

private fun modifyFunctionUsages(project: Project, function: ArendDefFunction, newParams: List<ArendParameterInfo>) {
    val oldParameters = ArrayList<Parameter>()
    val newParameters = ArrayList<NewParameter>()
    for (tele in function.parameters) for (p in tele.identifierOrUnknownList) oldParameters.add(Parameter(tele.isExplicit, p.defIdentifier))
    for (newParam in newParams) newParameters.add(NewParameter(newParam.isExplicit(), oldParameters.getOrNull(newParam.oldIndex)))
    if (function.ancestor<ArendDefClass>() != null) {
        val t = Parameter(false, null)
        oldParameters.add(0, t)
        newParameters.add(0, NewParameter(false, t))
    }
    NameFieldApplier(project).applyTo(singletonList(RefactoringDescriptor(function, oldParameters, newParameters)).toSet())
}

private fun modifyFunctionSignature(function: ArendDefFunction, changeInfo: ArendChangeInfo) {
    val signatureText = changeInfo.signaturePart()
    val startPosition = function.nameIdentifier?.startOffset ?: return
    val endPosition = (((function.returnExpr?.endOffset) ?: function.parameters.lastOrNull()?.endOffset) ?: function.alias?.endOffset) ?: function.nameIdentifier?.endOffset ?: return
    performTextModification(function, signatureText, startPosition, endPosition)
}

private fun<T> printWithComments(list: List<T>, t: List<Pair<Int, Boolean>>, defaultValue: String, converter: (T) -> String): String {
    val builder = StringBuilder(); var isInComment = false; var isAbsolutelyFirst = true; var isFirstUncommented = true
    for ((i, isCommented) in t) {
        if (isCommented && !isInComment) { builder.append("{-"); isInComment = true}
        if (!isAbsolutelyFirst && isFirstUncommented) builder.append(", ")
        if (isInComment && !isCommented) { builder.append("-}"); isInComment = false}
        if (!isAbsolutelyFirst && !isFirstUncommented) builder.append(", ")
        builder.append(list.getOrNull(i)?.let{converter.invoke(it)} ?: defaultValue)
        isAbsolutelyFirst = false
        if (!isCommented) isFirstUncommented = false
    }
    if (isInComment) builder.append("-}")
    return builder.toString()
}

private fun performTextModification(psi: PsiElement, newElim: String, startPosition : Int = psi.startOffset, endPosition : Int = psi.endOffset) {
    val containingFile = psi.containingFile
    val documentManager = PsiDocumentManager.getInstance(psi.project)
    val document = documentManager.getDocument(containingFile) ?: return
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(startPosition, endPosition, newElim)
    documentManager.commitDocument(document)
}