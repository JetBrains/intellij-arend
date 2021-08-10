package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class RemoveClarifyingParensIntentionTest : QuickFixTestBase() {
    @Language("Arend")
    private val opL = """\func \infixl 5 opL (a b : Nat) : Nat => 0"""
    @Language("Arend")
    private val opR = """\func \infixr 5 opR (a b : Nat) : Nat => 0"""

    fun `test parent with lower precedence is on the left`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => 0 +{-caret-} (1 * 2)
    """, """ 
      \open Nat (+, *)
      \func f => 0 + 1 * 2
    """)

    fun `test parent with lower precedence is on the right`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => (0 * 1) +{-caret-} 2
    """, """ 
      \open Nat (+, *)
      \func f => 0 * 1 + 2
    """)

    fun `test no fix when parent with higher precedence is on the left`() = typedCheckNoQuickFixes(""" 
      \open Nat (+, *)
      \func f => 0 *{-caret-} (1 + 2)
    """)

    fun `test no fix when parent with higher precedence is on the right`() = typedCheckNoQuickFixes(""" 
      \open Nat (+, *)
      \func f => (0 + 1) *{-caret-} 2
    """)

    fun `test clarifying parens in left associative operators sequence`() = typedQuickFixTest(""" 
      $opL
      \func f => (1 opL 2) opL{-caret-} 3
    """, """ 
      $opL
      \func f => 1 opL 2 opL 3
    """)

    fun `test clarifying parens in right associative operators sequence`() = typedQuickFixTest(""" 
      $opR
      \func f => 1 opR{-caret-} (2 opR 3)
    """, """ 
      $opR
      \func f => 1 opR 2 opR 3
    """)

    fun `test no fix when usual parens in left associative operators sequence`() = typedCheckNoQuickFixes(""" 
      $opL
      \func f => 1 opL{-caret-} (2 opL 3)
    """)

    fun `test no fix when usual parens in right associative operators sequence`() = typedCheckNoQuickFixes(""" 
      $opR
      \func f => (1 opR 2) opR{-caret-} 3
    """)

    fun `test top level bin op in parens`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => (0 +{-caret-} (1 * 2))
    """, """ 
      \open Nat (+, *)
      \func f => (0 + 1 * 2)
    """)

    fun `test no fix for explicitly typed term`() = typedCheckNoQuickFixes(""" 
      \open Nat (+, *)
      \func f => 0 + (1 :{-caret-} Nat)
    """)

    fun `test bin op passed as argument`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func foo (n : Nat) => {?}
      \func f => foo (0 +{-caret-} (1 * 2))
    """, """ 
      \open Nat (+, *)
      \func foo (n : Nat) => {?}
      \func f => foo (0 + 1 * 2)
    """)

    fun `test bin op in parens with parent bin op`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => 0 + (1 *{-caret-} 2)
    """, """ 
      \open Nat (+, *)
      \func f => 0 + 1 * 2
    """)

    fun `test bin op in parens with parent and grand parent bin op`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => 0 + (1 + (2 *{-caret-} 3))
    """, """ 
      \open Nat (+, *)
      \func f => 0 + (1 + 2 * 3)
    """)

    fun `test no fix when no parens`() = typedCheckNoQuickFixes(""" 
      \open Nat (+, *)
      \func f => 0 + 1 +{-caret-} 2
    """)

    fun `test no fix when child bin op is a function argument`() = typedCheckNoQuickFixes(""" 
      \open Nat (+, *)
      \func foo (n : Nat) => {?}
      \func f => foo ((0 + 1) + 2) +{-caret-} 3
    """)

    fun `test child with clarifying parens is two levels below parent`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => (0 + 1) + 2 +{-caret-} 3
    """, """ 
      \open Nat (+, *)
      \func f => 0 + 1 + 2 + 3
    """)

    fun `test child with clarifying parens is inside usual parens`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => 0 +{-caret-} ((1 + 2) + 3)
    """, """ 
      \open Nat (+, *)
      \func f => 0 + (1 + 2 + 3)
    """)

    fun `test child with clarifying parens is inside the second pair of usual parens`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func f => 0 +{-caret-} (1 + 2) + ((3 + 4) + 5)
    """, """ 
      \open Nat (+, *)
      \func f => 0 + (1 + 2) + (3 + 4 + 5)
    """)

    fun `test implicit argument`() = typedQuickFixTest(""" 
      \open Nat (+)
      \func f => (1 + 2) ={-caret-} {Nat} 3
    """, """ 
      \open Nat (+)
      \func f => 1 + 2 ={-caret-} {Nat} 3
    """)

    private fun typedQuickFixTest(@Language("Arend") before: String, @Language("Arend") after: String) =
            typedQuickFixTest(ArendBundle.message("arend.expression.removeClarifyingParentheses"), before, after)

    private fun typedCheckNoQuickFixes(@Language("Arend") code: String) =
            typedCheckNoQuickFixes(ArendBundle.message("arend.expression.removeClarifyingParentheses"), code)
}