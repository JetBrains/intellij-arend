package org.arend.codeInsight

import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.Lookup
import org.arend.ArendLanguage
import org.arend.search.ArendWordScanner

class ArendCharFilter : CharFilter() {
    override fun acceptChar(c: Char, prefixLength: Int, lookup: Lookup): Result? =
        if (lookup.psiFile?.language?.isKindOf(ArendLanguage.INSTANCE) == true) {
            if (ArendWordScanner.isArendIdentifierPart(c)) {
                Result.ADD_TO_PREFIX
            } else when (c) {
                '.', ',', ' ', '(' -> Result.SELECT_ITEM_AND_FINISH_LOOKUP
                else -> Result.HIDE_LOOKUP
            }
        } else {
            null
        }
}
