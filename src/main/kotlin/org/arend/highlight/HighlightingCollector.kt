package org.arend.highlight

import com.intellij.openapi.util.TextRange

interface HighlightingCollector {
    fun addHighlightInfo(range: TextRange, colors: ArendHighlightingColors)
}