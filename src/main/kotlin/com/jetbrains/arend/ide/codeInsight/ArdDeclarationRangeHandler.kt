package com.jetbrains.arend.ide.codeInsight

import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.psi.ArdDefClass
import com.jetbrains.arend.ide.psi.ArdDefData
import com.jetbrains.arend.ide.psi.ArdDefFunction
import com.jetbrains.arend.ide.psi.ArdDefInstance

class ArdDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange =
            when (container) {
                is ArdDefFunction -> container.expr ?: container.nameTeleList.lastOrNull() as PsiElement?
                ?: container.defIdentifier
                is ArdDefData -> container.universeExpr ?: container.typeTeleList.lastOrNull() as PsiElement?
                ?: container.defIdentifier
                is ArdDefClass -> container.longNameList.lastOrNull()
                        ?: container.fieldTeleList.lastOrNull() as PsiElement? ?: container.defIdentifier
                is ArdDefInstance -> container.nameTeleList.lastOrNull() ?: container.defIdentifier
                else -> null
            }?.let { TextRange(container.textRange.startOffset, it.textRange.endOffset) } ?: container.textRange
}
