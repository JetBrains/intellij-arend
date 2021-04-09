package org.arend.intention

import org.arend.quickfix.QuickFixTestBase

class SwapInfixOperatorArgumentsIntentionTest : QuickFixTestBase() {
    fun `test infix`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}op 1
    """, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 1 {-caret-}op 0
    """)

    fun `test no fix when not infix`() = typedCheckNoQuickFixes(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}op 1
    """)

    fun `test func as infix`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}`op` 1
    """, """ 
      \func op (a b : Nat) : Nat => 0
      \func f => 1 {-caret-}`op` 0
    """)

    fun `test no fix when func as postfix`() = typedCheckNoQuickFixes(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}`op
    """)

    fun `test caret is after operator`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 0 op{-caret-} 1
    """, """ 
      \func \infixl 5 op (a b : Nat) : Nat => 0
      \func f => 1 op{-caret-} 0
    """)

    fun `test no fix when one explicit argument`() = typedCheckNoQuickFixes(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func op (a b : Nat) : Nat => 0
      \func f => 0 {-caret-}`op`
    """)

    fun `test first operator in a sequence`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \open Nat (+)
      \func f => 0 {-caret-}+ 1 + 2
    """, """ 
      \open Nat (+)
      \func f => 1 {-caret-}+ 0 + 2
    """)

    fun `test second operator in a sequence`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \open Nat (+)
      \func f => 0 + 1 {-caret-}+ 2
    """, """ 
      \open Nat (+)
      \func f => 2 {-caret-}+ 0 + 1
    """)

    fun `test first in a sequence of different operators`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \open Nat (+, *)
      \func f => 0 {-caret-}+ 1 * 2
    """, """ 
      \open Nat (+, *)
      \func f => 1 * 2 {-caret-}+ 0
    """)

    fun `test second in a sequence of different operators`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \open Nat (+, *)
      \func f => 0 + 1 {-caret-}* 2
    """, """ 
      \open Nat (+, *)
      \func f => 0 + 2 {-caret-}* 1
    """)

    fun `test one implicit argument`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """ 
      \func f => Nat {-caret-}= {\Set} \Prop
    """, """ 
      \func f => \Prop {-caret-}= {\Set} Nat
    """)

    fun `test two implicit arguments`() = typedQuickFixTest(SwapInfixOperatorArgumentsIntention.NAME, """
      \func op {a b : Nat} (c d : Nat): Nat => 0
      \func f => 0 {-caret-}`op` {1} {2} 3
    """, """
      \func op {a b : Nat} (c d : Nat): Nat => 0
      \func f => 3 {-caret-}`op` {1} {2} 0
    """)
}