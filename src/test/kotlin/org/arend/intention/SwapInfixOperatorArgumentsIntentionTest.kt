package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class SwapInfixOperatorArgumentsIntentionTest : QuickFixTestBase() {
    val fixName = ArendBundle.message("arend.expression.swapInfixArguments")

    private fun doTest(@Language("Arend") before: String, @Language("Arend") after: String) =
            typedQuickFixTest(fixName, before, after)

    private fun doTestNoFix(@Language("Arend") code: String) = typedCheckNoQuickFixes(fixName, code)

    fun `test infix`() = doTest(""" 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}op 1
    """, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 1 {-caret-}op 0
    """)

    fun `test no fix when not infix`() = doTestNoFix(""" 
  \func op (a b : Nat) : Nat => 0
  \func f => 0 {-caret-}op 1
""")

    fun `test func as infix`() = doTest(""" 
      \func op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}`op` 1
    """, """ 
      \func op (a b : Nat) : Nat => 0
      \func f => 1 {-caret-}`op` 0
    """)

    fun `test no fix when func as postfix`() = doTestNoFix(""" 
  \func op (a b : Nat) : Nat => 0
  \func f => 0 {-caret-}`op
""")

    fun `test caret is after operator`() = doTest(""" 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 0 op{-caret-} 1
    """, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 1 op{-caret-} 0
    """)

    fun `test no fix when one explicit argument`() = doTestNoFix(""" 
  \func op (a b : Nat) : Nat => 0
  \func f => 0 {-caret-}`op`
""")

    fun `test first operator in a sequence`() = doTest(""" 
      \open Nat (+)
      \func f => 0 {-caret-}+ 1 + 2
    """, """ 
      \open Nat (+)
      \func f => 1 {-caret-}+ 0 + 2
    """)

    fun `test second operator in a sequence`() = doTest(""" 
      \open Nat (+)
      \func f => 0 + 1 {-caret-}+ 2
    """, """ 
      \open Nat (+)
      \func f => 2 {-caret-}+ 0 + 1
    """)

    fun `test first in a sequence of different operators`() = doTest(""" 
      \open Nat (+, *)
      \func f => 0 {-caret-}+ 1 * 2
    """, """ 
      \open Nat (+, *)
      \func f => 1 * 2 {-caret-}+ 0
    """)

    fun `test second in a sequence of different operators`() = doTest(""" 
      \open Nat (+, *)
      \func f => 0 + 1 {-caret-}* 2
    """, """ 
      \open Nat (+, *)
      \func f => 0 + 2 {-caret-}* 1
    """)

    fun `test one implicit argument`() = doTest(""" 
      \func f => Nat {-caret-}= {\Set} \Prop
    """, """ 
      \func f => \Prop {-caret-}= {\Set} Nat
    """)

    fun `test two implicit arguments`() = doTest("""
      \func op {a b : Nat} (c d : Nat): Nat => 0
      \func f => 0 {-caret-}`op` {1} {2} 3
    """, """
      \func op {a b : Nat} (c d : Nat): Nat => 0
      \func f => 3 {-caret-}`op` {1} {2} 0
    """)

    fun `test data`() = doTest("""
      \data \infixr 5 And (A B : \Type)
        | \infixr 5 && A B
      \func f : Nat And{-caret-} Int => 0 && 1
    """, """
      \data \infixr 5 And (A B : \Type)
        | \infixr 5 && A B
      \func f : Int And{-caret-} Nat => 0 && 1
    """)

    fun `test data constructor`() = doTest("""
      \data \infixr 5 And (A B : \Type)
        | \infixr 5 && A B
      \func f : Nat And Int => 0 &&{-caret-} 1
    """, """
      \data \infixr 5 And (A B : \Type)
        | \infixr 5 && A B
      \func f : Nat And Int => 1 &&{-caret-} 0
    """)

    fun `test class field alias`() = doTest("""
      \class A
        | \infixl 7 op \alias \infixl 7 ∧ : Nat -> Nat -> Nat
      \func f (a : A) => 1 ∧{-caret-} 2
    """, """
      \class A
        | \infixl 7 op \alias \infixl 7 ∧ : Nat -> Nat -> Nat
      \func f (a : A) => 2 ∧ 1
    """)
}