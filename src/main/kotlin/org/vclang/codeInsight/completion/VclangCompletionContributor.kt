package org.vclang.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.and
import com.intellij.patterns.StandardPatterns.or
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import org.vclang.psi.*
import org.vclang.psi.VcElementTypes.*
import org.vclang.psi.impl.VcStatementImpl
import java.util.Collections.singletonList

class VclangCompletionContributor: CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PREC_CONTEXT, KeywordCompletionProvider(FIXITY_KWS)) // fixity kws
        extend(CompletionType.BASIC, afterLeaf(FAT_ARROW), originalPositionCondition(withParentOrGrandParent(VcClassFieldSyn::class.java), KeywordCompletionProvider(FIXITY_KWS))) // fixity kws for class field synonym (2nd part)
        extend(CompletionType.BASIC, AS_CONTEXT, KeywordCompletionProvider(singletonList(AS_KW)))
        //extend(CompletionType.BASIC, PlatformPatterns.psiElement(), KeywordCompletionProvider(singletonList(INVALID_KW)))
    }

    companion object {
        val FIXITY_KWS = listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW)
        val ROOT_KWS = listOf(FUNCTION_KW, DATA_KW, CLASS_KW, INSTANCE_KW, TRUNCATED_KW, OPEN_KW, IMPORT_KW)

        private fun afterLeaf(et : IElementType) = PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(et))
        private fun<T: PsiElement> withParent(et : Class<T>) = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(et))
        private fun<T: PsiElement> withGrandParent(et : Class<T>) = PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(et))
        private fun<T: PsiElement> withParentOrGrandParent(et : Class<T>) = or(withParent(et), withGrandParent(et))
        private fun<T: PsiElement> withGrandParents(vararg et : Class<out T>) = or(*et.map { withGrandParent(it) }.toTypedArray())
        private fun<T: PsiElement> withParents(vararg et : Class<out T>) = or(*et.map { withParent(it) }.toTypedArray())

        val PREC_CONTEXT = or(afterLeaf(FUNCTION_KW), afterLeaf(DATA_KW), afterLeaf(CLASS_KW), afterLeaf(AS_KW),
                and(afterLeaf(PIPE),      withGrandParents(VcConstructor::class.java, VcDataBody::class.java)), //simple data type constructor
                and(afterLeaf(FAT_ARROW), withGrandParents(VcConstructor::class.java, VcConstructorClause::class.java)), //data type constructors with patterns
                and(afterLeaf(PIPE),      withGrandParents(VcClassField::class.java, VcClassStat::class.java)), //class field
                and(afterLeaf(FAT_ARROW), withGrandParent(VcClassFieldSyn::class.java))) //class field synonym

        val AS_CONTEXT = and(withGrandParent(VcNsId::class.java), withParents(VcRefIdentifier::class.java, PsiErrorElement::class.java))
        val ROOT_CONTEXT = and(withParent(PsiErrorElement::class.java), withGrandParent(VcFile::class.java))
        val ROOT_CONTEXT_O = and(PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(VcStatementImpl::class.java)), withParents(VcFile::class.java, VcWhere::class.java))
    }


    class ProviderWithCondition<T>(private val condition : (CompletionParameters, ProcessingContext?) -> Boolean, private val completionProvider : CompletionProvider<CompletionParameters>) : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            if (condition(parameters, context))
                completionProvider.addCompletionVariants(parameters, context, result)
        }
    }

    fun<T> originalPositionCondition (opc: ElementPattern<T>, completionProvider: CompletionProvider<CompletionParameters>): CompletionProvider<CompletionParameters> =
            ProviderWithCondition<T>({a, b ->
                opc.accepts(a.originalPosition, b)
            }, completionProvider)

    class KeywordCompletionProvider(private val keywords : List<IElementType>) : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            val insertHandler = InsertHandler<LookupElement> { insertContext, _ ->
                val document = insertContext.document
                val tailOffset = insertContext.tailOffset
                if (tailOffset < document.textLength && !Character.isWhitespace(document.getText(TextRange(tailOffset, tailOffset+1))[0]))
                    document.insertString(tailOffset, " ") // add tail whitespace

                insertContext.commitDocument()
            }

            val nonEmptyPrefix = result.prefixMatcher.prefix.isNotEmpty() ||
                                 parameters.offset > 0 && parameters.originalFile.text.substring(parameters.offset - 1, parameters.offset) == "\\" //prefix consists of single slash character

            System.out.println("position.parent: "+parameters.position.parent?.javaClass)
            System.out.println("position.grandparent: "+parameters.position.parent?.parent?.javaClass)

            System.out.println("originalPosition.parent: "+parameters.originalPosition?.parent?.javaClass)
            System.out.println("originalPosition.prevSibling: "+parameters.originalPosition?.prevSibling?.javaClass)
            System.out.println("originalPosition.grandparent: "+parameters.originalPosition?.parent?.parent?.javaClass)

            System.out.println(ROOT_CONTEXT_O.accepts(parameters.originalPosition, context))

            for (keyword in keywords)
                result.withPrefixMatcher(PlainPrefixMatcher(if (nonEmptyPrefix) "\\"+result.prefixMatcher.prefix else "")).addElement(LookupElementBuilder.create(keyword.toString()).bold().withInsertHandler(insertHandler))
        }
    }
}