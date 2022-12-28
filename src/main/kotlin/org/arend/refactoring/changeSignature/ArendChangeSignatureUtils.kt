package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
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
    for (nsCmd in changeInfo.nsCmds) nsCmd.execute()
    renameParametersUsages(project, function, changeInfo.newParameters.toList().map { it as ArendParameterInfo })
    modifyFunctionBody(function, changeInfo)

    if (changeInfo.isNameChanged) {
        val renameProcessor = ArendRenameProcessor(project, function, changeInfo.newName, function.refName, false, null)
        val usages = renameProcessor.findUsages()
        renameProcessor.executeEx(usages)
    }
}

private fun renameParametersUsages(project: Project, function: ArendDefFunction, newParams: List<ArendParameterInfo>) {
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

private fun modifyFunctionBody(function: ArendDefFunction, changeInfo: ArendChangeInfo) {
    val signatureText = changeInfo.signaturePart()
    val startPosition = function.nameIdentifier?.startOffset ?: return
    val endPosition = (((function.returnExpr?.endOffset) ?: function.parameters.lastOrNull()?.endOffset) ?: function.alias?.endOffset) ?: function.nameIdentifier?.endOffset ?: return
    val containingFile = function.containingFile
    val documentManager = PsiDocumentManager.getInstance(function.project)
    val document = documentManager.getDocument(containingFile) ?: return
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    document.replaceString(startPosition, endPosition, signatureText)
    documentManager.commitDocument(document)
}