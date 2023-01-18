package org.arend.psi

import com.intellij.psi.PsiDocumentManager
import org.arend.ArendTestBase

class ArendFileModificationCountTest : ArendTestBase() {
    fun `test background type checking finished`() {
        val file = InlineFile("""\func foo => 1{-caret-}""").withCaret() as ArendFile
        myFixture.doHighlighting()
        assertTrue(file.isBackgroundTypecheckingFinished)
        type("2")
        assertTrue(file.isBackgroundTypecheckingFinished)
        myFixture.doHighlighting()
        assertTrue(file.isBackgroundTypecheckingFinished)
    }

    fun `test background type checking not finished between typing and highlighting`() {
        val file = InlineFile("""\func foo => 1{-caret-}""").withCaret() as ArendFile
        type("2")
        myFixture.doHighlighting()
        assertTrue(file.isBackgroundTypecheckingFinished)
        type("3")
        assertTrue(file.isBackgroundTypecheckingFinished)
    }

    private fun type(str: String) {
        myFixture.type(str)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}