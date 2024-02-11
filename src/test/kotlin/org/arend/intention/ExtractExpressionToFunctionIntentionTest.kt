package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class ExtractExpressionToFunctionIntentionTest : QuickFixTestBase() {
    private fun doTest(@Language("Arend") contents: String, @Language("Arend") result: String) =
        simpleQuickFixTest(ArendBundle.message("arend.generate.function.from.expression"), contents.trimIndent(), result.trimIndent())

    fun `test extract from selection`() = doTest("""
        \func bar (n : Nat) : Nat => n Nat.+ 1

        \func foo : Nat => bar {-selection-}({-caret-}10 Nat.+ 10){-end_selection-}
    """, """
        \func bar (n : Nat) : Nat => n Nat.+ 1

        \func foo : Nat => bar (foo-lemma)
          \where {
            \func foo-lemma : Fin 21 => (10 Nat.+ 10)
          }
    """)

    fun `test extract from selection 2`() = doTest("""
        \axiom prop-pi {A : \Prop} (x y : A) : x = y
        \lemma bar {A : \Prop} (x y : A) : x = y => {-selection-}pro{-caret-}p-pi _ _{-end_selection-}
    """, """
        \axiom prop-pi {A : \Prop} (x y : A) : x = y
        \lemma bar {A : \Prop} (x y : A) : x = y => bar-lemma x y
          \where {
            \func bar-lemma {A : \Prop} (x y : A) : x = y => prop-pi _ _
          }
    """)

    // yes, it is intended. normalizing expressions would result in awful function bodies
    fun `test extract from selection 3`() = doTest("""
        \axiom prop-pi {A : \Prop} (x y : A) : x = y
        \lemma bar {A : \Prop} (x y : A) : x = y => prop-pi {-selection-}{-caret-}_{-end_selection-} _
    """, """
        \axiom prop-pi {A : \Prop} (x y : A) : x = y
        \lemma bar {A : \Prop} (x y : A) : x = y => prop-pi (bar-lemma x) _
          \where {
            \func bar-lemma {A : \Prop} (x : A) : A => _
          }
    """)

    fun `test extract from selection 4`() = doTest("""
        \func f (x : Nat) => x
        \func foo (x y : Nat) => f {-selection-}(x Nat{-caret-}.+ x){-end_selection-}
    """, """
        \func f (x : Nat) => x
        \func foo (x y : Nat) => f (foo-lemma x)
          \where {
            \func foo-lemma (x : Nat) : Nat => (x Nat.+ x)
          }
    """.trimIndent())

    fun `test implicit args`() = doTest("""
        \data D {x y : Nat} {eq : x = y} (z : Nat) | dd

        \func foo : D {2} {2} {idp} 1 => {-selection-}{-caret-}dd{-end_selection-}
    """, """
        \data D {x y : Nat} {eq : x = y} (z : Nat) | dd

        \func foo : D {2} {2} {idp} 1 => foo-lemma
          \where {
            \func foo-lemma : D {2} {2} {idp {Nat} {2}} 1 => dd
          }
    """)

    fun `test unqualified definition`() = doTest("""
        \func foo : Nat => {-selection-}b{-caret-}ar{-end_selection-}
        \where {
            \func bar : Nat => 1
        }
    """, """
        \func foo : Nat => foo-lemma
          \where {
            \func bar : Nat => 1
            
            \func foo-lemma : Nat => bar
          }
    """.trimIndent())

    fun `test projection`() = doTest("""
        \func foo : \Sigma Nat Nat => (1, 1)

        \func bar => \let (a, b) => foo \in {-selection-}{-caret-}a{-end_selection-}
    """, """
        \func foo : \Sigma Nat Nat => (1, 1)

        \func bar => \let (a, b) => foo \in bar-lemma a
          \where {
            \func bar-lemma (a : Nat) : Nat => a
          }
    """)

    fun `test nested projection`() = doTest("""
        \func foo : \Sigma (\Sigma Nat Nat) Nat => ((1, 1), 1)

        \func bar : \Sigma Nat Nat => \let ((a, b), c) => foo \in {-selection-}(a{-caret-}, c){-end_selection-}
    """, """
        \func foo : \Sigma (\Sigma Nat Nat) Nat => ((1, 1), 1)

        \func bar : \Sigma Nat Nat => \let ((a, b), c) => foo \in bar-lemma a c
          \where {
            \func bar-lemma (a c : Nat) : \Sigma Nat Nat => (a, c)
          }
    """)

    fun `test projections 2`() = doTest("""
        \func f : \Sigma Nat Nat => (1, 2)
        
        \func g : Nat -> Nat => \lam x => x
        
        \func foo : Nat => \let rr => f \in {-selection-}g {-caret-}rr.1{-end_selection-}
    """, """
        \func f : \Sigma Nat Nat => (1, 2)
        
        \func g : Nat -> Nat => \lam x => x
        
        \func foo : Nat => \let rr => f \in foo-lemma rr
          \where {
            \func foo-lemma (rr : \Sigma Nat Nat) : Nat => g rr.1
          }
  """)

    fun `test complex infix`() = doTest("""
        \func foo (x y : Nat) => {-selection-}x Nat{-caret-}.+ x{-end_selection-} Nat.+ y
    """, """
        \func foo (x y : Nat) => foo-lemma x Nat.+ y
          \where {
            \func foo-lemma (x : Nat) : Nat => x Nat.+ x
          }
    """)

    fun `test this reference`() = doTest("""
\record R {}

\record D { | Y : R }

\record U \extends R { | A : D }

\lemma f : U \cowith
  | A => {-selection-}\new{-caret-} D {
    | Y => (\this : U)
  }{-end_selection-}
""", """
\record R {}

\record D { | Y : R }

\record U \extends R { | A : D }

\lemma f : U \cowith
  | A => f-lemma (\this : U)
  \where {
    \func f-lemma (this : U) : D => \new D {
      | Y => (this : U)
    }
  }

""")


}
