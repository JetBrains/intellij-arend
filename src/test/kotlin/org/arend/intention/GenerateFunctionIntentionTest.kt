package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class GenerateFunctionIntentionTest : QuickFixTestBase() {
    private fun doTest(@Language("Arend") contents: String, @Language("Arend") result: String) =
        simpleQuickFixTest(ArendBundle.message("arend.generate.function"), contents.trimIndent(), result.trimIndent())


    fun `test basic`() = doTest("""
        \func lorem {A : \Type} (x y : A) : x = y => {?{-caret-}}
    """, """
        \func lorem {A : \Type} (x y : A) : x = y => lorem-lemma x y
        
        \func lorem-lemma {A : \Type} (x : A) (y : A) : x = y => {?}
        """
    )

    fun `test dependent type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => {?{-caret-}}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => lorem-lemma B x y
        
        \func lorem-lemma {A : \Type} (B : A -> \Type) {a : A} (x : B a) (y : B a) : x = y => {?}
        """
    )

    fun `test sigma type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} (c d : \Sigma (a : A) (B a)) : c = d => {?{-caret-}}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} (c d : \Sigma (a : A) (B a)) : c = d => lorem-lemma B c d

        \func lorem-lemma {A : \Type} (B : A -> \Type) (c : \Sigma (a : A) (B a)) (d : \Sigma (a : A) (B a)) : c = d => {?}
        """
    )

    fun `test nested`() = doTest("""
        \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q
          | idp => p
        
        \func lorem (a b c d : Nat) (eq1 : a = b) (eq2 : c = d) : a = d => eq1 *> {?{-caret-}} *> eq2""",
        """
        \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q
          | idp => p

        \func lorem (a b c d : Nat) (eq1 : a = b) (eq2 : c = d) : a = d => eq1 *> (lorem-lemma b c) *> eq2

        \func lorem-lemma (b : Nat) (c : Nat) : b = c => {?}
        """
    )

    fun `test in call`() = doTest("""
        \func foo (a b : Nat) (eq : a = b) : a = a => {?} 
        
        \func lorem (x y : Nat) : x = x => foo x y {?{-caret-}}""",
        """
        \func foo (a b : Nat) (eq : a = b) : a = a => {?}

        \func lorem (x y : Nat) : x = x => foo x y (lorem-lemma x y)

        \func lorem-lemma (x : Nat) (y : Nat) : x = y => {?}
        """
    )

    fun `test imported definition`() = doTest("""
        \module Foo \where {
          \func rr : Nat => 1
        }

        \func foo : Foo.rr = 1 => {?{-caret-}}
            """, """
        \module Foo \where {
          \func rr : Nat => 1
        }

        \func foo : Foo.rr = 1 => foo-lemma

        \func foo-lemma : Foo.rr = 1 => {?}
        """)

    fun `test imported definition in parameter`() = doTest("""
        \module Foo \where {
          \func rr : Nat => 1
        }

        \func foo (eq : Foo.rr = 1) : eq = idp => {?}
    """, """
        \module Foo \where {
          \func rr : Nat => 1
        }

        \func foo (eq : Foo.rr = 1) : eq = idp => foo-lemma eq

        \func foo-lemma (eq : Foo.rr = 1) : eq = idp {Nat} {1} => {?}
    """)

}
