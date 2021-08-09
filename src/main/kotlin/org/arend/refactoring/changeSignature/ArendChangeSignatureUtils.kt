package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.refactoring.RenameReferenceAction
import java.util.Collections.singletonList

fun processFunction(
    project: Project,
    changeInfo: ArendChangeInfo,
    function: ArendDefFunction,
) {
    val factory = ArendPsiFactory(project)

    val newTeles = changeInfo.newParameters.toList().map { it as ArendParameterInfo }

    renameParametersUsages(function.nameTeleList, newTeles)
    changeParameters(factory, function, newTeles)
}

private fun renameParametersUsages(
    originalTeles: List<ArendNameTele>,
    newTeles: List<ArendParameterInfo>
) {
    for (tele in newTeles) {
        if (tele.oldIndex == ParameterInfo.NEW_PARAMETER) continue

        val oldParameter = originalTeles[tele.oldIndex]
        val oldName = oldParameter.identifierOrUnknownList[0].defIdentifier?.name
        if (oldName != tele.name) {
            // one parameter in tele
            val usages =
                ReferencesSearch.search(oldParameter.identifierOrUnknownList[0].defIdentifier as PsiElement).toList()
            for (usage in usages) {
                RenameReferenceAction(usage.element as ArendReferenceElement, singletonList(tele.name)).execute(null)
            }
        }
    }
}

private fun changeParameters(
    factory: ArendPsiFactory,
    function: ArendDefFunction,
    newTeles: List<ArendParameterInfo>
) {
    function.deleteChildRangeWithNotification(function.nameTeleList.first(), function.nameTeleList.last())

    for (tele in newTeles) {
        val psiTele = factory.createNameTele(tele.name, tele.typeText ?: "ERROR_TYPE", tele.isExplicit())
        val addAfter = if (function.nameTeleList.isEmpty()) function.defIdentifier else function.nameTeleList.last()
        val added = function.addAfterWithNotification(psiTele, addAfter)
        val psiWs = factory.createWhitespace(" ")
        function.addBeforeWithNotification(psiWs, added)
    }
}