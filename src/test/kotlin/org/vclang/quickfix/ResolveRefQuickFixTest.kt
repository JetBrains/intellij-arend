package org.vclang.quickfix

import org.vclang.VcTestBase
import org.vclang.fileTreeFromText

class ResolveRefQuickFixTest : VcTestBase() {
    val fileA = """
                --! A.vc
                \func aa => 0 \where {
                  \func bb => 0 \where {
                    \func cc => 0
                  }
                }"""

    val fileB = """
                --! B.vc
                \import A
                \func d => {-caret-}bb
                """

    fun test1() {
        simpleQuickFixTest("B.vc", fileA + "\n" + fileB)
    }

    fun simpleQuickFixTest (fileName: String, contents: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        myFixture.doHighlighting()
        myFixture.configureFromTempProjectFile(fileName)

        val quickfixes = myFixture.getAllQuickFixes(fileName)
        assert(quickfixes.size == 1)
        val quickfix = quickfixes[0]

        myFixture.launchAction(quickfix)
    }
}