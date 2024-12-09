package org.arend.quickfix

import com.intellij.openapi.command.WriteCommandAction
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.Precedence
import org.arend.refactoring.ImportFileAction
import org.arend.refactoring.doAddIdToUsing
import org.arend.refactoring.doRemoveRefFromStatCmd
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class ResolveRefQuickFixTest : QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.import.fix")

    private fun simpleImportFixTest(contents: String, resultingContent: String) =
        simpleQuickFixTest(fixName, contents, resultingContent)

    private fun checkNoImport(@Language("Arend") contents: String) = checkNoQuickFixes(fixName, contents)

    private val fileA =
        """
            -- ! A.ard
            \func a => 0 \where
                \func b => 0 \where
                    \func c => 0 \where
                       \func d => 0
            \func e => 0
        """

    private val fileC =
        """
            -- ! C.ard
            \func f => 0 \where
              \func f => 1 \where
                \func f => 2
        """

    private val fileD =
        """
            -- ! D.ard
            \func g => 0
            \func \infixl 6 ++ (a b : Nat) => a
        """

    private val fileE =
        """
            -- ! E.ard
            \func a => 0
            \func e => 0
        """

    fun `test completing short name to full name if imports are correct`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test importing of libraries if imports are not correct`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \func d => {-caret-}a
            """,
            """
                \import A

                \func d => a
            """)

    fun `test importing of libraries if imports are not correct 2`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \func d => {-caret-}b
            """,
            """
                \import A

                \func d => a.b
            """)

    fun `test adding function name to empty using list`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A ()
                \func d => {-caret-}b
            """,
            """
                \import A (a)
                \func d => a.b
            """)

    fun `test adding function name to nonempty using list 1`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A (e)
                \func d => {-caret-}c
            """,
            """
                \import A (a, e)
                \func d => a.b.c
            """)

    fun `test adding function name to nonempty using list 2`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A (a)
                \func d => {-caret-}e
            """,
            """
                \import A (a, e)
                \func d => e
            """)

    fun `test removing function name from the singleton list of hidden definitions`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A \hiding ( a )
                \func d => {-caret-}b
            """,
            """
                \import A

                \func d => a.b
            """)

    fun `test removing function name from the list of hidden definitions`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A \hiding ( a , e)
                \func d => {-caret-}b
            """,
            """
                \import A \hiding (e)
                \func d => a.b
            """)

    fun `test removing function name from the list of hidden definitions 2`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A \hiding ( a , e)
                \func d => {-caret-}e
            """,
            """
                \import A \hiding ( a)
                \func d => e
            """)

    fun `test that adding library import preserves alphabetic order 1` () = simpleImportFixTest(fileA+fileC+fileD+
            """
                -- ! B.ard
                \import A
                \open a
                \import D
                \func d => {-caret-}f
            """,
            """
                \import A
                \import C
                \open a
                \import D
                \func d => f
            """)

    fun `test that adding library import preserves alphabetic order 2` () = simpleImportFixTest(fileA+fileC+fileD+
            """
                -- ! B.ard
                \import C
                \import D
                \func d => {-caret-}c
            """,
            """
                \import A
                \import C
                \import D
                \func d => a.b.c
            """)

    fun `test that adding library import preserves alphabetic order 3` () = simpleImportFixTest(fileA+fileC+fileD+
            """
                -- ! B.ard
                \import A
                \import C
                \func d => {-caret-}g
            """,
            """
                \import A
                \import C
                \import D

                \func d => g
            """)

    fun `test that open commands are taken into account`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A
                \open a
                \func d => {-caret-}c
            """,
            """
                \import A
                \open a
                \func d => b.c
            """)

    fun `test that clashing names are taken into account 1`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A
                \open a
                \func a => 0
                \func d => {-caret-}c
            """,
            """
                \import A
                \open a
                \func a => 0
                \func d => A.a.b.c
            """)

    fun `test reference fixing in namespace commands`() = simpleImportFixTest(fileA +
            """
               -- ! B.ard
               \open b{-caret-}
            """, """
               \import A
               \open a.b
            """)

    fun `test that using keyword has effect when clashing names are analyzed`() = simpleImportFixTest(fileE +
            """
                -- ! A.ard
                \func b => 0
                \func a => 0

                -- ! B.ard
                \import A \using (b \as b')
                \func d => {-caret-}e
            """,
            """
                \import A \using (b \as b')
                \import E (e)

                \func d => e
            """)

    fun `test that using keyword has effect when clashing names are analyzed 2`() = simpleImportFixTest(fileE +
            """
                -- ! A.ard
                \func b => 0
                \func a => 0

                -- ! B.ard
                \import A (b \as b')
                \func d => {-caret-}e
            """,
            """
                \import A (b \as b')
                \import E

                \func d => e
            """)


/*  private fun testB1(prefix : String, s : String) =
            """
                $prefix\import C (f \as f')
                \open f' (f \as f'')
                \func d => $s
            """


    private fun `test that clashing names are taken into account`(s : String) =
            simpleImportFixTest(fileC + testB1("                -- ! B.ard\n                ", "{-caret-}f"), testB1("", s))

    fun `test that clashing names are taken into account 2-1`() = `test that clashing names are taken into account`("f''")
    fun `test that clashing names are taken into account 2-2`() = `test that clashing names are taken into account`("f''.f")
    fun `test that clashing names are taken into account 2-3`() = `test that clashing names are taken into account`("f'") */

    fun `test that clashing names are taken into account 3`() = simpleImportFixTest(fileA + fileE +
            """
                -- ! B.ard
                \import E
                \func d => {-caret-}b
            """,
            """
                \import A ()
                \import E
                \func d => A.a.b
            """)


    fun `test that simple renamings are taken into account`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A
                \open a (b \as b')
                \func d => 0 \where {
                  \func e => {-caret-}c
                }
            """,
            """
                \import A
                \open a (b \as b')
                \func d => 0 \where {
                  \func e => b'.c
                }
            """)

    private fun testB2 (prefix : String, s : String) =
            """
                $prefix\import A (a \as a')
                \import A (a \as a'')
                \open a' (b \as b')
                \func d => 0 \where {
                  \open a'' (b \as b'')
                  \func e => $s
                }
            """

    private fun testB3(s : String) =
            simpleImportFixTest(fileA + testB2("                -- ! B.ard\n                ", "{-caret-}c"), testB2("", s))

    fun `test that only the smallest (wrt to lexicographic order) renaming option is shown to the user`() = testB3("b'.c")

    fun `test that shorter names are always preferred`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A (a \as a')
                \import A (a \as a'')
                \open a' (b \as b')
                \func f => 0 \where {
                  \open a''.b (c \as c')
                  \func e => {-caret-}d
                }
            """,
            """
                \import A (a \as a')
                \import A (a \as a'')
                \open a' (b \as b')
                \func f => 0 \where {
                  \open a''.b (c \as c')
                  \func e => c'.d
                }
            """)

    fun `test that renamings are not taken into account when names clash`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A (a \as a')
                \func a' => 0
                \func f => {-caret-}c
            """,
            """
                \import A (a \as a')
                \func a' => 0
                \func f => A.a.b.c
            """)

    fun `test that everything works in the situation when there is only one file`() = simpleImportFixTest(
            """
                -- ! A.ard
                \func a => 0 \where
                  \func b => 1 \where
                    \func c => 2
                \open a (b \as b')
                \func d => 0 \where {
                  \func e => {-caret-}c
                }
            """,
            """
                \func a => 0 \where
                  \func b => 1 \where
                    \func c => 2
                \open a (b \as b')
                \func d => 0 \where {
                  \func e => b'.c
                }
            """)

    private val fileF =
            """
            -- ! F.ard
                \class Test1 (El : \Set) {
                    | \infixl 7 * : El -> El -> El
                }
            """

    fun `test that class fields are supported`() = simpleImportFixTest(fileF +
            """
                -- ! B.ard
                \func test => 1 *{-caret-} 1
            """,
            """
                \import F

                \func test => 1 * 1
            """)

    fun `test that quickfixes work for infix operators`() = simpleImportFixTest(fileD +
            """
                -- ! B.ard
                \import D(++ \as +)
                
                \func test => 1 `++`{-caret-} 1
            """,
            """
                \import D(++ \as +)
                
                \func test => 1 `+` 1
            """)

    fun `test that quickfixes work for postfix operators`() = simpleImportFixTest(fileD +
            """
                -- ! B.ard
                \import D(++ \as +)
                
                \func test => 1 `++{-caret-}
            """,
            """
                \import D(++ \as +)
                
                \func test => 1 `+
            """)

    fun `test that possible name clashes are prevented by using empty imports`() = simpleImportFixTest(fileA + fileE +
            """
                -- ! B.ard
                \import E
                \func f => e
                \func g => b{-caret-}
            """,
            """
                \import A ()
                \import E
                \func f => e
                \func g => A.a.b
            """)

    fun `test that possible name clashes are prevented by using partial imports`() = simpleImportFixTest(fileA + fileE +
            """
                -- ! B.ard
                \import E (e)
                \func f => e
                \func g => b{-caret-}
            """,
            """
                \import A (a)
                \import E (e)
                \func f => e
                \func g => a.b
            """)

    fun `test that renames are preferred to imports`() = simpleImportFixTest(fileF +
            """
                -- ! B.ard
                \import F (Test1 \as Test)
                \func test => 1 *{-caret-} 2
            """,
            """
                \import F (*, Test1 \as Test)
                \func test => 1 * 2
            """)

    fun `test that only member is imported if there are no name clashes`() = simpleImportFixTest(fileF +
            """
                -- ! B.ard
                \import F ()
                \func test => 1 *{-caret-} 2
            """,
            """
                \import F (*)
                \func test => 1 * 2
            """)

    fun `test that only member is imported in the situation when there is a name clash for the parent`() = simpleImportFixTest(fileF +
            """
                -- ! C.ard
                \func Test1 => 0
                -- ! B.ard
                \import C
                \import F ()
                \func test => 1 *{-caret-} 2
            """,
            """
                \import C
                \import F (*)
                \func test => 1 * 2
            """)

    fun `test that deliberate empty imports left by the user lead to the minimalistic import mode not being activated`() = simpleImportFixTest(fileA + fileE +
            """
                -- ! B.ard
                \import E ()
                \func g => b{-caret-}
            """,
            """
                \import A
                \import E ()
                \func g => a.b
            """)

    fun `test function name is not removed from the list of hidden definitions if there are clashing names`() = simpleImportFixTest(fileA + fileE +
            """
                -- ! B.ard
                \import A \hiding (a, e)
                \import E (a)
                \func d => {-caret-}b
            """,
            """
                \import A \hiding (a, e)
                \import E (a)
                \func d => A.a.b
            """)

    fun `test that only one item is removed from the list of hidden definitions`() = simpleImportFixTest(fileF +
            """
                -- ! B.ard
                \import F \hiding (Test1, *)
                \func test => 1 *{-caret-} 2
            """,
            """
                \import F \hiding (Test1)
                \func test => 1 * 2
            """)

    fun `test that nothing is removed from hidden definitions if renaming to very long name is used anyway`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A \hiding (a, e)
                \func a => 1
                \func d => {-caret-}b
            """,
            """
                \import A \hiding (a, e)
                \func a => 1
                \func d => A.a.b
            """)

    fun `test that nothing is added to the using list if renaming to very long name is used anyway`() = simpleImportFixTest(fileA +
            """
                -- ! B.ard
                \import A (e)
                \func a => 1
                \func d => {-caret-}b
            """,
            """
                \import A (e)
                \func a => 1
                \func d => A.a.b
            """)

    fun `test that empty using list is used in import command if very long name is used anyway`() = simpleImportFixTest(fileA + fileE +
            """
                -- ! B.ard
                \import E (e)
                \func a => 1
                \func d => {-caret-}b
            """,
            """
                \import A ()
                \import E (e)
                \func a => 1
                \func d => A.a.b
            """)

    fun `test that top-level open commands also can activate minimalistic import mode 1`() = simpleImportFixTest(fileA +
            """
                -- ! C.ard
                \func j => 1 \where
                  \func e => 1

                -- ! B.ard
                \import C
                \open j
                \func d => {-caret-}b
            """,
            """
                \import A (a)
                \import C
                \open j
                \func d => a.b
            """)

    fun `test that top-level open commands also can activate minimalistic import mode 2`() = simpleImportFixTest(fileA +
            """
                -- ! C.ard
                \func j => 1 \where
                  \func a => 1

                -- ! B.ard
                \import C
                \open j
                \func d => {-caret-}b
            """,
            """
                \import A ()
                \import C
                \open j
                \func d => A.a.b
            """)

    fun `test importing nontop-level items from prelude`() = simpleImportFixTest(
            """
               -- ! A.ard
               \func lol => div{-caret-}
            """,
            """
               \func lol => Nat.div
            """)

    fun `test importing nontop-level items from prelude with obstructed scopes`() = simpleImportFixTest(
            """
                -- ! A.ard
                \func Nat => 0
                \func lol => div{-caret-}
            """,
            """
                \import Prelude

                \func Nat => 0
                \func lol => Prelude.Nat.div
            """)

    fun `test importing nontop-level items from partially imported prelude`() = simpleImportFixTest(
            """
                -- ! A.ard
                \import Prelude(I)
                \func Nat => 0
                \func lol => div{-caret-}
            """,
            """
                \import Prelude(I)
                \func Nat => 0
                \func lol => Prelude.Nat.div
            """
    )

     fun `test strange behavior of arend import commands`() = checkNoImport(
            """
                -- ! A.ard
                \func lol => 0

                -- ! B.ard
                \func f => 1

                -- ! C.ard
                \import A(f)
                \import B

                \func a => f{-caret-}
            """)

    fun `test spacing in import commands`() = simpleImportFixTest(
            """
                -- ! A.ard
                \func lol => 1
                -- ! B.ard

                {-
                ---

                -}

                \func lol2 => lol{-caret-}
            """,
            """
                \import A

                {-
                ---

                -}

                \func lol2 => lol
            """)

    fun `test AddIdToUsing action on an incomplete namespace command`() =  simpleActionTest(
            "\\import A \\using {-caret-}",
            "\\import A \\using (b \\as b')") { file ->
        val cmd = file.statements.first().statCmd!!
        WriteCommandAction.runWriteCommandAction(project, "", null, {
            doAddIdToUsing(cmd, listOf(Pair("b", "b'")))
        }, file) }

    fun `test ImportFileAction on empty file`() = simpleActionTest(
            "{-caret-}", "\\import Main") { file ->
        val action = ImportFileAction(file, file, null)
        WriteCommandAction.runWriteCommandAction(project, "", null, { action.execute() }, file) }

    fun `test RemoveFromHidingAction on namespace command with comments`() = simpleActionTest(
            "\\import Prelude \\hiding (Nat {- 1 -} , {- 2 -} Int {- 3 -} , {- 4 -} Path){-caret-}",
            "\\import Prelude \\hiding (Nat {- 1 -}  {- 2 -}  {- 3 -} , {- 4 -} Path)") {file ->
        val cmd = file.statements.first().statCmd!!
        val ref = cmd.hiddenReferences[1].refIdentifier
        WriteCommandAction.runWriteCommandAction(project, "", null, {
            doRemoveRefFromStatCmd(ref)
        }, file)
    }


    fun `test that resolve ref quick fixes are disabled inside class extensions`() =
            checkNoImport("\\func bar => 0\n\\class A {}\n\\func f => \\new A {| bar{-caret-} => 1}")

    fun `test that resolve ref quick fixes are disabled for matched variable of elim expressions`() =
            checkNoImport("\\func bar => 0 \\func foo(a : Nat): Nat \\elim bar{-caret-} | _ => zero")

    fun `test no import for class field tele`() =
            simpleImportFixTest(
             """
                -- ! A.ard
                \class C (x : Nat) | y : Nat

                -- ! B.ard
                \func foo => x{-caret-}
            """,
            """
                \import A

                \func foo => C.x
            """)

    fun `test that shorter names are always preferred 2`() =
            simpleImportFixTest("""
                -- ! Foo.ard
                \module FooM \where {
                  \func lol => 1
                }

                -- ! B.ard
                \func foo => lol{-caret-}
            """, """
                \import Foo

                \func foo => FooM.lol""")

    fun `test that simple renamings are taken into account 2`() =
            simpleImportFixTest("""
                -- ! Foo.ard
                \module FooM \where {
                  \func lol => 1
                }

                -- ! B.ard
                \import Foo
                \open Foo.FooM (lol \as lol')

                \func foo => lol{-caret-}
            """, """
                \import Foo
                \open Foo.FooM (lol \as lol')

                \func foo => lol'""")

    fun `test that simple renamings are taken into account 3`() =
            simpleImportFixTest("""
                -- ! Foo.ard
                \func lol => 1

                -- ! B.ard
                \import Foo \using (lol \as lol')

                \func foo => lol{-caret-}
            """, """
                \import Foo \using (lol \as lol')

                \func foo => lol'""")


    fun `test imports in patterns`() =
            simpleImportFixTest("""
               -- ! Main.ard
               \import Prelude (Nat)

               \func lol (a : Nat) \with
               | 0 => 0
               | suc{-caret-} a' => 1
            ""","""
               \import Prelude (Nat, suc)

               \func lol (a : Nat) \with
               | 0 => 0
               | suc a' => 1
            """)

    fun `test importing files`() =
            simpleImportFixTest("""
                -- ! Dir/A.ard 
                \func foo => 0 
                -- ! B.ard 
                \func bar => A{-caret-}
            """,
            """
                \import Dir.A    
            
                \func bar => Dir.A            
            """)

    fun `test importing infix functions`() =
            simpleImportFixTest("""
                \func test => 0 `mod`{-caret-} 1
            """, """
                \func test => 0 Nat.`mod` 1 
            """)

    fun `test importing postfix function 1`() =
            simpleImportFixTest("""
                \func test => 0 `mod{-caret-} 1
            """, """
                \func test => 0 Nat.`mod 1 
            """)

    fun `test importing postfix function 2`() =
            simpleImportFixTest("""
                -- ! A.ard
                \module Foo \where {
                  \func lol (a b : Nat) => a
                }
                -- ! Main.ard
                \func foo (a : Nat) => a `lol{-caret-}
            """, """
                \import A
                
                \func foo (a : Nat) => a Foo.`lol{-caret-}
            """)

    fun `test importing postfix function 3`() =
            simpleImportFixTest("""
               \func test3 => `mod{-caret-} 1 
            """, """
               \func test3 => Nat.`mod 1 
            """)

    fun `test importing of meta`() {
        addGeneratedModules {
            declare(ModulePath("Foo"), LongName("foo"), "", Precedence.DEFAULT, null)
        }

        simpleImportFixTest("""
            \func d => {-caret-}foo
        """,
        """
            \import Foo

            \func d => foo
        """)
    }

    fun `test alias import 1`() =
        simpleImportFixTest("""
               -- ! A.ard
               \func foo \alias bar => 101 
               -- ! Main.ard
               \func fubar => foo{-caret-} 
            """, """
               \import A
               
               \func fubar => bar
            """)

    fun `test alias import 2`() =
        simpleImportFixTest("""
               -- ! A.ard
               \func M \alias N => 202 \where {
                 \func foo \alias bar => 101
               }
               -- ! Main.ard
               \func fubar => bar{-caret-} 
            """, """
               \import A
               
               \func fubar => N.bar
            """)

    fun `test accessibility modifiers 1`() =
        simpleImportFixTest("""
               -- ! A.ard               
               \protected \func foo => 101
               -- ! Main.ard
               \import A ()
               
               \func fubar => foo{-caret-}
        """, """               
               \import A (foo)
               
               \func fubar => foo
        """)
    fun `test accessibility modifiers 2`() =
        simpleImportFixTest("""
               -- ! A.ard               
               \protected \func foo => 101
               -- ! Main.ard
               \import A
               
               \func fubar => foo{-caret-}
        """, """               
               \import A \using (foo)
               
               \func fubar => foo
        """)

    fun `test accessibility modifiers 3`() =
        simpleImportFixTest("""
               -- ! A.ard               
               \protected \func foo => 101
               -- ! Main.ard
               \import A ()
               
               \func fubar => foo{-caret-}
        """, """               
               \import A (foo)
               
               \func fubar => foo
        """)
}
