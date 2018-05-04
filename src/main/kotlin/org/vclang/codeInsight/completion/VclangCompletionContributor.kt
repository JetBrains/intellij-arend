package org.vclang.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import org.vclang.psi.VcElementTypes.*

class VclangCompletionContributor: CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PREC_CONTEXT, KeywordCompletionProvider(listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW)))
    }

    companion object {
        val PREC_CONTEXT = or(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(FUNCTION_KW)),
                PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(DATA_KW)),
                PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(CLASS_KW))) //TODO: Add support for class constructors
    }

    class KeywordCompletionProvider(private val keywords : List<IElementType>) : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            val insertHandler = InsertHandler<LookupElement> { insertContext, _ ->
                val document = insertContext.document
                val startOffset = insertContext.startOffset
                var tailOffset = insertContext.tailOffset

                if (startOffset > 0 && document.getText(TextRange(startOffset-1, startOffset+1)) == "\\\\") {
                    document.deleteString(startOffset-1, startOffset) //fix double backslash
                    tailOffset--
                }

                if (tailOffset < document.textLength && !Character.isWhitespace(document.getText(TextRange(tailOffset, tailOffset+1))[0])) {
                    document.insertString(tailOffset, " ") // add tail whitespace
                }

                insertContext.commitDocument()
            }

            for (keyword in keywords)
                result.addElement(LookupElementBuilder.create(keyword.toString()).bold().withInsertHandler(insertHandler))
        }
    }
}