package org.arend.psi

import com.intellij.patterns.*
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

object ArendPsiPattern {
    val onStatementBeginning: PsiElementPattern.Capture<PsiElement> =
            PlatformPatterns.psiElement().with(OnStatementBeginning())

    val onExpressionBeginning: PsiElementPattern.Capture<PsiElement> =
            PlatformPatterns.psiElement().with(OnExpressionBeginning())

    inline fun <reified I : PsiElement> psiElement(): PsiElementPattern.Capture<I> =
            PlatformPatterns.psiElement(I::class.java)

    val whitespace: PsiElementPattern.Capture<PsiElement> =
            PlatformPatterns.psiElement().whitespace()

    val error: PsiElementPattern.Capture<PsiErrorElement> = psiElement()

    private class OnStatementBeginning(vararg val startWords: String)
        : PatternCondition<PsiElement>("on statement beginning") {
        override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
            val prev = t.prevVisibleOrNewLine
            return if (startWords.isEmpty()) {
                prev == null || prev is PsiWhiteSpace
            } else {
                prev != null && prev.node.text in startWords
            }
        }
    }

    private class OnExpressionBeginning : PatternCondition<PsiElement>("on expression beginning") {
        override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
            val ancestor = t.ancestors.firstOrNull { it.parent is ArendExpr || it.parent is ArendNewExpr }
                    ?: return false
            return ancestor.leftSiblings.all { it is PsiWhiteSpace || it is PsiComment }
        }
    }
}

private val PsiElement.prevVisibleOrNewLine: PsiElement?
    get() = leftLeaves
            .filterNot { it is PsiComment || it is PsiErrorElement }
            .filter { it !is PsiWhiteSpace || it.textContains('\n') }
            .firstOrNull()

val PsiElement.leftLeaves: Sequence<PsiElement>
    get() = generateSequence(this, PsiTreeUtil::prevLeaf).drop(1)

val PsiElement.rightSiblings: Sequence<PsiElement>
    get() = generateSequence(this.nextSibling) { it.nextSibling }

val PsiElement.leftSiblings: Sequence<PsiElement>
    get() = generateSequence(this.prevSibling) { it.prevSibling }

fun <T : PsiElement, Self : PsiElementPattern<T, Self>> PsiElementPattern<T, Self>.withPrevItem(
        pattern: ElementPattern<out T>,
        visible: Boolean = false
): Self = with("withPrevItem") {
    val skip: (PsiElement) -> Boolean = {
        it is PsiComment
                || it is PsiErrorElement
                || it is PsiWhiteSpace
                || visible && it.text.isEmpty()
    }
    val leaf = it.leftLeaves.dropWhile(skip).firstOrNull() ?: return@with false
    pattern.accepts(leaf)
}

fun <T : PsiElement, Self : PsiElementPattern<T, Self>>
        PsiElementPattern<T, Self>.withPrevSiblingSkipping(
        skip: ElementPattern<out T>,
        pattern: ElementPattern<out T>
): Self = with("withPrevSiblingSkipping") {
    val sibling = it.leftSiblings
            .dropWhile { skip.accepts(it) }
            .firstOrNull() ?: return@with false
    pattern.accepts(sibling)
}

private fun <T, Self : ObjectPattern<T, Self>> ObjectPattern<T, Self>.with(
        name: String,
        cond: (T) -> Boolean
): Self = with(object : PatternCondition<T>(name) {
    override fun accepts(t: T, context: ProcessingContext?): Boolean = cond(t)
})
