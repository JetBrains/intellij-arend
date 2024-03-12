package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class ReplaceBracketsIntentionTest : QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.replaceBrackets")
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(fixName, contents, result)

    fun testReplaceBrackets1() = doTest(
        """\func foo (a{-caret-} : Nat) => 1""",
        """\func foo {a : Nat} => 1"""
    )

    fun testReplaceBrackets2() = doTest(
        """
            \func foo (a : Nat) => 1
            \func bar => foo {{-caret-}foo 0}
        """,
        """
            \func foo (a : Nat) => 1
            \func bar => foo (foo 0)
        """
    )
}
