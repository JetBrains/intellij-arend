package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
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
    val factory = ArendPsiFactory(project)

    for (nsCmd in changeInfo.nsCmds) nsCmd.execute()
    renameParametersUsages(project, function, changeInfo.newParameters.toList().map { it as ArendParameterInfo })
    modifyFunctionBody(factory, function, changeInfo)

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

private fun modifyFunctionBody(factory: ArendPsiFactory, function: ArendDefFunction, changeInfo: ArendChangeInfo) {
    val params = function.parameters
    if (params.isNotEmpty()) function.deleteChildRangeWithNotification(params.first(), params.last())
    val anchor = function.findParametersElement()
    val signatureText = changeInfo.signature()
    val sampleFunc = factory.createFromText(signatureText)!!.childOfType<ArendDefFunction>()!!
    val whitespaceBeforeFirstNameTele = sampleFunc.defIdentifier!!.nextSibling
    if (signatureText.trim() != "" && whitespaceBeforeFirstNameTele != null) {
        function.addRangeBeforeWithNotification(whitespaceBeforeFirstNameTele, sampleFunc.parameters.last(), anchor)
    }
}