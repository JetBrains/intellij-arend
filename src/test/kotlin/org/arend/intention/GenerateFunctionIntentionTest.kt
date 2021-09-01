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
        
        \func lorem-lemma {A : \Type} (x y : A) : x = y => {?}
        """
    )

    fun `test dependent type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => {?{-caret-}}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => lorem-lemma B x y
        
        \func lorem-lemma {A : \Type} (B : A -> \Type) {a : A} (x y : B a) : x = y => {?}
        """
    )

    fun `test sigma type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} (c d : \Sigma (a : A) (B a)) : c = d => {?{-caret-}}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} (c d : \Sigma (a : A) (B a)) : c = d => lorem-lemma B c d

        \func lorem-lemma {A : \Type} (B : A -> \Type) (c d : \Sigma (a : A) (B a)) : c = d => {?}
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

        \func lorem-lemma (b c : Nat) : b = c => {?}
        """
    )

    fun `test in call`() = doTest("""
        \func foo (a b : Nat) (eq : a = b) : a = a => {?} 
        
        \func lorem (x y : Nat) : x = x => foo x y {?{-caret-}}""",
        """
        \func foo (a b : Nat) (eq : a = b) : a = a => {?}

        \func lorem (x y : Nat) : x = x => foo x y (lorem-lemma x y)

        \func lorem-lemma (x y : Nat) : x = y => {?}
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

        \func foo (eq : Foo.rr = 1) : eq = idp => {?{-caret-}}
    """, """
        \module Foo \where {
          \func rr : Nat => 1
        }

        \func foo (eq : Foo.rr = 1) : eq = idp => foo-lemma eq

        \func foo-lemma (eq : Foo.rr = 1) : eq = idp => {?}
    """)

    fun `test goal with expression`() = doTest("""
        \func lorem {A : \Prop} (x y : A) : x = y => {?{-caret-}(Path.inProp _ _)}
    """, """
        \func lorem {A : \Prop} (x y : A) : x = y => lorem-lemma x y
        
        \func lorem-lemma {A : \Prop} (x y : A) : x = y => Path.inProp x y
    """)

    fun `test goal with name`() = doTest("""
        \func lorem {A : \Prop} (x y : A) : x = y => {?my-lemma{-caret-}}
    """, """
        \func lorem {A : \Prop} (x y : A) : x = y => my-lemma x y
        
        \func my-lemma {A : \Prop} (x y : A) : x = y => {?}""")

    fun `test extract from selection`() = doTest("""
        \func bar (n : Nat) : Nat => n Nat.+ 1

        \func foo : Nat => bar {-selection-}({-caret-}10 Nat.+ 10){-end_selection-}
    """, """
        \func bar (n : Nat) : Nat => n Nat.+ 1

        \func foo : Nat => bar (foo-lemma)
    
        \func foo-lemma : Fin 21 => 20
    """)

    fun `test extract from selection 2`() = doTest("""
        \func bar {A : \Prop} (x y : A) : x = y => {-selection-}Path.i{-caret-}nProp{-end_selection-} _ _
    """, """
        \func bar {A : \Prop} (x y : A) : x = y => bar-lemma x y

        \func bar-lemma {A : \Prop} (x y : A) : x = y => Path.inProp x y
    """)

    fun `test extract from selection 3`() = doTest("""
        \func bar {A : \Prop} (x y : A) : x = y => Path.inProp {-selection-}{-caret-}_{-end_selection-} _
    """, """
        \func bar {A : \Prop} (x y : A) : x = y => Path.inProp (bar-lemma x) _

        \func bar-lemma {A : \Prop} (x : A) : A => x
    """)

    fun `test implicit args`() = doTest("""
        \data D {x y : Nat} {eq : x = y} (z : Nat) | dd

        \func foo : D {2} {2} {idp} 1 => {-selection-}{-caret-}dd{-end_selection-}
    """, """
        \data D {x y : Nat} {eq : x = y} (z : Nat) | dd

        \func foo : D {2} {2} {idp} 1 => foo-lemma
  
        \func foo-lemma : D {2} {2} {idp {Nat} {2}} 1 => dd
    """)

    fun `test qualified definition`() = doTest("""
        \func foo : Nat => {-selection-}b{-caret-}ar{-end_selection-}
        \where {
            \func bar : Nat => 1
        }
    """, """
        \func foo : Nat => foo-lemma
        \where {
            \func bar : Nat => 1
        }
        
        \func foo-lemma : Nat => foo.bar
    """.trimIndent())

    fun `test projection`() = doTest("""
        \func foo : \Sigma Nat Nat => (1, 1)

        \func bar => \let (a, b) => foo \in {-selection-}{-caret-}a{-end_selection-}
    """, """
        \func foo : \Sigma Nat Nat => (1, 1)

        \func bar => \let (a, b) => foo \in bar-lemma a
        
        \func bar-lemma (a : Nat) : Nat => a 
    """)

    fun `test nested projection`() = doTest("""
        \func foo : \Sigma (\Sigma Nat Nat) Nat => ((1, 1), 1)

        \func bar : \Sigma Nat Nat => \let ((a, b), c) => foo \in {-selection-}(a{-caret-}, c){-end_selection-}
    """, """
        \func foo : \Sigma (\Sigma Nat Nat) Nat => ((1, 1), 1)

        \func bar : \Sigma Nat Nat => \let ((a, b), c) => foo \in bar-lemma a c
        
        \func bar-lemma (a c : Nat) : \Sigma Nat Nat => (a, c) 
    """)

    fun `test projections 2`() = doTest("""
        \func f : \Sigma Nat Nat => (1, 2)
        
        \func g : Nat -> Nat => \lam x => x
        
        \func foo : Nat => \let rr => f \in {-selection-}g {-caret-}rr.1{-end_selection-}
    """, """
        \func f : \Sigma Nat Nat => (1, 2)
        
        \func g : Nat -> Nat => \lam x => x
        
        \func foo : Nat => \let rr => f \in foo-lemma rr
        
        \func foo-lemma (rr : \Sigma Nat Nat) : Nat => g rr.1""")

    fun `test complex infix`() = doTest("""
        \func foo (x y : Nat) => {-selection-}x Nat{-caret-}.+ x{-end_selection-} Nat.+ y
    """, """
        \func foo (x y : Nat) => (foo-lemma x) Nat.+ y
        
        \func foo-lemma (x : Nat) : Nat => x Nat.+ x
    """)


    fun `test empty goal`() = doTest("""
        \func foo => {?{-caret-}}
    """, """
        \func foo => foo-lemma
        
        \func foo-lemma => {?}
    """)
}
