package org.arend.refactoring.changeSignature.processors

import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendConstructor
import org.arend.psi.findPrevSibling
import org.arend.refactoring.changeSignature.*
import java.util.*

class ArendConstructorChangeSignatureProcessor(val constructor: ArendConstructor, val changeInfo: ArendChangeInfo):
        ArendChangeSignatureDefinitionProcessor(constructor, changeInfo) {
    override fun getRefactoringDescriptors(implicitPrefix: List<Parameter>, mainParameters: List<Parameter>, newParametersPrefix: List<NewParameter>, newParameters: List<NewParameter>, isSetOrOrderPreserved: Boolean): Set<ChangeSignatureRefactoringDescriptor> =
        Collections.singletonList(
            ChangeSignatureRefactoringDescriptor(
                constructor,
                implicitPrefix + mainParameters,
                newParametersPrefix + newParameters,
                mainParameters,
                newParameters,
                newName = if (constructor.name != info.newName) info.newName else null
            )
        ).toSet()

    override fun getSignatureEnd(): PsiElement? = constructor.elim

    override fun fixEliminator() {
        val elim = constructor.elim
        if (elim != null && constructor.clauses.isNotEmpty())
            fixElim(constructor.elim, elim.findPrevSibling()!!, constructor.clauses, constructor.parameters, changeInfo)
    }

    override fun getSignature(): String = "${changeInfo.precText}${changeInfo.name}${changeInfo.aliasText}${changeInfo.parameterText()}${changeInfo.returnPart()}"

}