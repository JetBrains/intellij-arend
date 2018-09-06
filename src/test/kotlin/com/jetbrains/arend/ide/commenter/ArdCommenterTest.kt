package com.jetbrains.arend.ide.commenter

import com.intellij.openapi.actionSystem.IdeActions
import com.jetbrains.arend.ide.ArdTestBase

class ArdCommenterTest : ArdTestBase() {
    override val dataPath = "com/jetbrains/arend/ide/commenter/fixtures"

    private fun doTest(actionId: String) {
        myFixture.configureByFile(fileName)
        myFixture.performEditorAction(actionId)
        myFixture.checkResultByFile(fileName.replace(".vc", "_after.vc"), true)
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
