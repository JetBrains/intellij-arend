package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase

class ArendChangeSignatureDialogParameterTableModelItem(resultParameterInfo: ArendParameterInfo,
                                                        typeCodeFragment: ArendExpressionCodeFragment):
    ParameterTableModelItemBase<ArendParameterInfo>(resultParameterInfo, typeCodeFragment, null) {
    val associatedReferable = ArendChangeSignatureDialogParameter(this)

    override fun isEllipsisType(): Boolean = false
}