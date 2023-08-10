package org.arend.refactoring.changeSignature.processors

import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendAccessMod
import org.arend.psi.ext.ArendClassField
import org.arend.refactoring.changeSignature.ArendChangeInfo
import org.arend.refactoring.changeSignature.ChangeSignatureRefactoringDescriptor
import org.arend.refactoring.changeSignature.NewParameter
import org.arend.refactoring.changeSignature.Parameter
import java.util.*

class ArendClassFieldChangeSignatureProcessor(val classField: ArendClassField, val changeInfo: ArendChangeInfo):
    ArendChangeSignatureDefinitionProcessor(classField, changeInfo) {
    override fun getRefactoringDescriptors(implicitPrefix: List<Parameter>, mainParameters: List<Parameter>, newParametersPrefix: List<NewParameter>, newParameters: List<NewParameter>, isSetOrOrderPreserved: Boolean): Set<ChangeSignatureRefactoringDescriptor> =
        Collections.singletonList(
            ChangeSignatureRefactoringDescriptor(
                classField,
                implicitPrefix + mainParameters,
                newParametersPrefix + newParameters,
                mainParameters,
                newParameters,
                newName = if (classField.name != info.newName) info.newName else null
            )
        ).toSet()

    override fun getSignature(): String = "${(classField.descendantOfType<ArendAccessMod>())?.let { it.text + " " } ?: ""}${changeInfo.precText}${changeInfo.name}${changeInfo.aliasText}${changeInfo.parameterText()}${changeInfo.returnPart()}"

}