package org.vclang.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.openapi.command.WriteCommandAction

class ResolveRefQuickFixTest : QuickFixTestBase() {
    private val fileA =
        """
            --! A.vc
            \func a => 0 \where
                \func b => 0 \where
                    \func c => 0 \where
                       \func d => 0
            \func e => 0
        """

    private val fileC =
        """
            --! C.vc
            \func f => 0 \where
              \func f => 1 \where
                \func f => 2
        """

    private val fileD =
        """
            --! D.vc
            \func g => 0
            \func \infixl 6 ++ (a b : Nat) => a
        """

    private val fileE =
        """
            --! E.vc
            \func a => 0
            \func e => 0
        """

    fun `test completing short name to full name if imports are correct`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test importing of libraries if imports are not correct`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \func d => {-caret-}a
            """,
            """
                \import A
                \func d => a
            """)

    fun `test importing of libraries if imports are not correct 2`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test adding function name to empty using list`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A ()
                \func d => {-caret-}b
            """,
            """
                \import A (a)
                \func d => a.b
            """)

    fun `test adding function name to nonempty using list 1`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A (e)
                \func d => {-caret-}c
            """,
            """
                \import A (a, e)
                \func d => a.b.c
            """)

    fun `test adding function name to nonempty using list 2`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A (a)
                \func d => {-caret-}e
            """,
            """
                \import A (a, e)
                \func d => e
            """)

    fun `test removing function name from the singleton list of hidden definitions`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A \hiding ( a )
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test removing function name from the list of hidden definitions`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A \hiding ( a , e)
                \func d => {-caret-}b
            """,
            """
                \import A \hiding (e)
                \func d => a.b
            """)

    fun `test removing function name from the list of hidden definitions 2`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A \hiding ( a , e)
                \func d => {-caret-}e
            """,
            """
                \import A \hiding ( a)
                \func d => e
            """)

    fun `test that adding library import preserves alphabetic order 1` () = simpleImportFixTest(fileA+fileC+fileD+
            """
                --! B.vc
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
                --! B.vc
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
                --! B.vc
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
                --! B.vc
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
                --! B.vc
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

    fun `test that using keyword has effect when clashing names are analyzed`() = simpleImportFixTest(fileE +
            """
                --! A.vc
                \func b => 0
                \func a => 0

                --! B.vc
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
                --! A.vc
                \func b => 0
                \func a => 0

                --! B.vc
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
            simpleImportFixTest(fileC + testB1("                --! B.vc\n                ", "{-caret-}f"), testB1("", s))

    fun `test that clashing names are taken into account 2-1`() = `test that clashing names are taken into account`("f''")
    fun `test that clashing names are taken into account 2-2`() = `test that clashing names are taken into account`("f''.f")
    fun `test that clashing names are taken into account 2-3`() = `test that clashing names are taken into account`("f'") */

    fun `test that clashing names are taken into account 3`() = simpleImportFixTest(fileA + fileE +
            """
                --! B.vc
                \import E
                \func d => {-caret-}b
            """,
            """
                \import A ()
                \import E
                \func d => A.a.b
            """)


    fun `test that simple renamings are taking into account`() = simpleImportFixTest(fileA +
            """
                --! B.vc
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
            simpleImportFixTest(fileA + testB2("                --! B.vc\n                ", "{-caret-}c"), testB2("", s))

    fun `test that only the smallest (wrt to lexicographic order) renaming option is shown to the user`() = testB3("b'.c")

    fun `test that shorter names are always preferred`() = simpleImportFixTest(fileA +
            """
                --! B.vc
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
                --! B.vc
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
                --! A.vc
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
            --! F.vc
                \class Test1 (El : \Set) {
                    | \infixl 7 * : El -> El -> El
                }

                \class Test2 => Test1 {
                    | * => \infixl 6 +
                }
            """

    fun `test that class fields are supported`() = simpleImportFixTest(fileF +
            """
                --! B.vc
                \func test => 1 *{-caret-} 1
            """,
            """
                \import F
                \func test => 1 * 1
            """)

    fun `test that class synonyms are supported`() = simpleImportFixTest(fileF +
            """
                --! B.vc
                \func test => 1 +{-caret-} 1
            """,
            """
                \import F
                \func test => 1 + 1
            """)

    fun `test that infix quickfixes work for infix operators`() = simpleImportFixTest(fileD +
            """
                --! B.vc
                \func test => 1 `++`{-caret-} 1
            """,
            """
                \import D
                \func test => 1 `++` 1
            """)

    fun `test that possible name clashes are prevented by using empty imports`() = simpleImportFixTest(fileA + fileE +
            """
                --! B.vc
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
                --! B.vc
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
                --! B.vc
                \import F (Test1 \as Test)
                \func test => 1 *{-caret-} 2
            """,
            """
                \import F (Test1 \as Test)
                \func test => 1 Test.* 2
            """)

    fun `test that only member is imported if there are no name clashes`() = simpleImportFixTest(fileF +
            """
                --! B.vc
                \import F ()
                \func test => 1 *{-caret-} 2
            """,
            """
                \import F (*)
                \func test => 1 * 2
            """)

    fun `test that only member is imported in the situation when there is a name clash for the parent`() = simpleImportFixTest(fileF +
            """
                --! C.vc
                \func Test1 => 0
                --! B.vc
                \import C
                \import F ()
                \func test => 1 *{-caret-} 2
            """,
            """
                \import C
                \import F (*)
                \func test => 1 * 2
            """)

    fun `test that deliberate empty imports left by the user lead to the "cautious mode" not being activated`() = simpleImportFixTest(fileA + fileE +
            """
                --! B.vc
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
                --! B.vc
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
                --! B.vc
                \import F \hiding (Test1, *)
                \func test => 1 *{-caret-} 2
            """,
            """
                \import F \hiding (Test1)
                \func test => 1 * 2
            """)

    fun `test that nothing is removed from hidden definitions if renaming to "very long name" is used anyway`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A \hiding (a, e)
                \func a => 1
                \func d => {-caret-}b
            """,
            """
                \import A \hiding (a, e)
                \func a => 1
                \func d => A.a.b
            """)

    fun `test that nothing is added to the "using" list if renaming to "very long name" is used anyway`() = simpleImportFixTest(fileA +
            """
                --! B.vc
                \import A (e)
                \func a => 1
                \func d => {-caret-}b
            """,
            """
                \import A (e)
                \func a => 1
                \func d => A.a.b
            """)

    fun `test that empty using list is used in import command if "very long name" is used anyway`() = simpleImportFixTest(fileA + fileE +
            """
                --! B.vc
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

    fun `test that top-level open commands also can activate "cautious mode" 1`() = simpleImportFixTest(fileA +
            """
                --! C.vc
                \func j => 1 \where
                  \func e => 1

                --! B.vc
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

    fun `test that top-level open commands also can activate "cautious mode" 2`() = simpleImportFixTest(fileA +
            """
                --! C.vc
                \func j => 1 \where
                  \func a => 1

                --! B.vc
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

    /* fun `test strange behavior of vclang import commands`() = simpleImportFixTest(
            """
                --! A.vc
                \func lol => 0

                --! B.vc
                \func f => 1

                --! C.vc
                \import A(f)
                \import B

                \func a => f{-caret-}
            """,
            """
                \import A(f)
                \import B

                \func a => f
            """) */

    fun `test that AddIdToUsingId works in the situation when there is a broken using command`() =  simpleActionTest(
            "\\import A \\using {-caret-}",
            "\\import A \\using (a, b, z)", {file ->
        val cmd = file.namespaceCommands.first()
        val action = AddIdToUsingAction(cmd, listOf("a", "z", "b"))
        WriteCommandAction.runWriteCommandAction(project, QuickFixBundle.message("add.import"), null, Runnable { action.execute(myFixture.editor) }, file) })

    fun `test that resolve ref quick fixes are disabled inside class extensions`() =
            checkNoImport("\\func bar => 0\n\\class A {}\n\\func f => \\new A {| bar{-caret-} => 1}")
}