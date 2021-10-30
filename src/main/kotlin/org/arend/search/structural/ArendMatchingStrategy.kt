package org.arend.search.structural

import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.arend.ArendLanguage

object ArendMatchingStrategy : MatchingStrategy {
    override fun continueMatching(start: PsiElement?): Boolean = start?.language === ArendLanguage.INSTANCE

    override fun shouldSkip(element: PsiElement?, elementToMatchWith: PsiElement?): Boolean = false
}