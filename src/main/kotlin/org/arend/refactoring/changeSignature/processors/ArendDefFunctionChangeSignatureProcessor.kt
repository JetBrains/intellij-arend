package org.arend.refactoring.changeSignature.processors

import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendDefFunction
import org.arend.psi.findPrevSibling
import org.arend.refactoring.changeSignature.*
import java.util.*

class ArendDefFunctionChangeSignatureProcessor(val function: ArendDefFunction, val changeInfo: ArendChangeInfo):
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
        "${(changeInfo.locatedReferable as ArendDefFunction).functionKw.text} ${changeInfo.precText}${changeInfo.name}${changeInfo.pLevelsText}${changeInfo.hLevelsText}${changeInfo.aliasText}${changeInfo.parameterText()}${changeInfo.returnPart()}"
}