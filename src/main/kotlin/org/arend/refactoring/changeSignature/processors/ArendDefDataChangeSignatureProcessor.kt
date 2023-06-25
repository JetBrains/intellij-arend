package org.arend.refactoring.changeSignature.processors

import com.intellij.psi.PsiElement
import org.arend.codeInsight.ArendParameterInfoHandler
import org.arend.naming.reference.Referable
import org.arend.psi.ArendElementTypes
import org.arend.psi.ext.ArendDefData
import org.arend.psi.findPrevSibling
import org.arend.refactoring.changeSignature.*
import java.util.HashSet

class ArendDefDataChangeSignatureProcessor(val data: ArendDefData, val changeInfo: ArendChangeInfo):
        ArendChangeSignatureDefinitionProcessor(data, changeInfo) {
    override fun getRefactoringDescriptors(implicitPrefix: List<Parameter>, mainParameters: List<Parameter>, newParametersPrefix: List<NewParameter>, newParameters: List<NewParameter>, isSetOrOrderPreserved: Boolean): Set<ChangeSignatureRefactoringDescriptor> {
        val refactoringDescriptors = HashSet<ChangeSignatureRefactoringDescriptor>()
        refactoringDescriptors.add(
            ChangeSignatureRefactoringDescriptor(
                data,
                implicitPrefix + mainParameters,
                newParametersPrefix + newParameters,
                newName = if (data.name != info.newName) info.newName else null
            )
        )
        if (!isSetOrOrderPreserved) for (cons in data.constructors) {
            val newDataParametersReferables = newParameters.map { it.oldParameter?.referable }
            val newConstructorPrefixReferables = ArrayList<Referable?>()
            val constructorPrefixReferables = ArendParameterInfoHandler.getImplicitPrefixForReferable(
                cons,
                newDataParametersReferables,
                newConstructorPrefixReferables
            ).map { p -> p.referableList.map { it } }.flatten()
            val constructorPrefix = constructorPrefixReferables.map { Parameter(false, it) }
            val constructorParameters = cons.parameters.map { p -> p.referableList.map { Parameter(p.isExplicit, it) } }.flatten()
            val newConstructorPrefix = newConstructorPrefixReferables.map {
                val oldParameterIndex = constructorPrefixReferables.indexOf(it)
                val oldParameter = if (oldParameterIndex != -1) constructorPrefix[oldParameterIndex] else null
                NewParameter(false, oldParameter)
            }

            val newConstructorParameters = constructorParameters.map { NewParameter(it.isExplicit, it) }
            refactoringDescriptors.add(
                ChangeSignatureRefactoringDescriptor(
                    cons,
                    constructorPrefix + constructorParameters,
                    newConstructorPrefix + newConstructorParameters,
                    constructorParameters,
                    newConstructorParameters
                )
            )
        }
        return refactoringDescriptors
    }

    override fun getSignatureEnd(): PsiElement? = data.dataBody ?: data.where

    override fun fixEliminator() {
        val body = data.dataBody
        if (body != null && body.constructorClauseList.isNotEmpty())
            fixElim(body.elim, body.findPrevSibling()!!, body.constructorClauseList, data.parameters, changeInfo)
    }

    override fun getSignature(): String =
        "${data.truncatedKw?.text?.let { "$it " } ?: ""}${ArendElementTypes.DATA_KW} ${changeInfo.precText}${changeInfo.name}${changeInfo.pLevelsText}${changeInfo.hLevelsText}${changeInfo.aliasText}${changeInfo.parameterText()}${changeInfo.returnPart()}"
}