package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class ReplaceBracketsIntentionTest : QuickFixTestBase() {
    private val fixNameBrackets = ArendBundle.message("arend.replaceBrackets")
    private val fixNameParentheses = ArendBundle.message("arend.replaceParentheses")
    private fun doTestBrackets(contents: String, result: String) = simpleQuickFixTest(fixNameBrackets, contents, result)
    private fun doTestParentheses(contents: String, result: String) = simpleQuickFixTest(fixNameParentheses, contents, result)

    fun testReplaceBrackets() = doTestBrackets(
        """\func foo ({-caret-}a : Nat) => 1""",
        """\func foo {a : Nat} => 1"""
    )

    fun testReplaceParentheses() = doTestParentheses(
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
