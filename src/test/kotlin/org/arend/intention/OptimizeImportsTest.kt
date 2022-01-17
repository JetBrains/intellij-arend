package org.arend.intention

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import org.arend.ArendTestBase
import org.arend.codeInsight.ArendImportOptimizer
import org.arend.fileTreeFromText
import org.arend.replaceCaretMarker
import org.intellij.lang.annotations.Language

class OptimizeImportsTest : ArendTestBase() {

    private fun doTest(
        @Language("Arend") before: String,
        @Language("Arend") after: String
    ) {
        fileTreeFromText(before).createAndOpenFileWithCaretMarker()
        val optimizer = ArendImportOptimizer()
        WriteCommandAction.runWriteCommandAction(myFixture.project, optimizer.processFile(myFixture.file))
        CodeStyleManager.getInstance(myFixture.project).reformat(myFixture.file)
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    fun `test prelude`() {
        doTest(
            """
            --! Main.ard
            \func foo : Nat => 1{-caret-}
            """, """
            \func foo : Nat => 1
            """
        )
    }
}