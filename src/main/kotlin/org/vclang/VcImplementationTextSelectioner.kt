package org.vclang

import com.intellij.codeInsight.hint.ImplementationTextSelectioner
import com.intellij.psi.PsiElement
import org.vclang.psi.VcDefIdentifier
import org.vclang.psi.VcDefinition

class VcImplementationTextSelectioner : ImplementationTextSelectioner {
    override fun getTextStartOffset(element: PsiElement): Int {
        if (element is VcDefIdentifier) {
            val parent = element.parent
            if (parent is VcDefinition) {
                return parent.textRange.startOffset
            }
        }
        return element.textRange.startOffset
    }

    override fun getTextEndOffset(element: PsiElement): Int {
        if (element is VcDefIdentifier) {
            val parent = element.parent
            if (parent is VcDefinition) {
                return parent.textRange.endOffset
            }
        }
        return element.textRange.endOffset
    }
}
