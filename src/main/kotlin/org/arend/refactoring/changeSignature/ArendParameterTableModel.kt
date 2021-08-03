package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.util.ui.ColumnInfo
import org.arend.psi.ArendDefFunction

class ArendParameterTableModel constructor(
    descriptor: ArendSignatureDescriptor,
    defaultValueContext: PsiElement,
    vararg columnInfos: ColumnInfo<*, *>?
) : ParameterTableModelBase<ParameterInfoImpl, ParameterTableModelItemBase<ParameterInfoImpl>>(
    getTypeCodeFragmentContext(descriptor.function),
    defaultValueContext,
    *columnInfos
) {
    override fun createRowItem(parameterInfo: ParameterInfoImpl?): ParameterTableModelItemBase<ParameterInfoImpl> {
        TODO("Not yet implemented")
    }

    companion object {
        fun getTypeCodeFragmentContext(startFrom: PsiElement) = startFrom.parents(true).mapNotNull {
            when (it) {
                is ArendDefFunction -> it.functionBody
                else -> null
            }
        }.first()
    }
}