package org.arend.codeInsight

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendDefData
import org.arend.psi.ext.ArendDefFunction
import org.arend.psi.ext.ArendDefInstance

class ArendDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange =
        when (container) {
            is ArendDefFunction -> container.returnExpr ?: container.parameters.lastOrNull() as PsiElement? ?: container.defIdentifier
            is ArendDefData -> container.universe ?: container.parameters.lastOrNull() as PsiElement? ?: container.defIdentifier
            is ArendDefClass -> container.superClassList.lastOrNull()?.longName ?: container.fieldTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is ArendDefInstance -> container.parameters.lastOrNull() ?: container.defIdentifier
            else -> null
        }?.let { TextRange(container.textRange.startOffset, it.textRange.endOffset) } ?: container.textRange
}
