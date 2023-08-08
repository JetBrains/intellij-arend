package org.arend.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.arend.ArendTestBase
import org.arend.ext.concrete.expr.ConcreteExpression
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.ExpressionResolver
import org.arend.ext.reference.Precedence
import org.arend.ext.typechecking.ContextData
import org.arend.ext.typechecking.MetaResolver
import org.arend.extImpl.ConcreteFactoryImpl
import org.arend.term.concrete.Concrete
import org.intellij.lang.annotations.Language

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
                -- ! Main.ard
                \import Foo

                -- ! Foo.ard
                -- empty
            """,
            """
                -- ! Main.ard
                \import Bar

                -- ! Bar.ard
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("Foo.ard")
        myFixture.renameElement(file, "Bar.ard")
    }

    fun `test rename file without extension`() = checkByDirectory(
            """
                -- ! Main.ard
                \import Foo

                -- ! Foo.ard
                -- empty
            """,
            """
                -- ! Main.ard
                \import Bar

                -- ! Bar.ard
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("Foo.ard")
        myFixture.renameElement(file, "Bar")
    }

    fun `test rename directory`() = checkByDirectory(
            """
                -- ! Main.ard
                \import DirA.Foo

                -- ! DirA/Foo.ard
                -- empty
            """,
            """
                -- ! Main.ard
                \import DirB.Foo

                -- ! DirB/Foo.ard
                -- empty
            """
    ) {
        val file = myFixture.configureFromTempProjectFile("DirA/Foo.ard")
        myFixture.renameElement(file.containingDirectory, "DirB")
    }

    fun `test rename refIdentifier`() {
        addGeneratedModules {
            declare(ModulePath("Meta"), LongName("myMeta"), "", Precedence.DEFAULT, null, null, null, object : MetaResolver {
                override fun resolvePrefix(resolver: ExpressionResolver, contextData: ContextData): ConcreteExpression {
                    val factory = ConcreteFactoryImpl(contextData.marker.data)
                    val ref = (contextData.arguments[0].expression as Concrete.ReferenceExpression).referent
                    return resolver.resolve(factory.lam(listOf(factory.param(true, ref)), contextData.arguments[1].expression))
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

    fun test226() = doInlineTest("lem2",
            "\\func lem => \\lam t => lem{-caret-} suc (suc t) (lem t)",
            "\\func lem2 => \\lam t => lem2 suc (suc t) (lem2 t)")

    fun testDefIdentifierInPattern1() = doInlineTest("bar", """
       \data D
         | con (\Sigma Nat Nat)
         
       \func foo (d : D) : Nat
         | con ({-caret-}n,m) => n
    """, """
       \data D
         | con (\Sigma Nat Nat)
         
       \func foo (d : D) : Nat
         | con (bar,m) => bar
    """)

    fun testDefIdentifierInPattern2() = doInlineTest("bar", """
       \data D
         | con {Nat} Nat
         
       \func foo (d : D) : Nat
         | con {{-caret-}n} m => n
    """, """
       \data D
         | con {Nat} Nat
         
       \func foo (d : D) : Nat
         | con {bar} m => bar
    """)

    fun `test name starts with a colon`() = doInlineTest(
            ":b",
            "\\func :a{-caret-} => {?}",
            "\\func :b => {?}"
    )

    fun testNsId1() = doTest("fubar1", """
       \module M1 \where {
         \func foo => 101
       }
       
       \module M2 \where {
         \open M1 (foo \as {-caret-}fubar)
         \open M1 (foo \as fubar2)
         \func lol => fubar2 Nat.+ fubar
       }
       
       \module M3 \where {
         \open M1 (foo \as fubar)
         \func lol => fubar Nat.+ fubar
       }
    """, """
       \module M1 \where {
         \func foo => 101
       }
       
       \module M2 \where {
         \open M1 (foo \as fubar1)
         \open M1 (foo \as fubar2)
         \func lol => fubar2 Nat.+ fubar1
       }
       
       \module M3 \where {
         \open M1 (foo \as fubar)
         \func lol => fubar Nat.+ fubar
       }
    """, true)

    fun testNsId2() = doTest("fubar1", """
       \module M1 \where {
         \func foo => 101
       }
       
       \module M2 \where {
         \open M1 (foo \as fubar)
         \open M1 (foo \as fubar2)
         \func lol => fubar2 Nat.+ fubar
       }
       
       \module M3 \where {
         \open M1 (foo \as fubar)
         \func lol => fubar{-caret-} Nat.+ fubar
       }
    """, """
       \module M1 \where {
         \func foo => 101
       }
       
       \module M2 \where {
         \open M1 (foo \as fubar)
         \open M1 (foo \as fubar2)
         \func lol => fubar2 Nat.+ fubar
       }
       
       \module M3 \where {
         \open M1 (foo \as fubar1)
         \func lol => fubar1 Nat.+ fubar1
       }
    """, true)

    fun testNsId3() = doTest("fubar1", """
       \module M0 \where
         \func lol => 101

       \module M1 \where {
         \open M0 (lol \as fubar)
  
         \func foo => fubar
  
         \module M2 \where {
           \open M0 (lol \as fubar)
    
           \func bar => fubar{-caret-}
         }
       }
    """, """
       \module M0 \where
         \func lol => 101

       \module M1 \where {
         \open M0 (lol \as fubar)
  
         \func foo => fubar
  
         \module M2 \where {
           \open M0 (lol \as fubar1)
    
           \func bar => fubar1
         }
       }
    """, true)

    fun testInlineRename() = doTest("foo2", """
        \module M \where {
          \func foo \alias bar => 101
        }
        \module M2 \where {
          \open M (bar \as foo)
          \func test => foo{-caret-}
        }
     """, """
        \module M \where {
          \func foo \alias bar => 101
        }
        \module M2 \where {
          \open M (bar \as foo2)
          \func test => foo2
        }
     """, true)

fun testInlineRename2() = doTest("lol3", """
       \module N \where {
         \func lol \alias lol2 => 101
       }

       \module M \where {
          \open N (lol \as lol)

          \func bar => lol{-caret-}
       }
    """, """
       \module N \where {
         \func lol \alias lol2 => 101
       }

       \module M \where {
          \open N (lol \as lol3)

          \func bar => lol3
       }
    """, true)

    fun testInlineRename3() = doTest("lol3", """
       \module N \where {
         \func lol \alias lol2 => 101
         
         \func bar => lol Nat.+ lol2
       }

       \module M \where {
          \open N (lol{-caret-} \as lol)

          \func bar => lol
       }
    """, """
       \module N \where {
         \func lol3 \alias lol2 => 101
         
         \func bar => lol3 Nat.+ lol2
       }

       \module M \where {
          \open N (lol3 \as lol)

          \func bar => lol
       }
    """, true)

    fun testInlineRename4() = doTest("lol3", """
       \module N \where {
         \func lol2 \alias lol => 101

         \func bar => lol Nat.+ lol2
       }

       \module M \where {
         \open N (lol \as lol)

         \func bar => lol{-caret-}
       }
    """, """
       \module N \where {
         \func lol2 \alias lol => 101

         \func bar => lol Nat.+ lol2
       }

       \module M \where {
         \open N (lol \as lol3)

         \func bar => lol3
       }
    """, true)

    fun testInlineRename5() = doTest("lol3", """
       \module N \where {
         \func lol2 \alias lol => 101

         \func bar => lol Nat.+ lol2
       }

       \module M \where {
         \open N (lo{-caret-}l \as lol)

         \func bar => lol
       }
    """, """
       \module N \where {
         \func lol2 \alias lol3 => 101

         \func bar => lol3 Nat.+ lol2
       }

       \module M \where {
         \open N (lol3 \as lol)

         \func bar => lol
       }
    """, true)

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

    private fun doInlineTest(newName: String,
    @Language("Arend") before: String,
    @Language("Arend") after: String) {
        val psiFile = InlineFile(before).withCaret()
        val editor = myFixture.editor as EditorEx
        val editorContext = editor.dataContext
        val currentCaret = editor.caretModel.currentCaret
        val elementToRename = psiFile.findElementAt(currentCaret.offset)!!.parentOfType<PsiNamedElement>(true)
        val dataContext = DataContext { dataId ->
            when (dataId) {
                PsiElementRenameHandler.DEFAULT_NAME.name -> newName
                else -> editorContext.getData(dataId)
            }
        }
        val handler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)!!
        assert(handler.isAvailableOnDataContext(dataContext))
        if (handler is VariableInplaceRenameHandler) {
            CodeInsightTestUtil.doInlineRename(handler, newName, editor, elementToRename)
        } else {
            assert(false)
        }
        myFixture.checkResult(after)
    }
}
