package org.arend.quickfix

import org.intellij.lang.annotations.Language
import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ArendFile

abstract class QuickFixTestBase : ArendTestBase() {
    private val importQfName = "Fix import"

    protected fun configure(@Language("Arend") contents: String) {
        fileTreeFromText(contents).createAndOpenFileWithCaretMarker()
        myFixture.doHighlighting()
    }

    protected fun checkNoQuickFixes(fixName: String, @Language("Arend") contents: String? = null) {
        if (contents != null) {
            InlineFile(contents).withCaret()
        }
        assert(myFixture.getAvailableIntention(fixName, "Main.ard") == null)
    }

    protected fun checkQuickFix(fixName: String, @Language("Arend") resultingContent: String) {
        myFixture.launchAction(myFixture.findSingleIntention(fixName))
        testCaret(resultingContent)
    }

    protected fun simpleQuickFixTest(fixName: String, @Language("Arend") contents: String, @Language("Arend") resultingContent: String) {
        configure(contents)
        checkQuickFix(fixName, resultingContent)
    }

    protected fun simpleImportFixTest(@Language("Arend") contents: String, @Language("Arend") resultingContent: String) =
        simpleQuickFixTest(importQfName, contents, resultingContent)

    protected fun checkNoImport(@Language("Arend") contents: String) = checkNoQuickFixes(importQfName, contents)

    protected fun simpleActionTest (@Language("Arend") contents: String, @Language("Arend") resultingContent: String, f: (ArendFile) -> Unit) {
        InlineFile(contents).withCaret()

        val file = myFixture.configureByFile("Main.ard")
        if (file is ArendFile)
            f.invoke(file)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }

}
