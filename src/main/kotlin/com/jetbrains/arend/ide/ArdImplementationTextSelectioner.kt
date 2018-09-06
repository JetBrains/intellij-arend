package com.jetbrains.arend.ide

import com.intellij.codeInsight.hint.ImplementationTextSelectioner
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.psi.ArdDefIdentifier
import com.jetbrains.arend.ide.psi.ArdDefinition

class ArdImplementationTextSelectioner : ImplementationTextSelectioner {
    override fun getTextStartOffset(element: PsiElement): Int {
        if (element is ArdDefIdentifier) {
            val parent = element.parent
            if (parent is ArdDefinition) {
                return parent.textRange.startOffset
            }
        }
        return element.textRange.startOffset
    }

    override fun getTextEndOffset(element: PsiElement): Int {
        if (element is ArdDefIdentifier) {
            val parent = element.parent
            if (parent is ArdDefinition) {
                return parent.textRange.endOffset
            }
        }
        return element.textRange.endOffset
    }
}
