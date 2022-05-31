package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.intention.NameFieldApplier
import org.arend.intention.NewParameter
import org.arend.intention.Parameter
import org.arend.intention.RefactoringDescriptor
import org.arend.psi.*
import java.util.Collections.singletonList


fun processFunction(project: Project, changeInfo: ArendChangeInfo, function: ArendDefFunction) {
    val factory = ArendPsiFactory(project)

    renameParametersUsages(project, function, changeInfo.newParameters.toList().map { it as ArendParameterInfo })
    changeParameters(factory, function, changeInfo)
}

private fun renameParametersUsages(project: Project, function: ArendDefFunction, newParams: List<ArendParameterInfo>) {
    val oldParameters = ArrayList<Parameter>()
    val newParameters = ArrayList<NewParameter>()
    for (tele in function.nameTeleList) for (p in tele.identifierOrUnknownList) oldParameters.add(Parameter(tele.isExplicit, p.defIdentifier))
    for (newParam in newParams) newParameters.add(NewParameter(newParam.isExplicit(), oldParameters.getOrNull(newParam.oldIndex)))
    if (function.ancestor<ArendDefClass>() != null) {
        val t = Parameter(false, null)
        oldParameters.add(0, t)
        newParameters.add(0, NewParameter(false, t))
    }
    NameFieldApplier(project).applyTo(singletonList(RefactoringDescriptor(function, oldParameters, newParameters)).toSet())
}

private fun changeParameters(factory: ArendPsiFactory, function: ArendDefFunction, changeInfo: ArendChangeInfo) {
    if (function.nameTeleList.isNotEmpty()) function.deleteChildRangeWithNotification(function.nameTeleList.first(), function.nameTeleList.last())
    val anchor = function.alias ?: function.hLevelParams ?: function.pLevelParams ?: function.defIdentifier
    val signatureText = changeInfo.signature()
    val sampleFunc = factory.createFromText(signatureText)!!.childOfType<ArendDefFunction>()!!
    val whitespaceBeforeFirstNameTele = sampleFunc.defIdentifier!!.nextSibling
    if (signatureText.trim() != "" && whitespaceBeforeFirstNameTele != null && anchor != null) {
        function.addRangeAfterWithNotification(whitespaceBeforeFirstNameTele, sampleFunc.nameTeleList.last(), anchor)
    }
}