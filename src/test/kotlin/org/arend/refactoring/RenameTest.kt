package org.arend.refactoring

import org.intellij.lang.annotations.Language
import org.arend.ArendTestBase

class RenameTest : ArendTestBase() {

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
                \func _ : _ `{-caret-}foo` _
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
                \func _ : _ `{-caret-}foo` _
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
                \func _ : _ `{-caret-}foo
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
                \func _ : _ `{-caret-}foo
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
                \func _ : _ `{-caret-}++
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
                \func _ : _ `{-caret-}++
            """,
            """
                \func **
                \func _ : _ `**
            """
    )

    fun `test rename class parameters`() = doTest("q",
            "\\class Lol (p{-caret-} : Nat) {}\n\\func lol => Lol.p",
            "\\class Lol (q : Nat) {}\n\\func lol => Lol.q")

    fun `test rename file`() = checkByDirectory(
            """
                --! Main.ard
                \import Foo

                --! Foo.ard
                -- empty
            """,
            """
                --! Main.ard
                \import Bar

                --! Bar.ard
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("Foo.ard")
        myFixture.renameElement(file, "Bar.ard")
    }

    fun `test rename file without extension`() = checkByDirectory(
            """
                --! Main.ard
                \import Foo

                --! Foo.ard
                -- empty
            """,
            """
                --! Main.ard
                \import Bar

                --! Bar.ard
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("Foo.ard")
        myFixture.renameElement(file, "Bar")
    }

    fun `test rename directory`() = checkByDirectory(
            """
                --! Main.ard
                \import DirA.Foo

                --! DirA/Foo.ard
                -- empty
            """,
            """
                --! Main.ard
                \import DirB.Foo

                --! DirB/Foo.ard
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("DirA/Foo.ard")
        myFixture.renameElement(file.containingDirectory, "DirB")
    }

    private fun doTest(
            newName: String,
            @Language("Arend") before: String,
            @Language("Arend") after: String
    ) {
        InlineFile(before).withCaret()
        myFixture.renameElementAtCaret(newName)
        myFixture.checkResult(after)
    }
}
