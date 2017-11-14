package org.vclang.codeInsight

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.vclang.psi.*

class VcDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange =
        when (container) {
            is VcDefFunction -> container.expr ?: container.nameTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is VcDefData -> container.expr ?: container.typeTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is VcDefClass -> container.longNameList.lastOrNull() ?: container.typeTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
            is VcDefInstance -> container.nameTeleList.lastOrNull() ?: container.defIdentifier
            else -> null
        }?.let { TextRange(container.textRange.startOffset, it.textRange.endOffset) } ?: container.textRange
}
