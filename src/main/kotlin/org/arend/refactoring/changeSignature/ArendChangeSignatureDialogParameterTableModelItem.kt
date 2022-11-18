package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase

class ArendChangeSignatureDialogParameterTableModelItem(val resultParameterInfo: ArendParameterInfo, typeCodeFragment: ArendChangeSignatureDialogCodeFragment):
    ParameterTableModelItemBase<ArendParameterInfo>(resultParameterInfo, typeCodeFragment, null) {
    val associatedReferable = ArendChangeSignatureDialogParameter(this)
    val dependencies = LinkedHashSet<ArendChangeSignatureDialogParameterTableModelItem>()

    override fun isEllipsisType(): Boolean = false
}