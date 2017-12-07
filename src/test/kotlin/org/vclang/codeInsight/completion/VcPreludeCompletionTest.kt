package org.vclang.codeInsight.completion

class VcPreludeCompletionTest : VcCompletionTestBase() {

    fun `test prelude`() = checkSingleCompletion("zero", """\func _ : z{-caret-}""")

    fun `test prelude visibility`() = checkNoCompletion("""z{-caret-}""")
}
