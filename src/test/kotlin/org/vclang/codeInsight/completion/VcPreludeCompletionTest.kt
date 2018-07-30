package org.vclang.codeInsight.completion

class VcPreludeCompletionTest : VcCompletionTestBase() {

    fun `test prelude`() = checkSingleCompletion("""\func _ : z{-caret-}""", "zero")

    fun `test prelude visibility`() = checkNoCompletion("""z{-caret-}""")
}
