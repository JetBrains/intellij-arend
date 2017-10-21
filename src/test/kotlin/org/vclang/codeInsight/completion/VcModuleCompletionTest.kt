package org.vclang.codeInsight.completion

class VcModuleCompletionTest : VcCompletionTestBase() {
    fun `test module name completion`() = doSingleCompletionMultiflie(
            """
                --! Main.vc
                \import My{-caret-}

                --! MyModule.vc
                -- empty
            """,
            """
                \import MyModule
            """
    )

    fun `test directory name completion`() = doSingleCompletionMultiflie(
            """
                --! Main.vc
                \import Dir{-caret-}

                --! Directory/MyModule.vc
                -- empty
            """,
            """
                \import Directory{-caret-}
            """
    )

    fun `test module name completion subdirectory`() = doSingleCompletionMultiflie(
            """
                --! Main.vc
                \import Directory.My{-caret-}

                --! Directory/MyModule.vc
                -- empty
            """,
            """
                \import Directory.MyModule{-caret-}
            """
    )
}
