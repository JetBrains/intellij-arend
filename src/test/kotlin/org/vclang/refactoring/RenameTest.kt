package org.vclang.refactoring

import org.intellij.lang.annotations.Language
import org.vclang.VcTestBase

class RenameTest : VcTestBase() {

    fun `test rename prefix to prefix`() = doTest(
            "bar",
            """
                \func foo
                \func _ : {-caret-}foo
            """,
            """
                \func bar
                \func _ : bar
            """
    )

    fun `test rename prefix to prefix infix`() = doTest(
            "**",
            """
                \func foo
                \func _ : {-caret-}foo
            """,
            """
                \func **
                \func _ : **
            """
    )

    fun `test rename prefix infix to prefix`() = doTest(
            "bar",
            """
                \func ++
                \func _ : {-caret-}++
            """,
            """
                \func bar
                \func _ : bar
            """
    )

    fun `test rename prefix infix to prefix infix`() = doTest(
            "**",
            """
                \func ++
                \func _ : {-caret-}++
            """,
            """
                \func **
                \func _ : **
            """
    )

    fun `test rename infix to infix`() = doTest(
            "**",
            """
                \func \infixl 6 ++
                \func _ : _ {-caret-}++ _
            """,
            """
                \func \infixl 6 **
                \func _ : _ ** _
            """
    )

    fun `test rename infix to infix prefix`() = doTest(
            "bar",
            """
                \func \infixl 6 ++
                \func _ : _ {-caret-}++ _
            """,
            """
                \func \infixl 6 bar
                \func _ : _ bar _
            """
    )

    fun `test rename infix prefix to infix`() = doTest(
            "**",
            """
                \func foo
                \func _ : _ {-caret-}`foo` _
            """,
            """
                \func **
                \func _ : _ `**` _
            """
    )

    fun `test rename infix prefix to infix prefix`() = doTest(
            "bar",
            """
                \func foo
                \func _ : _ {-caret-}`foo` _
            """,
            """
                \func bar
                \func _ : _ `bar` _
            """
    )

    fun `test rename postfix prefix to postfix prefix`() = doTest(
            "bar",
            """
                \func foo
                \func _ : _ {-caret-}`foo
            """,
            """
                \func bar
                \func _ : _ `bar
            """
    )

    fun `test rename postfix prefix to postfix infix`() = doTest(
            "**",
            """
                \func foo
                \func _ : _ {-caret-}`foo
            """,
            """
                \func **
                \func _ : _ `**
            """
    )

    fun `test rename postfix infix to postfix prefix`() = doTest(
            "bar",
            """
                \func ++
                \func _ : _ {-caret-}`++
            """,
            """
                \func bar
                \func _ : _ `bar
            """
    )

    fun `test rename postfix infix to postfix infix`() = doTest(
            "**",
            """
                \func ++
                \func _ : _ {-caret-}`++
            """,
            """
                \func **
                \func _ : _ `**
            """
    )

    fun `test rename file`() = checkByDirectory(
            """
                --! Main.vc
                \import Foo

                --! Foo.vc
                -- empty
            """,
            """
                --! Main.vc
                \import Bar

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
                \import Foo

                --! Foo.vc
                -- empty
            """,
            """
                --! Main.vc
                \import Bar

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
                \import DirA.Foo

                --! DirA/Foo.vc
                -- empty
            """,
            """
                --! Main.vc
                \import DirB.Foo

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
