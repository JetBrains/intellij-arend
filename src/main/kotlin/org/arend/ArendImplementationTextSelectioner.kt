package org.arend

import com.intellij.codeInsight.hint.ImplementationTextSelectioner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendWhere

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
                var elem = PsiTreeUtil.getChildOfType(parent, ArendWhere::class.java)?.prevSibling
                while (elem is PsiWhiteSpace) {
                    elem = elem.prevSibling ?: break
                }
                return (elem ?: parent).textRange.endOffset
            }
        }
        return element.textRange.endOffset
    }
}
