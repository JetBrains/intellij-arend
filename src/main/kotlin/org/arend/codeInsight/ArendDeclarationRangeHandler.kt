package org.arend.codeInsight

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendDefData
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendDefInstance

class ArendDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange =
        when (container) {
            is ArendDefFunction -> container.expr ?: container.nameTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is ArendDefData -> container.universeExpr ?: container.typeTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is ArendDefClass -> container.longNameList.lastOrNull() ?: container.fieldTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is ArendDefInstance -> container.nameTeleList.lastOrNull() ?: container.defIdentifier
            else -> null
        }?.let { TextRange(container.textRange.startOffset, it.textRange.endOffset) } ?: container.textRange
}
