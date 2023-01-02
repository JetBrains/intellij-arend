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
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendDefFunction
import org.arend.refactoring.rename.ArendRenameProcessor
import java.util.Collections.singletonList


fun processFunction(project: Project, changeInfo: ArendChangeInfo, function: ArendDefFunction) {
    changeInfo.addNamespaceCommands()
    if (changeInfo.isParameterSetOrOrderChanged) {
        val elim = function.body?.elim
        if (elim?.withKw != null) {
            val defIdentifiers = function.parameters.map { tele -> if (tele.isExplicit) tele.identifierOrUnknownList.mapNotNull { iou -> iou.defIdentifier } else emptyList() }.flatten().fold("") { acc, dI -> if (acc == "") dI.name else acc + ", ${dI.name}" }
            performTextModification(elim, "\\elim $defIdentifiers")

        }
        modifyFunctionUsages(project, function, changeInfo.newParameters.toList().map { it as ArendParameterInfo })
    }

    if (changeInfo.isParameterNamesChanged)
        renameFunctionParameters(project, changeInfo, function)

    if (changeInfo.isParameterNamesChanged || changeInfo.isParameterSetOrOrderChanged || changeInfo.isParameterTypesChanged)
        modifyFunctionSignature(function, changeInfo)

    if (changeInfo.isNameChanged) {
        val renameProcessor = ArendRenameProcessor(project, function, changeInfo.newName, function.refName, false, null)
        val usages = renameProcessor.findUsages()
        renameProcessor.executeEx(usages)
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

private fun performTextModification(psi: PsiElement, newElim: String, startPosition : Int = psi.startOffset, endPosition : Int = psi.endOffset) {
    val containingFile = psi.containingFile
    val documentManager = PsiDocumentManager.getInstance(psi.project)
    val document = documentManager.getDocument(containingFile) ?: return
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(startPosition, endPosition, newElim)
    documentManager.commitDocument(document)
}