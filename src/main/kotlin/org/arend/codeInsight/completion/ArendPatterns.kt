package org.arend.codeInsight.completion

import com.intellij.patterns.*
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext

fun afterLeaf(et: IElementType) = PlatformPatterns.psiElement().afterLeaf(PlatformPatterns.psiElement(et))

fun ofType(vararg types: IElementType) = StandardPatterns.or(*types.map { PlatformPatterns.psiElement(it) }.toTypedArray())

fun <T : PsiElement> withParent(et: Class<T>) = PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(et))

fun <T : PsiElement> withGrandParent(et: Class<T>) = PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(et))

fun <T : PsiElement> withParentOrGrandParent(et: Class<T>) = StandardPatterns.or(withParent(et), withGrandParent(et))

fun <T : PsiElement> withGrandParents(vararg et: Class<out T>) = StandardPatterns.or(*et.map { withGrandParent(it) }.toTypedArray())

fun <T : PsiElement> withGreatGrandParents(vararg et: Class<out T>) = StandardPatterns.or(*et.map { PlatformPatterns.psiElement().withSuperParent(3, it) }.toTypedArray())

fun <T : PsiElement> withParents(vararg et: Class<out T>) = StandardPatterns.or(*et.map { withParent(it) }.toTypedArray())

fun <T : PsiElement> withAncestors(vararg et: Class<out T>): ElementPattern<PsiElement> =
        StandardPatterns.and(*et.mapIndexed { i, it -> PlatformPatterns.psiElement().withSuperParent(i + 1, PlatformPatterns.psiElement(it)) }.toTypedArray())

abstract class ArendElementPattern : ElementPattern<PsiElement> {
    abstract fun accept(o: PsiElement): Boolean

    override fun accepts(o: Any?): Boolean = o is PsiElement && accept(o)

    override fun accepts(o: Any?, context: ProcessingContext?): Boolean = accepts(o)

    override fun getCondition() = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
        override fun accepts(o: Any?, context: ProcessingContext?): Boolean = this@ArendElementPattern.accepts(o, context)
    })
}
