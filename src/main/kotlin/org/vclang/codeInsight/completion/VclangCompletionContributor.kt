package org.vclang.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.*
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext
import org.vclang.psi.*
import org.vclang.psi.VcElementTypes.*
import java.util.Collections.singletonList

class VclangCompletionContributor: CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PREC_CONTEXT, KeywordCompletionProvider(FIXITY_KWS))
        extend(CompletionType.BASIC, afterLeaf(FAT_ARROW), originalPositionCondition(withParentOrGrandParent(VcClassFieldSyn::class.java), KeywordCompletionProvider(FIXITY_KWS))) // fixity kws for class field synonym (2nd part)
        extend(CompletionType.BASIC, AS_CONTEXT, ProviderWithCondition({parameters, _ -> (parameters.position.parent.parent as VcNsId).asKw == null}, KeywordCompletionProvider(singletonList(AS_KW))))

        extend(CompletionType.BASIC, NS_CMD_CONTEXT, originalPositionCondition(withParent(VcFile::class.java), KeywordCompletionProvider(listOf(HIDING_KW, USING_KW))))
        extend(CompletionType.BASIC, NS_CMD_CONTEXT, ProviderWithCondition({ parameters, _ -> noUsing(parameters.position.parent.parent as VcStatCmd)}, KeywordCompletionProvider(singletonList(USING_KW))))
        extend(CompletionType.BASIC, NS_CMD_CONTEXT, ProviderWithCondition({ parameters, _ -> noUsingAndHiding(parameters.position.parent.parent as VcStatCmd)}, KeywordCompletionProvider(singletonList(HIDING_KW))))
        extend(CompletionType.BASIC, withAncestors(PsiErrorElement::class.java, VcNsUsing::class.java, VcStatCmd::class.java), ProviderWithCondition({parameters, _ -> noHiding(parameters.position.parent.parent.parent as VcStatCmd)}, KeywordCompletionProvider(singletonList(HIDING_KW))))

        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, noCommonAncestorWithNextElement(VcStatement::class.java, KeywordCompletionProvider(STATEMENT_KWS)))
        extend(CompletionType.BASIC, STATEMENT_END_CONTEXT, noCommonAncestorWithNextElement(VcStatement::class.java,
                ProviderWithCondition({ parameters, _ -> parameters.position.ancestors.filter { it is VcWhere }.toList().isEmpty() }, KeywordCompletionProvider(singletonList(IMPORT_KW)))))

        //extend(CompletionType.BASIC, ANY, KeywordCompletionProvider(singletonList(INVALID_KW)))
    }

    companion object {
        val FIXITY_KWS = listOf(INFIX_LEFT_KW, INFIX_RIGHT_KW, INFIX_NON_KW, NON_ASSOC_KW, LEFT_ASSOC_KW, RIGHT_ASSOC_KW)
        val STATEMENT_KWS = listOf(FUNCTION_KW, DATA_KW, CLASS_KW, INSTANCE_KW, TRUNCATED_KW, OPEN_KW)
        val GLOBAL_STATEMENT_KWS = STATEMENT_KWS + singletonList(IMPORT_KW)

        private fun afterLeaf(et: IElementType) = PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withParent(et: Class<T>) = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withGrandParent(et: Class<T>) = PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(et))
        private fun <T : PsiElement> withParentOrGrandParent(et: Class<T>) = or(withParent(et), withGrandParent(et))
        private fun <T : PsiElement> withGrandParents(vararg et: Class<out T>) = or(*et.map { withGrandParent(it) }.toTypedArray())
        private fun <T : PsiElement> withParents(vararg et: Class<out T>) = or(*et.map { withParent(it) }.toTypedArray())
        private fun <T : PsiElement> withAncestors(vararg et: Class<out T>): ElementPattern<PsiElement> = and(*et.mapIndexed { i, it -> PlatformPatterns.psiElement().withSuperParent(i + 1, PlatformPatterns.psiElement(it)) }.toTypedArray())

        val PREC_CONTEXT = or(afterLeaf(FUNCTION_KW), afterLeaf(DATA_KW), afterLeaf(CLASS_KW), afterLeaf(AS_KW),
                and(afterLeaf(PIPE), withGrandParents(VcConstructor::class.java, VcDataBody::class.java)), //simple data type constructor
                and(afterLeaf(FAT_ARROW), withGrandParents(VcConstructor::class.java, VcConstructorClause::class.java)), //data type constructors with patterns
                and(afterLeaf(PIPE), withGrandParents(VcClassField::class.java, VcClassStat::class.java)), //class field
                and(afterLeaf(FAT_ARROW), withGrandParent(VcClassFieldSyn::class.java))) //class field synonym

        val AS_CONTEXT = and(withGrandParent(VcNsId::class.java), withParents(VcRefIdentifier::class.java, PsiErrorElement::class.java))
        val NS_CMD_CONTEXT = withAncestors(PsiErrorElement::class.java, VcStatCmd::class.java)
        val ANY = PlatformPatterns.psiElement()!!
        val STATEMENT_END_CONTEXT = withParents(PsiErrorElement::class.java, VcRefIdentifier::class.java)

        private fun noUsing(cmd: VcStatCmd): Boolean = cmd.nsUsing?.usingKw == null
        private fun noHiding(cmd: VcStatCmd): Boolean = cmd.hidingKw == null
        private fun noUsingAndHiding(cmd: VcStatCmd): Boolean = noUsing(cmd) && noHiding(cmd)


        class ProviderWithCondition(private val condition: (CompletionParameters, ProcessingContext?) -> Boolean, private val completionProvider: CompletionProvider<CompletionParameters>) : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
                if (condition(parameters, context))
                    completionProvider.addCompletionVariants(parameters, context, result)
            }
        }

        private fun <T> originalPositionCondition(opc: ElementPattern<T>, completionProvider: CompletionProvider<CompletionParameters>): CompletionProvider<CompletionParameters> =
                ProviderWithCondition({ parameters, context ->
                    opc.accepts(parameters.originalPosition, context)
                }, completionProvider)

        fun findClosestElements(file: PsiFile, offset: Int): Pair<PsiElement?, PsiElement?> {
            var ofs = 0
            var nextElement: PsiElement?
            var prevElement: PsiElement?
            do {
                val pos = offset + (ofs++)
                nextElement = if (pos > file.textLength) null else file.findElementAt(pos)
            } while (nextElement is PsiWhiteSpace || nextElement is PsiComment)
            ofs = -1
            do {
                val pos = offset + (ofs--)
                prevElement = if (pos < 0) null else file.findElementAt(pos)
            } while (prevElement is PsiWhiteSpace || prevElement is PsiComment)
            return Pair(prevElement, nextElement)
        }

        private fun <T> ancestorsUntil(c: Class<T>, element: PsiElement?): HashSet<PsiElement> {
            val ancestors = HashSet<PsiElement>()
            var elem: PsiElement? = element
            while (elem != null && !c.isInstance(elem)) {
                ancestors.add(elem)
                elem = elem.parent
            }
            return ancestors
        }

        private fun <T> noCommonAncestorWithNextElement(c: Class<T>, completionProvider: CompletionProvider<CompletionParameters>): CompletionProvider<CompletionParameters> =
                ProviderWithCondition({ parameters, _ ->
                    val closestElements = findClosestElements(parameters.originalFile, parameters.offset)
                    val prevElement = closestElements.first
                    val ancestorsNE = ancestorsUntil<T>(c, closestElements.second)
                    val ancestorsPE = ancestorsUntil<T>(c, prevElement)

                    val lastExprIncorrect = (prevElement?.nextSibling is PsiErrorElement ||
                            prevElement?.parent?.nextSibling is PsiErrorElement)

                    !lastExprIncorrect && ancestorsNE.intersect(ancestorsPE).isEmpty()
                }, completionProvider)
    }

    class KeywordCompletionProvider(private val keywords : List<IElementType>) : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
            val insertHandler = InsertHandler<LookupElement> { insertContext, _ ->
                val document = insertContext.document
                val tailOffset = insertContext.tailOffset
                if (tailOffset < document.textLength && !Character.isWhitespace(document.getText(TextRange(tailOffset, tailOffset+1))[0]))
                    document.insertString(tailOffset, " ") // add tail whitespace

                insertContext.commitDocument()
            }

            var prefix = result.prefixMatcher.prefix
            while (prefix.contains(' '))
                prefix = prefix.substring(prefix.indexOf(' ')+1, prefix.length)
            if (prefix == "\\") prefix = ""

            val nonEmptyPrefix = prefix.isNotEmpty() ||
                                 parameters.offset > 0 && parameters.originalFile.text.substring(parameters.offset - 1, parameters.offset) == "\\" //prefix consists of single slash character

            /* System.out.println("position.parent: "+parameters.position.parent?.javaClass)
            System.out.println("position.grandparent: "+parameters.position.parent?.parent?.javaClass)
            System.out.println("position.grandgrandparent: "+parameters.position.parent?.parent?.parent?.javaClass)
            System.out.println("originalPosition.parent: "+parameters.originalPosition?.parent?.javaClass)
            System.out.println("originalPosition.grandparent: "+parameters.originalPosition?.parent?.parent?.javaClass)
            val closestElements = findClosestElements(parameters.originalFile, parameters.offset)
            System.out.println("prevElement: ${closestElements.first}")
            System.out.println("prevElement.parent: ${closestElements.first?.parent?.javaClass}")
            System.out.println("nextElement: ${closestElements.second}")
            System.out.println("nextElement.parent: ${closestElements.second?.parent?.javaClass}")
            if (parameters.position.parent is PsiErrorElement) System.out.println("errorDescription: "+(parameters.position.parent as PsiErrorElement).errorDescription)
            System.out.println("") */

            for (keyword in keywords)
                result.withPrefixMatcher(PlainPrefixMatcher(if (nonEmptyPrefix) "\\"+prefix else "")).addElement(LookupElementBuilder.create(keyword.toString()).bold().withInsertHandler(insertHandler))
        }
    }
}