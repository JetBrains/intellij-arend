package org.vclang.quickfix

import org.vclang.VcTestBase
import org.vclang.fileTreeFromText

class ResolveRefQuickFixTest : VcTestBase() {
    val fileA = """
                --! A.vc
                \\func aa => 0 \\where {\n
                  \\func bb => 0 \\where {\n
                    \\func cc => 0\n
                  }\n
                }"""

    val fileB = """
                --! B.vc
                \import A
                \func d => {-caret-}bb
                """

    fun test1 () {
        setUp()

        val fileTree = fileTreeFromText(fileA + "\n" + fileB)
        fileTree.createAndOpenFileWithCaretMarker()
        myFixture.configureFromTempProjectFile("A.vc")
        myFixture.configureFromTempProjectFile("B.vc")
        myFixture.doHighlighting()
        assert(myFixture.getAllQuickFixes("B.vc").size > 0)
    }
}