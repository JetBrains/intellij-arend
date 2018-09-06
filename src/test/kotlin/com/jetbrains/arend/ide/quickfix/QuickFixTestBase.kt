package com.jetbrains.arend.ide.quickfix

import com.jetbrains.arend.ide.ArdTestBase
import com.jetbrains.arend.ide.fileTreeFromText
import com.jetbrains.arend.ide.psi.ArdFile
import org.intellij.lang.annotations.Language

abstract class QuickFixTestBase : ArdTestBase() {
    private val importQfName = "Fix import"

    protected fun simpleQuickFixTest(fixName: String,
                                     @Language("Arend") contents: String,
                                     @Language("Arend") resultingContent: String) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        myFixture.doHighlighting()

        val quickfix = myFixture.findSingleIntention(fixName)
        myFixture.launchAction(quickfix)
        val index = resultingContent.trimIndent().indexOf("{-caret-}")
        if (index != -1) {
            myFixture.checkResult(resultingContent.trimIndent().replace("{-caret-}", ""), true)
            assert(index == myFixture.caretOffset)
        } else {
            myFixture.checkResult(resultingContent.trimIndent(), true)
        }

    }

    protected fun simpleImportFixTest(@Language("Arend") contents: String, @Language("Arend") resultingContent: String) = simpleQuickFixTest(importQfName, contents, resultingContent)


    protected fun checkNoQuickFixes(fixName: String, @Language("Arend") contents: String) {
        InlineFile(contents).withCaret()
        assert(myFixture.getAvailableIntention(fixName, "Main.vc") == null)
    }

    protected fun checkNoImport(@Language("Arend") contents: String) = checkNoQuickFixes(importQfName, contents)

    protected fun simpleActionTest(@Language("Arend") contents: String, @Language("Arend") resultingContent: String, f: (ArdFile) -> Unit) {
        InlineFile(contents).withCaret()

        val file = myFixture.configureByFile("Main.vc")
        if (file is ArdFile)
            f.invoke(file)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }

}