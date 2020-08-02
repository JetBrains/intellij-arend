package org.arend.highlight

import com.intellij.codeInsight.TargetElementEvaluatorEx2
import com.intellij.psi.PsiElement
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendRefIdentifier

class ArendTargetElementEvaluator : TargetElementEvaluatorEx2() {
    override fun isAcceptableNamedParent(parent: PsiElement) =
            parent !is ArendRefIdentifier && parent !is ArendDefIdentifier
}