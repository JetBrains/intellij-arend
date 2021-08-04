package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class AddClarifyingParensIntentionTest : QuickFixTestBase() {
    @Language("Arend")
    private val op = """\func \infixl 5 op (a b : Nat) : Nat => 0"""

    @Language("Arend")
    private val f = """\func f (a b : Nat) : Nat => 0"""

    @Language("Arend")
    private val equationalReasoning = """
      \func \infix 2 ==< {A : \Type} (a : A) {a' : A} (p : a = a') => p
      \func \infixr 1 >== {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' => {?}
      \func \fix 2 qed {A : \Type} (a : A) : a = a => idp
      """

    fun `test infix`() = typedQuickFixTest(""" 
      $op
      \func s => 0 op{-caret-} 1 op 2
    """, """ 
      $op
      \func s => (0 op 1) op 2 
    """)

    fun `test func as infix`() = typedQuickFixTest(""" 
      $f
      \func s => 0 `f`{-caret-} 1 `f` 2
    """, """ 
      $f
      \func s => 0 `f` (1 `f` 2) 
    """)

    fun `test from second operator`() = typedQuickFixTest(""" 
      $op
      \func s => 0 op 1 op{-caret-} 2
    """, """ 
      $op
      \func s => (0 op 1) op 2 
    """)

    fun `test no fix when second arg is usual application`() = typedCheckNoQuickFixes(""" 
      $op
      $f
      \func s => 0 op{-caret-} f 1 2
    """)

    fun `test no fix when second arg is in parens`() = typedCheckNoQuickFixes(""" 
      $op
      \func s => 0 op{-caret-} (1 op 2)
    """)

    fun `test put caret after operator`() = typedQuickFixTest(""" 
      $op
      \func s => 0 {-caret-}op 1 op 2
    """, """ 
      $op
      \func s => (0 op{-caret-} 1) op 2
    """)

    fun `test don't remove code that looks like a special caret marker`() = typedQuickFixTest(""" 
      $op
      \func <<<<caret-pos>>>> => 1
      \func s => 0 op{-caret-} 1 op <<<<caret-pos>>>>
    """, """ 
      $op
      \func <<<<caret-pos>>>> => 1
      \func s => (0 op 1) op <<<<caret-pos>>>>
    """)

    fun `test infix and func as infix`() = typedQuickFixTest(""" 
      $op
      $f
      \func s => 0 op{-caret-} 1 `f` 2
    """, """ 
      $op
      $f
      \func s => 0 op (1 `f` 2) 
    """)

    fun `test wrap left and right arguments`() = typedQuickFixTest(""" 
      $op
      $f
      \func s => 0 `f` 1 op{-caret-} 2 `f` 3
    """, """ 
      $op
      $f
      \func s => (0 `f` 1) op (2 `f` 3)
    """)

    fun `test four operators`() = typedQuickFixTest(""" 
      $op
      \func s => 0 op{-caret-} 1 op 2 op 3 op 4
    """, """ 
      $op
      \func s => (((0 op{-caret-} 1) op 2) op 3) op 4
    """)

    fun `test Nat plus and mul`() = typedQuickFixTest(""" 
      \open Nat (+, *)
      \func s => 1 * 2 + 3 * 4 * 5 + 6 +{-caret-} 7
    """, """ 
      \open Nat (+, *)
      \func s => (((1 * 2) + ((3 * 4) * 5)) + 6) +{-caret-} 7
    """)

    fun `test different associativity`() = typedQuickFixTest(""" 
      $op
      \func \infixr 4 opR (a b : Nat) : Nat => 0
      \func s => 0 op 1 op 2 opR 3 opR{-caret-} 4
    """, """ 
      $op
      \func \infixr 4 opR (a b : Nat) : Nat => 0
      \func s => ((0 op 1) op 2) opR (3 opR 4)
    """)

    fun `test implicit argument`() = typedQuickFixTest(""" 
      \open Nat (+)
      \func s => 1 + 2 ={-caret-} {Nat} 3
    """, """ 
      \open Nat (+)
      \func s => (1 + 2) ={-caret-} {Nat} 3
    """)

    fun `test preserve whitespaces`() = typedQuickFixTest("""
      \open Nat (+)
      \func s =>
        1
          +
        2
          +{-caret-}
        3
    """, """
      \open Nat (+)
      \func s =>
        (1
          +
        2)
          +{-caret-}
        3
    """)

    fun `test preserve whitespaces in equations`() = typedQuickFixTest(""" 
      $equationalReasoning
      \func s =>
        1 ==< {?} >==
        2 ==< {?} >=={-caret-}
        3 `qed
    """, """ 
      $equationalReasoning
      \func s =>
        (1 ==< {?}) >==
        ((2 ==< {?}) >=={-caret-}
        3 `qed)
    """)

    private fun typedQuickFixTest(@Language("Arend") before: String, @Language("Arend") after: String) =
            typedQuickFixTest(ArendBundle.message("arend.expression.addClarifyingParentheses"), before, after)

    private fun typedCheckNoQuickFixes(@Language("Arend") code: String) =
            typedCheckNoQuickFixes(ArendBundle.message("arend.expression.addClarifyingParentheses"), code)
}