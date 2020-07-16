package org.arend.highlight

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.psi.*

class ArendChangeLocalityDetector : ChangeLocalityDetector {
    override fun getChangeHighlightingDirtyScopeFor(element: PsiElement): PsiElement? =
        when (element) {
            is LeafPsiElement -> if (AREND_COMMENTS.contains(element.node.elementType)) element else null
            else -> null
        }
}