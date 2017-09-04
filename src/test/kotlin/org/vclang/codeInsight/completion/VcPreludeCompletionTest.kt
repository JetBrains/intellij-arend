package org.vclang.codeInsight.completion

class VcPreludeCompletionTest : VcCompletionTestBase() {

    fun `test prelude`() = checkSingleCompletion("zero", """\function _ : z{-caret-}""")

    fun `test prelude visibility`() = checkNoCompletion("""z{-caret-}""")
}
