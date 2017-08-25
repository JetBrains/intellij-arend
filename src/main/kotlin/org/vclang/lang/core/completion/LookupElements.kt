package org.vclang.lang.core.completion

import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder

const val KEYWORD_PRIORITY = 10.0

fun LookupElementBuilder.withPriority(priority: Double): LookupElement =
        PrioritizedLookupElement.withPriority(this, priority)
