package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ThreeState
import org.arend.psi.ArendElementTypes


class ArendCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
        if (contextElement is LeafPsiElement && contextElement.elementType == ArendElementTypes.NUMBER) ThreeState.YES else ThreeState.UNSURE
}