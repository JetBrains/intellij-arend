package org.arend.search

import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.search.proof.ProofSearchUI.Companion.insertDefinition
import org.arend.search.proof.generateProofSearchResults

class ArendProofSearchInsertTest : ArendTestBase() {
    fun testCheckInsert() {
        val result = fileTreeFromText("""
            \data Bool

            \func foo : Nat -> Bool => {?}
            \func lol => {-caret-}
        """.trimIndent())
        result.createAndOpenFileWithCaretMarker()
        val caret = myFixture.editor.caretModel.currentCaret

        typecheck()
        val results = generateProofSearchResults(project, "Nat").filterNotNull().toList()
        assertTrue(results.size == 1)

        insertDefinition(project, results[0].def, caret)
        myFixture.checkResult("""
            \data Bool

            \func foo : Nat -> Bool => {?}
            \func lol => (foo)
        """.trimIndent())
    }
}
