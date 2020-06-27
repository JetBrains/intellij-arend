package org.arend.codeInsight.completion

class ArendPreludeCompletionTest : ArendCompletionTestBase() {

    fun `test prelude`() = checkSingleCompletion("""\func _ : z{-caret-}""", "zero")

    fun `test prelude expr`() = checkSingleCompletion("""z{-caret-}""", "zero")
}
