package com.jetbrains.arend.ide.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.ThreeState
import com.jetbrains.arend.ide.psi.ArdElementTypes


class ArdCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
            if (contextElement is LeafPsiElement && contextElement.elementType == ArdElementTypes.NUMBER) ThreeState.YES else ThreeState.UNSURE
}