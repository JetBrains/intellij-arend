package org.vclang.quickfix

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
        """

    private val fileE =
        """
            --! E.vc
            \func a => 0
        """

    fun `test completing short name to full name if imports are correct`() = simpleQuickFixTest("[Rename", fileA +
            """
                --! B.vc
                \import A
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test importing of libraries if imports are not correct`() = simpleQuickFixTest("[Import", fileA +
            """
                --! B.vc
                \func d => {-caret-}a
            """,
            """
                \import A
                \func d => a
            """)

    fun `test importing of libraries if imports are not correct 2`() = simpleQuickFixTest("[Import", fileA +
            """
                --! B.vc
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test adding function name to empty using list`() = simpleQuickFixTest("[Add", fileA +
            """
                --! B.vc
                \import A ()
                \func d => {-caret-}b
            """,
            """
                \import A (a)
                \func d => a.b
            """)

    fun `test adding function name to nonempty using list`() = simpleQuickFixTest("[Add", fileA +
            """
                --! B.vc
                \import A (e)
                \func d => {-caret-}c
            """,
            """
                \import A (a, e)
                \func d => a.b.c
            """)

    fun `test removing function name from the singleton list of hidden definitions`() = simpleQuickFixTest("[Remove", fileA +
            """
                --! B.vc
                \import A \hiding ( a )
                \func d => {-caret-}b
            """,
            """
                \import A
                \func d => a.b
            """)

    fun `test removing function name from the list of hidden definitions`() = simpleQuickFixTest("[Remove", fileA +
            """
                --! B.vc
                \import A \hiding ( a , e)
                \func d => {-caret-}b
            """,
            """
                \import A \hiding (e)
                \func d => a.b
            """)

    fun `test that adding library import preserves alphabetic order 1` () = simpleQuickFixTest("[Import file C]", fileA+fileC+fileD+
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

    fun `test that adding library import preserves alphabetic order 2` () = simpleQuickFixTest("[Import", fileA+fileC+fileD+
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

    fun `test that open commands are taken into account`() = simpleQuickFixTest("[Rename", fileA +
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

    fun `test that clashing names are taken into account 1`() = simpleQuickFixTest("[Rename", fileA +
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

    private fun testB1(prefix : String, s : String) =
            """
                $prefix\import C (f \as f')
                \open f' (f \as f'')
                \func d => $s
            """

    private fun `test that clashing names are taken into account`(s : String) =
            simpleQuickFixTest("[Rename f to $s]", fileC + testB1("                --! B.vc\n                ", "{-caret-}f"), testB1("", s))

    fun `test that clashing names are taken into account 2-1`() = `test that clashing names are taken into account`("f''")
    fun `test that clashing names are taken into account 2-2`() = `test that clashing names are taken into account`("f''.f")
    fun `test that clashing names are taken into account 2-3`() = `test that clashing names are taken into account`("f'")

    fun `test that clashing names are taken into account 3`() = simpleQuickFixTest("[Import", fileA + fileE +
            """
                --! B.vc
                \import E
                \func d => {-caret-}b
            """,
            """
                \import A
                \import E
                \func d => A.a.b
            """)


    fun `test that simple renamings are taking into account`() = simpleQuickFixTest("[Rename", fileA +
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

    private fun `test that all possible variants of renaming are shown to the user`(s : String) =
            simpleQuickFixTest("[Rename c to $s]", fileA + testB2("                --! B.vc\n                ", "{-caret-}c"), testB2("", s))

    fun `test that all possible variants of renaming are shown to the user 1`() = `test that all possible variants of renaming are shown to the user`("b'.c")
    fun `test that all possible variants of renaming are shown to the user 2`() = `test that all possible variants of renaming are shown to the user`("b''.c")

    fun `test that shorter names are always preferred`() = simpleQuickFixTest("[Rename", fileA +
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

    fun `test that renamings are not taken into account when names clash`() = simpleQuickFixTest("[Rename", fileA +
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

    fun `test that everything works in the situation when there is only one file`() = simpleQuickFixTest("[Rename",
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
}