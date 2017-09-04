package org.vclang.codeInsight

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import org.vclang.VcLanguage
import org.vclang.search.VcWordScanner

class CompletionCharFilter : CharFilter() {
    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): CharFilter.Result? {
        val psiFile = lookup.psiFile ?: return null
        if (VcLanguage !in psiFile.viewProvider.languages) return null
        if (!lookup.isCompletion) return null
        if (VcWordScanner.isVclangIdentifierPart(c)) return CharFilter.Result.ADD_TO_PREFIX
        return when (c) {
            '.', ',', ' ', '(' -> CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP
            else -> CharFilter.Result.HIDE_LOOKUP
        }
    }
}
