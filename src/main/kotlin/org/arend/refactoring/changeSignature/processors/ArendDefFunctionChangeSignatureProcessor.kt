package org.arend.refactoring.changeSignature.processors

import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.INSTANCE_KW
import org.arend.psi.ext.ArendDefFunction
import org.arend.psi.ext.ArendDefInstance
import org.arend.psi.ext.ArendFunctionDefinition
import org.arend.psi.findPrevSibling
import org.arend.refactoring.changeSignature.*
import java.util.*

class ArendDefFunctionChangeSignatureProcessor(val function: ArendFunctionDefinition<*>, val changeInfo: ArendChangeInfo):
    ArendChangeSignatureDefinitionProcessor(function, changeInfo) {
  override fun getRefactoringDescriptors(implicitPrefix: List<Parameter>, mainParameters: List<Parameter>,
                                         newParametersPrefix: List<NewParameter>, newParameters: List<NewParameter>,
                                         isSetOrOrderPreserved: Boolean) =
      Collections.singletonList(
          ChangeSignatureRefactoringDescriptor(
              function,
              implicitPrefix + mainParameters,
              newParametersPrefix + newParameters,
              newName = if (function.name != info.newName) info.newName else null
          )
      ).toSet()

    override fun getSignatureEnd(): PsiElement? = function.body ?: function.where

    override fun fixEliminator() {
        val body = function.body
        if (body != null && body.clauseList.isNotEmpty())
            fixElim(body.elim, body.findPrevSibling()!!, body.clauseList, definition.parameters, changeInfo)
    }

    override fun getSignature(): String =
        "${(when (val lR = changeInfo.locatedReferable) {
            is ArendDefFunction -> lR.functionKw.text
            is ArendDefInstance -> INSTANCE_KW.toString()
            else -> throw IllegalStateException()})} ${changeInfo.precText}${changeInfo.name}${changeInfo.pLevelsText}${changeInfo.hLevelsText}${changeInfo.aliasText}${changeInfo.parameterText()}${changeInfo.returnPart()}"
}