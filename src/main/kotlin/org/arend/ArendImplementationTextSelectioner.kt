package org.arend

import com.intellij.codeInsight.hint.ImplementationTextSelectioner
import com.intellij.psi.PsiElement
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendDefinition

class ArendImplementationTextSelectioner : ImplementationTextSelectioner {
    override fun getTextStartOffset(element: PsiElement): Int {
        if (element is ArendDefIdentifier) {
            val parent = element.parent
            if (parent is ArendDefinition) {
                return parent.textRange.startOffset
            }
        }
        return element.textRange.startOffset
    }

    override fun getTextEndOffset(element: PsiElement): Int {
        if (element is ArendDefIdentifier) {
            val parent = element.parent
            if (parent is ArendDefinition) {
                return parent.textRange.endOffset
            }
        }
        return element.textRange.endOffset
    }
}
