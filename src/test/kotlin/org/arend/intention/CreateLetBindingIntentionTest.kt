package org.arend.intention

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class CreateLetBindingIntentionTest : QuickFixTestBase() {
    private fun doTestLet(expr: String, @Language("Arend") contents: String, @Language("Arend") result: String, uiShown: Boolean = true) {
        if (uiShown) {
            UiInterceptors.register(ChooserInterceptor(null, StringUtil.escapeToRegexp(expr)))
        }
        simpleQuickFixTest(ArendBundle.message("arend.create.let.binding"), contents.trimIndent(), result.trimIndent())
    }

    override fun tearDown() {
        UiInterceptors.clear()
        super.tearDown()
    }

    fun `test basic let`() = doTestLet("foo (foo 10)", """
        \func foo (n : Nat) : Nat => n

        \func bar => foo (foo (foo {-selection-}1{-caret-}0{-end_selection-}))
    """, """
        \func foo (n : Nat) : Nat => n

        \func bar => foo (
          \let
            x : Fin 11 => 10
          \in foo (foo x))
    """)

    fun testExistingLet() = doTestLet("foo (foo 10)", """
        \func foo (n : Nat) : Nat => n

        \func bar (n : Nat) : Nat => foo (foo (\let x : Fin 11 => 10 \in foo (foo {-selection-}1{-caret-}0{-end_selection-})))
    """, """
        \func foo (n : Nat) : Nat => n

        \func bar (n : Nat) : Nat => foo (foo (
          \let 
            | x : Fin 11 => 10 
            | x' : Fin 11 => 10
          \in foo (foo x')))
        """)

    fun testDependencyOnLocalBinding() = doTestLet("foo x", """
        \func foo (n : Nat) : Nat => n

        \func bar (n : Nat) : Nat => foo (foo (\let x => 12 \in foo (foo {-selection-}{-caret-}x{-end_selection-})))
    """, """
        \func foo (n : Nat) : Nat => n
     
        \func bar (n : Nat) : Nat => foo (foo (\let x => 12 \in foo (
          \let
            x' : Fin 13 => x
          \in foo x')))
    """)

    fun testBinOp() = doTestLet("(2 + 2) * 4 ^ 10", """
        \func \infixr 7 ^ : Nat -> Nat -> Nat => {?}
        \func \infixl 6 * : Nat -> Nat -> Nat => {?}
        \func \infixl 5 + : Nat -> Nat -> Nat => {?}
        
        \func f => (2 + 2) * {-selection-}4 {-caret-}^ 10{-end_selection-} + 12 + 13 + 14 
    """, """
        \func \infixr 7 ^ : Nat -> Nat -> Nat => {?}
        \func \infixl 6 * : Nat -> Nat -> Nat => {?}
        \func \infixl 5 + : Nat -> Nat -> Nat => {?}
        
        \func f => (
          \let
            x : Nat => 4 ^ 10
          \in (2 + 2) * x) + 12 + 13 + 14
    """)

    fun testNoNPEWithContractions() = checkNoQuickFixes(ArendBundle.message("arend.create.let.binding"), """
        \func \infix 4 f : Nat -> Nat -> Nat => {?}

        \func g : Nat => ({-selection-}`f {-caret-}3{-end_selection-}) 1
    """)
}
