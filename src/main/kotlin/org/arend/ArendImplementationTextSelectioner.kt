package org.arend

import com.intellij.codeInsight.hint.ImplementationTextSelectioner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendWhere
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.childOfType

class ArendImplementationTextSelectioner : ImplementationTextSelectioner {
    override fun getTextStartOffset(element: PsiElement): Int {
        if (element is ArendDefIdentifier) {
            val parent = element.parent
            if (parent is PsiLocatedReferable) {
                return parent.textRange.startOffset
            }
        }
        return element.textRange.startOffset
    }

    override fun getTextEndOffset(element: PsiElement): Int {
        if (element is ArendDefIdentifier) {
            val parent = element.parent
            if (parent is PsiLocatedReferable) {
                var elem = parent.childOfType<ArendWhere>()?.prevSibling
                while (elem is PsiWhiteSpace) {
                    elem = elem.prevSibling ?: break
                }
                return (elem ?: parent).textRange.endOffset
            }
        }
        return element.textRange.endOffset
    }
}
