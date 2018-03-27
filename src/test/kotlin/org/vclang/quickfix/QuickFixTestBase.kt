package org.vclang.quickfix

import org.intellij.lang.annotations.Language
import org.vclang.VcTestBase
import org.vclang.fileTreeFromText

abstract class QuickFixTestBase : VcTestBase() {
    protected fun simpleQuickFixTest (fixName: String,
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