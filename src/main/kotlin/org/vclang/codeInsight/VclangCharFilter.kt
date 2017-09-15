package org.vclang.codeInsight

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import org.vclang.VcLanguage
import org.vclang.search.VcWordScanner

class VclangCharFilter : CharFilter() {
    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): CharFilter.Result? {
        if (lookup.psiFile?.language?.isKindOf(VcLanguage) ?: false) {
            if (VcWordScanner.isVclangIdentifierPart(c)) return CharFilter.Result.ADD_TO_PREFIX
            return when (c) {
                '.', ',', ' ', '(' -> CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP
                else -> CharFilter.Result.HIDE_LOOKUP
            }
        }
        return null
    }
}
