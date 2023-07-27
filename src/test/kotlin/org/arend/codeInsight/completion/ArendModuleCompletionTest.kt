package org.arend.codeInsight.completion

class ArendModuleCompletionTest : ArendCompletionTestBase() {
    fun `test module name completion`() = doSingleCompletionMultifile(
            """
                -- ! Main.ard
                \import My{-caret-}

                -- ! MyModule.ard
                -- empty
            """,
            """
                \import MyModule
            """
    )

    fun `test directory name completion`() = doSingleCompletionMultifile(
            """
                -- ! Main.ard
                \import Dir{-caret-}

                -- ! Directory/MyModule.ard
                -- empty
            """,
            """
                \import Directory{-caret-}
            """
    )

    fun `test module name completion subdirectory`() = doSingleCompletionMultifile(
            """
                -- ! Main.ard
                \import Directory.My{-caret-}

                -- ! Directory/MyModule.ard
                -- empty
            """,
            """
                \import Directory.MyModule{-caret-}
            """
    )

    fun `test noVariantsDelegator`() = doSingleCompletionMultifile("""
                -- ! Main.ard
                \import Depend{-caret-}
                -- ! Dir1/Dependency.ard
                -- empty
    """, """
                \import Dir1.Dependency{-caret-}
    """)
}
