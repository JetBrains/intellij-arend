package org.arend.quickfix

import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInspection.ex.DisableInspectionToolAction
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import org.arend.ArendTestBase
import org.arend.FileTree
import org.arend.fileTreeFromText
import org.arend.psi.ArendFile

abstract class QuickFixTestBase : ArendTestBase() {
    protected fun configure(contents: String, annotate: Boolean = true): FileTree {
        val result = fileTreeFromText(contents)
        result.createAndOpenFileWithCaretMarker()
        if (annotate) {
            myFixture.doHighlighting()
        }
        return result
    }

    protected fun checkNoQuickFixes(fixName: String, contents: String? = null) {
        if (contents != null) {
            InlineFile(contents).withCaret()
        }
        val intentions = myFixture.filterAvailableIntentions(fixName)
                .filter { !isToolOptionsAction(it) }
                .map { it.text to it }
        assertEmpty("There must be no quick fixes available\n", intentions)
    }

    protected fun checkNoQuickFixesWithMultifile(fixName: String, contents: String? = null) {
        if (contents != null) {
            fileTreeFromText(contents).createAndOpenFileWithCaretMarker()
        }
        val intentions = myFixture.filterAvailableIntentions(fixName)
            .filter { !isToolOptionsAction(it) }
            .map { it.text to it }
        assertEmpty("There must be no quick fixes available\n", intentions)
    }

    protected fun checkQuickFix(fixName: String, resultingContent: String) {
        myFixture.launchAction(myFixture.findSingleIntention(fixName))
        testCaret(resultingContent)
    }

    protected fun simpleQuickFixTest(fixName: String, contents: String, resultingContent: String) {
        configure(contents)
        checkQuickFix(fixName, resultingContent)
    }

    protected fun simpleActionTest (contents: String, resultingContent: String, f: (ArendFile) -> Unit) {
        InlineFile(contents).withCaret()

        val file = myFixture.configureByFile("Main.ard")
        if (file is ArendFile)
            f.invoke(file)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }

    protected fun typedQuickFixTest(fixName: String, contents: String, resultingContent: String) {
        val fileTree = configure(contents, false)
        typecheck(fileTree.fileNames)
        myFixture.doHighlighting()
        checkQuickFix(fixName, resultingContent)
    }

    protected fun typedCheckNoQuickFixes(fixName: String, contents: String) {
        val fileTree = configure(contents, false)
        typecheck(fileTree.fileNames)
        myFixture.doHighlighting()
        assert(myFixture.filterAvailableIntentions(fixName).isEmpty())
    }
}

private fun isToolOptionsAction(action: IntentionAction?) =
        action is IntentionActionDelegate &&
                (action.delegate is EmptyIntentionAction ||
                        action.delegate is EditInspectionToolsSettingsAction ||
                        action.delegate is DisableInspectionToolAction)

