package org.vclang.quickfix

import org.intellij.lang.annotations.Language
import org.vclang.VcTestBase
import org.vclang.fileTreeFromText
import org.vclang.psi.VcFile

abstract class QuickFixTestBase : VcTestBase() {
    private val importQfName = "Fix import"

    protected fun simpleQuickFixTest (fixName: String,
                                    @Language("Vclang") contents: String,
                                    @Language("Vclang") resultingContent: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        myFixture.doHighlighting()

        val quickfix = myFixture.findSingleIntention(fixName)
        myFixture.launchAction(quickfix)
        val index = resultingContent.trimIndent().indexOf("{-caret-}")
        if (index != -1) {
            myFixture.checkResult(resultingContent.trimIndent().replace("{-caret-}", ""), true)
            assert (index == myFixture.caretOffset)
        } else {
            myFixture.checkResult(resultingContent.trimIndent(), true)
        }

    }

    protected fun simpleImportFixTest(@Language("Vclang") contents: String, @Language("Vclang") resultingContent: String) = simpleQuickFixTest(importQfName, contents, resultingContent)


    protected fun checkNoQuickFixes(fixName: String, @Language("Vclang") contents: String) {
        InlineFile(contents).withCaret()
        assert(myFixture.getAvailableIntention(fixName, "Main.vc") == null)
    }

    protected fun checkNoImport(@Language("Vclang") contents: String) = checkNoQuickFixes(importQfName, contents)

    protected fun simpleActionTest (@Language("Vclang") contents: String, @Language("Vclang") resultingContent: String, f: (VcFile) -> Unit) {
        InlineFile(contents).withCaret()

        val file = myFixture.configureByFile("Main.vc")
        if (file is VcFile)
            f.invoke(file)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }

}