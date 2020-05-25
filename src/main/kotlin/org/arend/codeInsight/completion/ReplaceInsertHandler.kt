package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement

class ReplaceInsertHandler(private val replacement: String) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val document = context.document
        document.deleteString(context.startOffset, context.tailOffset)
        document.insertString(context.tailOffset, replacement)
        context.commitDocument()
        context.editor.caretModel.moveToOffset(context.tailOffset)
    }
}