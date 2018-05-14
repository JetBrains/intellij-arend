package org.vclang.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.and
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import org.vclang.psi.*
import org.vclang.psi.VcElementTypes.*

class VclangCompletionContributor: CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PREC_CONTEXT, KeywordCompletionProvider(listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW)))
    }

    companion object {
        val PREC_CONTEXT = or(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(FUNCTION_KW)),
                PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(DATA_KW)),
                PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(CLASS_KW)),
                PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(AS_KW)),
                and(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(PIPE)),
                        or(PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcConstructor::class.java)),
                           PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcDataBody::class.java)))), //simple data type constructor
                and(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(FAT_ARROW)),
                        or(PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcConstructor::class.java)),
                           PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcConstructorClause::class.java)))), //data type constructors with patterns
                and(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(PIPE)),
                        or(PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcClassField::class.java)),
                           PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcClassStat::class.java)))), //class field
                and(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(FAT_ARROW)),
                        PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(VcClassFieldSyn::class.java))) //class field synonym
        )
    }

    class KeywordCompletionProvider(private val keywords : List<IElementType>) : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            val insertHandler = InsertHandler<LookupElement> { insertContext, _ ->
                val document = insertContext.document

                val tailOffset = insertContext.tailOffset
                if (tailOffset < document.textLength && !Character.isWhitespace(document.getText(TextRange(tailOffset, tailOffset+1))[0])) {
                    document.insertString(tailOffset, " ") // add tail whitespace
                }

                insertContext.commitDocument()
            }

            val nonEmptyPrefix = result.prefixMatcher.prefix.isNotEmpty() ||
                                 parameters.offset > 0 && parameters.originalFile.text.substring(parameters.offset - 1, parameters.offset) == "\\" //prefix consists of single slash character

            for (keyword in keywords)
                result.withPrefixMatcher(PlainPrefixMatcher(if (nonEmptyPrefix) "\\"+result.prefixMatcher.prefix else "")).addElement(LookupElementBuilder.create(keyword.toString()).bold().withInsertHandler(insertHandler))

        }
    }
}