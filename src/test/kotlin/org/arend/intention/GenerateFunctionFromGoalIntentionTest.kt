package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class GenerateFunctionFromGoalIntentionTest : QuickFixTestBase() {
    private fun doTest(@Language("Arend") contents: String, @Language("Arend") result: String) =
        simpleQuickFixTest(ArendBundle.message("arend.generate.function.from.goal"), contents.trimIndent(), result.trimIndent())


    fun `test basic`() = doTest("""
        \func lorem {A : \Type} (x y : A) : x = y => {?{-caret-}}
    """, """
        \func lorem {A : \Type} (x y : A) : x = y => lorem-lemma x y
          \where {
            \func lorem-lemma {A : \Type} (x y : A) : x = y => {?}
          }
        """
    )

    fun `test dependent type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => {?{-caret-}}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} {a : A} (x y : B a) : x = y => lorem-lemma B x y
          \where {
            \func lorem-lemma {A : \Type} (B : A -> \Type) {a : A} (x y : B a) : x = y => {?}
          }
        """
    )

    fun `test sigma type`() = doTest("""
        \func lorem {A : \Type} {B : A -> \Type} (c d : \Sigma (a : A) (B a)) : c = d => {?{-caret-}}
    """, """
        \func lorem {A : \Type} {B : A -> \Type} (c d : \Sigma (a : A) (B a)) : c = d => lorem-lemma B c d
          \where {
            \func lorem-lemma {A : \Type} (B : A -> \Type) (c d : \Sigma (a : A) (B a)) : c = d => {?}
          }
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
          \where {
            \func lorem-lemma (b c : Nat) : b = c => {?}
          }
        """
    )

    fun `test in call`() = doTest("""
        \func foo (a b : Nat) (eq : a = b) : a = a => {?} 
        
        \func lorem (x y : Nat) : x = x => foo x y {?{-caret-}}""",
        """
        \func foo (a b : Nat) (eq : a = b) : a = a => {?}

        \func lorem (x y : Nat) : x = x => foo x y (lorem-lemma x y)
          \where {
            \func lorem-lemma (x y : Nat) : x = y => {?}
          }
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
          \where {
            \func foo-lemma : Foo.rr = 1 => {?}
          }
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
          \where {
            \func foo-lemma (eq : Foo.rr = 1) : eq = idp => {?}
          }
    """)

    fun `test goal with expression`() = doTest("""
        \func lorem {A : \Prop} (x y : A) : x = y => {?{-caret-}(Path.inProp _ _)}
    """, """
        \func lorem {A : \Prop} (x y : A) : x = y => lorem-lemma x y
          \where {
            \func lorem-lemma {A : \Prop} (x y : A) : x = y => Path.inProp _ _
          }
    """)

    fun `test goal with name`() = doTest("""
        \func lorem {A : \Prop} (x y : A) : x = y => {?my-lemma{-caret-}}
    """, """
        \func lorem {A : \Prop} (x y : A) : x = y => my-lemma x y
          \where {
            \func my-lemma {A : \Prop} (x y : A) : x = y => {?}
          }""")

    fun `test empty goal`() = doTest("""
        \func foo => {?{-caret-}}
    """, """
        \func foo => foo-lemma
          \where {
            \func foo-lemma => {?}
          }
    """)

    fun `test non-standard name`() = doTest("""
        \func foo => {?{-caret-}}
        
        \func foo-lemma : Nat => 1
    """,
    """
       \func foo => foo-lemma'
         \where {
           \func foo-lemma' => {?}       
         }
       
       \func foo-lemma : Nat => 1
    """)

    fun `test goal with arguments`() = doTest("""
        \func foo : Nat => {?{-caret-}} 1
        """, """
        \func foo : Nat => foo-lemma 1
          \where {
            \func foo-lemma (n : Nat) : Nat => {?}
          }
        """)

    fun `test goal with arguments, replacing in type`() = doTest("""
        \data Unit | unit

        \func foo (f : Unit -> Unit) (u : Unit) : f u = unit => {?{-caret-}} (f u)
    """, """
        \data Unit | unit

        \func foo (f : Unit -> Unit) (u : Unit) : f u = unit => foo-lemma (f u)
          \where {
            \func foo-lemma (u : Unit) : u = unit => {?}
          }
    """)

    fun `test goal with arguments, dependency on argument`() = doTest("""
        \data D (n : Nat) 
        
        \func foo (n : Nat) (d : D n) : Nat => {?{-caret-}} d
    """, """
         \data D (n : Nat) 
        
         \func foo (n : Nat) (d : D n) : Nat => (foo-lemma n) d
           \where {
             \func foo-lemma (n : Nat) (d : D n) : Nat => {?}
           }
    """)

    fun `test goal with arguments, inside binop`() = doTest("""
        \func foo : Nat => {?{-caret-}} 1 Nat.+ 2
    """, """
        \func foo : Nat => foo-lemma 1 Nat.+ 2
          \where {
            \func foo-lemma (n : Nat) : Nat => {?}
          }
    """)

    fun `test goal with arguments, arguments with similar type`() = doTest("""
       \func foo : Nat => {?{-caret-}} 1 1 
    """, """
       \func foo : Nat => foo-lemma 1 1
         \where {
           \func foo-lemma (n n' : Nat) : Nat => {?}
         }
    """)

    fun `test in class`() = doTest("""
      \class Foo {
        | foo : Nat
      }
        
      \class Bar \extends Foo {
        | foo => {?{-caret-}}
      }
    """, """
      \class Foo {
        | foo : Nat
      }
        
      \class Bar \extends Foo {
        | foo => Bar-lemma
      } \where {
        \func Bar-lemma : Nat => {?}
      }
    """)
}
