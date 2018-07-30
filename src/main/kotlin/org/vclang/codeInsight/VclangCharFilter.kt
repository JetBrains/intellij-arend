package org.vclang.codeInsight

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import org.vclang.VcLanguage
import org.vclang.search.VcWordScanner

class VclangCharFilter : CharFilter() {
    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): CharFilter.Result? =
        if (lookup.psiFile?.language?.isKindOf(VcLanguage.INSTANCE) == true) {
            if (VcWordScanner.isVclangIdentifierPart(c)) {
                Result.ADD_TO_PREFIX
            } else when (c) {
                '.', ',', ' ', '(' -> Result.SELECT_ITEM_AND_FINISH_LOOKUP
                else -> Result.HIDE_LOOKUP
            }
        } else {
            null
        }
}
