package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiExpressionCodeFragmentImpl
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl
import com.intellij.psi.util.parents
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import org.arend.ArendFileType
import org.arend.psi.ArendDefFunction

class ArendParameterTableModel(
    val descriptor: ArendSignatureDescriptor,
    defaultValueContext: PsiElement,
) : ParameterTableModelBase<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
    getTypeCodeFragmentContext(descriptor.function),
    defaultValueContext,
    NameColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(defaultValueContext.project),
    TypeColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
        defaultValueContext.project, ArendFileType
    )
) {
    override fun createRowItem(parameterInfoInfo: ArendParameterInfo?): ParameterTableModelItemBase<ArendParameterInfo> {
        if (parameterInfoInfo == null) {
            TODO("case `parameterInfo == null` isn't implemented yet")
        }

        val function = descriptor.function

        val paramTypeCodeFragment: PsiCodeFragment = PsiTypeCodeFragmentImpl(
            function.project,
            true,
            "dummyParamTypeName",
            function.text,
            0,
            myTypeContext
        )

        val defaultValueCodeFragment: PsiCodeFragment = PsiExpressionCodeFragmentImpl(
            function.project,
            true,
            "dummyExprName",
            function.text,
            null,
            myDefaultValueContext
        )

        return object : ParameterTableModelItemBase<ArendParameterInfo>(
            parameterInfoInfo,
            paramTypeCodeFragment,
            defaultValueCodeFragment
        ) {
            override fun isEllipsisType(): Boolean = false
        }
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