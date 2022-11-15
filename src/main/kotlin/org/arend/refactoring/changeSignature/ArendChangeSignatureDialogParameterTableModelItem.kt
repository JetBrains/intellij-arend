package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase

class ArendChangeSignatureDialogParameterTableModelItem(val resultParameterInfo: ArendParameterInfo, val typeCodeFragment: ArendChangeSignatureDialogCodeFragment):
    ParameterTableModelItemBase<ArendParameterInfo>(resultParameterInfo, typeCodeFragment, null) {
    val associatedReferable = ArendChangeSignatureDialogParameter(this)
    val dependencies = HashSet<ArendChangeSignatureDialogParameterTableModelItem>()

    override fun isEllipsisType(): Boolean = false
}