package org.arend.quickfix

import org.intellij.lang.annotations.Language
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ArendFile

abstract class QuickFixTestBase : ArendTestBase() {
    private val importQfName = "Fix import"

    protected fun simpleQuickFixTest (fixName: String,
                                    @Language("Arend") contents: String,
                                    @Language("Arend") resultingContent: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        myFixture.doHighlighting()

        val quickfix = myFixture.findSingleIntention(fixName)
        myFixture.launchAction(quickfix)

        testCaret(resultingContent)
    }

    protected fun simpleImportFixTest(@Language("Arend") contents: String, @Language("Arend") resultingContent: String) = simpleQuickFixTest(importQfName, contents, resultingContent)


    protected fun checkNoQuickFixes(fixName: String, @Language("Arend") contents: String) {
        InlineFile(contents).withCaret()
        assert(myFixture.getAvailableIntention(fixName, "Main.ard") == null)
    }

    protected fun checkNoImport(@Language("Arend") contents: String) = checkNoQuickFixes(importQfName, contents)

    protected fun simpleActionTest (@Language("Arend") contents: String, @Language("Arend") resultingContent: String, f: (ArendFile) -> Unit) {
        InlineFile(contents).withCaret()

        val file = myFixture.configureByFile("Main.ard")
        if (file is ArendFile)
            f.invoke(file)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }

}
