package org.arend.formatting

import com.intellij.openapi.actionSystem.IdeActions
import org.arend.ArendTestBase
import org.intellij.lang.annotations.Language

abstract class ArendFormatterTestBase : ArendTestBase() {

    protected fun checkNewLine(@Language("Arend") code: String, @Language("Arend") resultingContent: String, count: Int = 1) {
        val c = code.trimIndent()
        InlineFile(c).withCaret()
        for (i in 1..count) myFixture.type('\n')

        val rC = resultingContent.trimIndent()
        val index = rC.indexOf(CARET_MARKER)
        if (index != -1) {
            val contentWithoutMarkers = rC.replace(CARET_MARKER, "")
            myFixture.checkResult(contentWithoutMarkers, false)
            val actualCaret = myFixture.caretOffset
            if (index != actualCaret) {
                if (actualCaret < rC.length) {
                    System.err.println("Expected caret position: \n$rC")
                    System.err.println("Actual caret position: \n${StringBuilder(contentWithoutMarkers).insert(actualCaret, CARET_MARKER)}")
                } else {
                    System.out.println("Expected caret position: $index\n Actual caret position: $actualCaret")
                }
                assert(false)
            }

        } else {
            myFixture.checkResult(rC, true)
        }
    }

    protected fun checkReformat(@Language("Arend") code: String, @Language("Arend") resultingContent: String) {
        InlineFile(code.trimIndent())

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT)

        myFixture.checkResult(resultingContent.trimIndent(), true)
    }
}