package org.vclang.lang.core.completion

class VcModuleCompletionTest : VcCompletionTestBase() {
    fun `test module name completion`() = doSingleCompletionMultiflie(
            """
                --! Main.vc
                \open ::My{-caret-}

                --! MyModule.vc
                -- empty
            """,
            """
                \open ::MyModule
            """
    )

    fun `test module name completion subdirectory`() = doSingleCompletionMultiflie(
            """
                --! Main.vc
                \open ::My{-caret-}

                --! Directory/MyModule.vc
                -- empty
            """,
            """
                \open ::Directory::MyModule{-caret-}
            """
    )
}
