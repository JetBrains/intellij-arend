package org.arend.intention

import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class CreateLetBindingIntentionTest : QuickFixTestBase() {
    private fun doTestLet(expr: String, @Language("Arend") contents: String, @Language("Arend") result: String) {
        UiInterceptors.register(ChooserInterceptor(null, ".*text=${StringUtil.escapeToRegexp(expr)}.*"))
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

        \func bar => foo (\let a-lemma : Fin 11 => 10 \in foo (foo a-lemma))
    """)
}
