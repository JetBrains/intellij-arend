package org.arend.commenter

import com.intellij.openapi.actionSystem.IdeActions
import org.arend.ArendTestBase

class ArendCommenterTest : ArendTestBase() {
    override val dataPath = "org/arend/commenter/fixtures"

    private fun doTest(actionId: String) {
        myFixture.configureByFile(fileName)
        myFixture.performEditorAction(actionId)
        myFixture.checkResultByFile(fileName.replace(".ard", "_after.ard"), true)
    }

    fun testSingleLine() = doTest(IdeActions.ACTION_COMMENT_LINE)

    fun testMultiLine() = doTest(IdeActions.ACTION_COMMENT_LINE)

    fun testSingleLineBlock() = doTest(IdeActions.ACTION_COMMENT_BLOCK)

    fun testMultiLineBlock() = doTest(IdeActions.ACTION_COMMENT_BLOCK)

    fun testSingleLineUncomment() = doTest(IdeActions.ACTION_COMMENT_LINE)

    fun testMultiLineUncomment() = doTest(IdeActions.ACTION_COMMENT_LINE)

    fun testSingleLineBlockUncomment() = doTest(IdeActions.ACTION_COMMENT_BLOCK)

    fun testMultiLineBlockUncomment() = doTest(IdeActions.ACTION_COMMENT_BLOCK)

    fun testSingleLineUncommentWithSpace() = doTest(IdeActions.ACTION_COMMENT_LINE)

    fun testIndentedSingleLineComment() = doTest(IdeActions.ACTION_COMMENT_LINE)
}
