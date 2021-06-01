package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class SwapInfixOperatorArgumentsIntentionTest : QuickFixTestBase() {
    val fixName = ArendBundle.message("arend.expression.swapInfixArguments")

    private fun doTest(contents: String, result: String) = typedQuickFixTest(fixName, contents, result)

    private fun testNoQuickfixes(contents: String) = typedCheckNoQuickFixes(fixName, contents)

    fun `test infix`() = doTest(""" 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}op 1
    """, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 1 {-caret-}op 0
    """)

    fun `test no fix when not infix`() = testNoQuickfixes(""" 
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

    fun `test no fix when func as postfix`() = testNoQuickfixes(""" 
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

    fun `test no fix when one explicit argument`() = testNoQuickfixes(""" 
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
}