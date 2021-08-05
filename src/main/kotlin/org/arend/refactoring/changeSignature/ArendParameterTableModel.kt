package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import org.arend.ArendFileType

class ArendParameterTableModel(
    val descriptor: ArendSignatureDescriptor,
    defaultValueContext: PsiElement,
) : ParameterTableModelBase<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
    descriptor.method,
    defaultValueContext,
    NameColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(defaultValueContext.project),
    TypeColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
        descriptor.method.project, ArendFileType
    )
) {
    override fun createRowItem(parameterInfo: ArendParameterInfo?): ParameterTableModelItemBase<ArendParameterInfo> {
        if (parameterInfo == null) {
            TODO("case `parameterInfo == null` isn't implemented yet")
        }

        val typeCodeFragment = PsiTypeCodeFragmentImpl(
            myTypeContext.project,
            true,
            "fragment.ard",
            parameterInfo.typeText,
            0,
            myTypeContext
        )

        return object : ParameterTableModelItemBase<ArendParameterInfo>(
            parameterInfo,
            typeCodeFragment,
            null
        ) {
            override fun isEllipsisType(): Boolean = false
        }
    }
}