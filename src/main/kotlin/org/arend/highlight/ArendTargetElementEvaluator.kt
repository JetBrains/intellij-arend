package org.arend.highlight

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.intellij.psi.util.startOffset
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.psi.ext.PsiReferable

class ArendTargetElementEvaluator : TargetElementEvaluatorEx2() {
    override fun isAcceptableNamedParent(parent: PsiElement) =
            parent !is ArendRefIdentifier && parent !is ArendDefIdentifier

    override fun getNamedElement(element: PsiElement): PsiElement? {
        // protection against ArendDefIdentifier, as no references can resolve to it.
        return element
            .parents(true)
            .filter { it is PsiReferable && it !is ArendDefIdentifier }
            .firstOrNull()
            ?.takeIf { (it as PsiReferable).nameIdentifier?.startOffset == element.startOffset  }
    }
}