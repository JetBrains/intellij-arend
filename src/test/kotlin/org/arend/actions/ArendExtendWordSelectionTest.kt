package org.arend.actions

import com.intellij.openapi.actionSystem.IdeActions
import org.arend.ArendTestBase

class ArendExtendWordSelectionTest: ArendTestBase() {
    fun doTest(before: String, finalRange: String, count: Int = 1) {
        InlineFile(before).withCaret()
        for (i in 1..count) myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
        val selectionModel = myFixture.editor.selectionModel
        val t = myFixture.file.text.substring(selectionModel.selectionStart, selectionModel.selectionEnd)
        assert (t == finalRange)
    }

    fun testSmartSelection() = doTest("""
       \data List {X : \Type}
         | nil
         | \infixr 1 :: X (List {X})
         | \infixl 2 && (List {X}) X

       \func length {X : \Type} (x : List {X}) : List {X}
         | y :: x :: x{-caret-}2 && x1 => y :: (x :: x2) && x1 
    """, "x :: x2 && x1", 3)
}