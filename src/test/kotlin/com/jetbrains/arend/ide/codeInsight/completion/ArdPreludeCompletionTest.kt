package com.jetbrains.arend.ide.codeInsight.completion

class ArdPreludeCompletionTest : ArdCompletionTestBase() {

    fun `test prelude`() = checkSingleCompletion("""\func _ : z{-caret-}""", "zero")

    fun `test prelude visibility`() = checkNoCompletion("""z{-caret-}""")
}
