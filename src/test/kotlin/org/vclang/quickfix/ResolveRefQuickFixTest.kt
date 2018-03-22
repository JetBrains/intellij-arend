package org.vclang.quickfix

import org.intellij.lang.annotations.Language
import org.vclang.VcTestBase
import org.vclang.fileTreeFromText

class ResolveRefQuickFixTest : VcTestBase() {
    private val fileA =
        """
            --! A.vc
            \func a => 0 \where
                \func b => 0 \where
                    \func c => 0
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

    fun `test that adding library import preserves alphabetic order #1` () = simpleQuickFixTest("[Import file C]", fileA+fileC+fileD+
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

    fun `test that adding library import preserves alphabetic order #2` () = simpleQuickFixTest("[Import", fileA+fileC+fileD+
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

    fun `test taking into account open commands`() = simpleQuickFixTest("[Rename", fileA +
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


    private fun simpleQuickFixTest (fixName: String,
                                    @Language("Vclang") contents: String,
                                    @Language("Vclang") resultingContent: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        myFixture.doHighlighting()

        val quickfix = myFixture.findSingleIntention(fixName)
        myFixture.launchAction(quickfix)
        myFixture.checkResult(resultingContent.trimIndent(), true)
    }
}