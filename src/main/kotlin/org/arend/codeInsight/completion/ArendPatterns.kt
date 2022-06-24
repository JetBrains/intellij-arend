package org.arend.codeInsight.completion

import com.intellij.patterns.*
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.ProcessingContext

fun after(pattern: ElementPattern<PsiElement>) = PlatformPatterns.psiElement().afterLeaf(pattern)

fun afterLeaf(et: IElementType) = after(PlatformPatterns.psiElement(et))

fun afterLeaves (vararg types: IElementType) = StandardPatterns.or(*types.map { after(PlatformPatterns.psiElement(it)) }.toTypedArray())

fun ofType(vararg types: IElementType) = StandardPatterns.or(*types.map { PlatformPatterns.psiElement(it) }.toTypedArray())

fun <T : PsiElement> withParent(et: Class<T>): PsiElementPattern.Capture<PsiElement> {
    return PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(et))
}

fun <T : PsiElement> withGrandParent(et: Class<T>) = PlatformPatterns.psiElement().withSuperParent(2, PlatformPatterns.psiElement(et))

fun <T : PsiElement> withParentOrGrandParent(et: Class<T>) = StandardPatterns.or(withParent(et), withGrandParent(et))

fun <T : PsiElement> withGrandParents(vararg et: Class<out T>) = StandardPatterns.or(*et.map { withGrandParent(it) }.toTypedArray())

fun <T : PsiElement> withGreatGrandParents(vararg et: Class<out T>) = StandardPatterns.or(*et.map { PlatformPatterns.psiElement().withSuperParent(3, it) }.toTypedArray())

fun <T : PsiElement> withParents(vararg et: Class<out T>) = StandardPatterns.or(*et.map { withParent(it) }.toTypedArray())

fun <T : PsiElement> withAncestors(vararg et: Class<out T>): ElementPattern<PsiElement> =
        StandardPatterns.and(*et.mapIndexed { i, it -> PlatformPatterns.psiElement().withSuperParent(i + 1, PlatformPatterns.psiElement(it)) }.toTypedArray())

fun elementPattern(condition: (PsiElement) -> Boolean): ElementPattern<PsiElement> =
        object : ElementPattern<PsiElement> {
            override fun accepts(o: Any?): Boolean = o is PsiElement && condition(o)

            override fun accepts(o: Any?, context: ProcessingContext?): Boolean = accepts(o)

            override fun getCondition() = ElementPatternCondition(object : InitialPatternCondition<PsiElement>(PsiElement::class.java) {
                override fun accepts(o: Any?, context: ProcessingContext?): Boolean = accepts(o)
            })
        }