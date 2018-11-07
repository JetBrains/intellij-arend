package org.arend.formatting

import org.arend.ArendTestBase
import org.intellij.lang.annotations.Language

abstract class ArendFormatterTestBase: ArendTestBase(){

    protected fun checkNewLine(@Language("Arend") code: String, @Language("Arend") resultingContent: String) {
        InlineFile(code.trimIndent()).withCaret()
        myFixture.type('\n')

        val contentTrimmed = resultingContent.trimIndent()
        val index = contentTrimmed.indexOf(CARET_MARKER)
        if (index != -1) {
            myFixture.checkResult(contentTrimmed.replace(CARET_MARKER, ""), true)
            assert (index == myFixture.caretOffset)
        } else {
            myFixture.checkResult(contentTrimmed, true)
        }
    }
}