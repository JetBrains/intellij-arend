package org.arend.refactoring

import org.intellij.lang.annotations.Language
import org.arend.ArendTestBase
import org.arend.ext.concrete.expr.ConcreteArgument
import org.arend.ext.concrete.expr.ConcreteExpression
import org.arend.ext.concrete.expr.ConcreteReferenceExpression
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.ExpressionResolver
import org.arend.ext.reference.Precedence
import org.arend.ext.typechecking.MetaResolver
import org.arend.extImpl.ConcreteFactoryImpl
import org.arend.term.concrete.Concrete

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

    fun `test rename definition with alias 1`() = doTest("foo2",
            "\\func foo \\alias bar{-caret-}\n\n\\func fubar1 => foo\n\n\\func fubar2 => bar",
            "\\func foo \\alias foo2\n\n\\func fubar1 => foo\n\n\\func fubar2 => foo2", usingHandler = true)

    fun `test rename definition with alias 2`() = doTest("foo2",
            "\\func foo \\alias bar\n\n\\func fubar1 => foo\n\n\\func fubar2 => bar{-caret-}",
            "\\func foo \\alias foo2\n\n\\func fubar1 => foo\n\n\\func fubar2 => foo2", usingHandler = true)

    fun `test rename definition with alias 3`() = doTest("foo2",
            "\\func foo{-caret-} \\alias bar\n\n\\func fubar1 => foo\n\n\\func fubar2 => bar",
            "\\func foo2 \\alias bar\n\n\\func fubar1 => foo2\n\n\\func fubar2 => bar", usingHandler = true)

    fun `test rename definition with alias 4`() = doTest("foo2",
            "\\func foo \\alias bar\n\n\\func fubar1 => foo{-caret-}\n\n\\func fubar2 => bar",
            "\\func foo2 \\alias bar\n\n\\func fubar1 => foo2\n\n\\func fubar2 => bar", usingHandler = true)

    fun `test rename definition with alias 5`() = doTest("foo2",
            "\\func foo \\alias bar (a b : Nat)\n\n\\func fubar1 => `foo{-caret-} 1\n\n\\func fubar2 => bar",
            "\\func foo2 \\alias bar (a b : Nat)\n\n\\func fubar1 => `foo2 1\n\n\\func fubar2 => bar", usingHandler = true)

    fun `test rename definition with alias 6`() = doTest("foo2",
            "\\func foo{-caret-} \\alias bar (a b : Nat)\n\n\\func fubar1 => `foo 1\n\n\\func fubar2 => bar",
            "\\func foo2 \\alias bar (a b : Nat)\n\n\\func fubar1 => `foo2 1\n\n\\func fubar2 => bar", usingHandler = true)

    fun `test rename nametele defIdentifier with doc comment`() = doTest("fubar",
            "-- | {bar{-caret-}}\n\\func foo (bar : Nat) => bar",
            "-- | {fubar}\n\\func foo (fubar : Nat) => fubar")

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

    fun `test rename refIdentifier`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, null, null, null, object : MetaResolver {
                override fun resolvePrefix(resolver: ExpressionResolver, refExpr: ConcreteReferenceExpression, arguments: List<ConcreteArgument>): ConcreteExpression {
                    val factory = ConcreteFactoryImpl(refExpr.data)
                    val ref = (arguments[0].expression as Concrete.ReferenceExpression).referent
                    return resolver.resolve(factory.lam(listOf(factory.param(true, ref)), arguments[1].expression))
                }
            })
        }

        doTest("xoox", """
           \import Meta 
             
           \func test => myMeta oxxo{-caret-} (oxxo = 0)
        """, """
           \import Meta 
             
           \func test => myMeta xoox (xoox = 0)
        """)
    }

    private fun doTest(
            newName: String,
            @Language("Arend") before: String,
            @Language("Arend") after: String,
            usingHandler: Boolean = false
    ) {
        InlineFile(before).withCaret()
        if (usingHandler) myFixture.renameElementAtCaretUsingHandler(newName) else myFixture.renameElementAtCaret(newName)
        myFixture.checkResult(after)
    }
}
