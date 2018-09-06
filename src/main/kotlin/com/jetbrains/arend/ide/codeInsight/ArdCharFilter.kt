package com.jetbrains.arend.ide.codeInsight

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import com.jetbrains.arend.ide.search.ArdWordScanner

class ArdCharFilter : CharFilter() {
    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): CharFilter.Result? =
            if (lookup.psiFile?.language?.isKindOf(com.jetbrains.arend.ide.ArdLanguage.INSTANCE) == true) {
                if (ArdWordScanner.isArendIdentifierPart(c)) {
                    Result.ADD_TO_PREFIX
                } else when (c) {
                    '.', ',', ' ', '(' -> Result.SELECT_ITEM_AND_FINISH_LOOKUP
                    else -> Result.HIDE_LOOKUP
                }
            } else {
                null
            }
}
