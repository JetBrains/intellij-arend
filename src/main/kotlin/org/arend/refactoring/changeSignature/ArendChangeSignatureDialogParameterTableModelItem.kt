package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import org.arend.psi.ext.ArendReferenceElement
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

class ArendChangeSignatureDialogParameterTableModelItem(val resultParameterInfo: ArendParameterInfo, typeCodeFragment: ArendChangeSignatureDialogCodeFragment):
    ParameterTableModelItemBase<ArendParameterInfo>(resultParameterInfo, typeCodeFragment, null) {
    val associatedReferable = ArendChangeSignatureDialogParameter(this)
    val dependencies = LinkedHashSet<ArendChangeSignatureDialogParameterTableModelItem>()
    val usages = HashSet<ArendReferenceElement>()

    override fun isEllipsisType(): Boolean = false
}