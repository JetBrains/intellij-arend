package org.vclang.refactoring

import org.intellij.lang.annotations.Language
import org.vclang.VcTestBase

class RenameTest : VcTestBase() {

    fun `test rename prefix to prefix`() = doTest(
            "bar",
            """
                \function foo
                \function _ : {-caret-}foo
            """,
            """
                \function bar
                \function _ : bar
            """
    )

    fun `test rename prefix to prefix infix`() = doTest(
            "**",
            """
                \function foo
                \function _ : {-caret-}foo
            """,
            """
                \function **
                \function _ : `**
            """
    )

    fun `test rename prefix infix to prefix`() = doTest(
            "bar",
            """
                \function ++
                \function _ : {-caret-}`++
            """,
            """
                \function bar
                \function _ : bar
            """
    )

    fun `test rename prefix infix to prefix infix`() = doTest(
            "**",
            """
                \function ++
                \function _ : {-caret-}`++
            """,
            """
                \function **
                \function _ : `**
            """
    )

    fun `test rename infix to infix`() = doTest(
            "**",
            """
                \function ++
                \function _ : _ {-caret-}++ _
            """,
            """
                \function **
                \function _ : _ ** _
            """
    )

    fun `test rename infix to infix prefix`() = doTest(
            "bar",
            """
                \function ++
                \function _ : _ {-caret-}++ _
            """,
            """
                \function bar
                \function _ : _ `bar _
            """
    )

    fun `test rename infix prefix to infix`() = doTest(
            "**",
            """
                \function foo
                \function _ : _ {-caret-}`foo _
            """,
            """
                \function **
                \function _ : _ ** _
            """
    )

    fun `test rename infix prefix to infix prefix`() = doTest(
            "bar",
            """
                \function foo
                \function _ : _ {-caret-}`foo _
            """,
            """
                \function bar
                \function _ : _ `bar _
            """
    )

    fun `test rename postfix prefix to postfix prefix`() = doTest(
            "bar",
            """
                \function foo
                \function _ : _ {-caret-}foo`
            """,
            """
                \function bar
                \function _ : _ bar`
            """
    )

    fun `test rename postfix prefix to postfix infix`() = doTest(
            "**",
            """
                \function foo
                \function _ : _ {-caret-}foo`
            """,
            """
                \function **
                \function _ : _ **`
            """
    )

    fun `test rename postfix infix to postfix prefix`() = doTest(
            "bar",
            """
                \function ++
                \function _ : _ {-caret-}++`
            """,
            """
                \function bar
                \function _ : _ bar`
            """
    )

    fun `test rename postfix infix to postfix infix`() = doTest(
            "**",
            """
                \function ++
                \function _ : _ {-caret-}++`
            """,
            """
                \function **
                \function _ : _ **`
            """
    )

    fun `test rename file`() = checkByDirectory(
            """
                --! Main.vc
                \open ::Foo

                --! Foo.vc
                -- empty
            """,
            """
                --! Main.vc
                \open ::Bar

                --! Bar.vc
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("Foo.vc")
        myFixture.renameElement(file, "Bar.vc")
    }

    fun `test rename file without extension`() = checkByDirectory(
            """
                --! Main.vc
                \open ::Foo

                --! Foo.vc
                -- empty
            """,
            """
                --! Main.vc
                \open ::Bar

                --! Bar.vc
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("Foo.vc")
        myFixture.renameElement(file, "Bar")
    }

    fun `test rename directory`() = checkByDirectory(
            """
                --! Main.vc
                \open ::DirA::Foo

                --! DirA/Foo.vc
                -- empty
            """,
            """
                --! Main.vc
                \open ::DirB::Foo

                --! DirB/Foo.vc
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("DirA/Foo.vc")
        myFixture.renameElement(file.containingDirectory, "DirB")
    }

    private fun doTest(
            newName: String,
            @Language("Vclang") before: String,
            @Language("Vclang") after: String
    ) {
        InlineFile(before).withCaret()
        myFixture.renameElementAtCaret(newName)
        myFixture.checkResult(after)
    }
}
