package org.arend.formatting

import com.intellij.openapi.actionSystem.IdeActions
import org.arend.ArendTestBase
import org.intellij.lang.annotations.Language

abstract class ArendFormatterTestBase : ArendTestBase() {

    protected fun checkNewLine(@Language("Arend") code: String, @Language("Arend") resultingContent: String, count: Int = 1) {
        val c = code.trimIndent()
        InlineFile(c).withCaret()
        for (i in 1..count) myFixture.type('\n')

        testCaret(resultingContent)
    }

    protected fun checkReformat(@Language("Arend") code: String, @Language("Arend") resultingContent: String = code) {
        InlineFile(code.trimIndent())

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }
}