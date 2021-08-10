package org.arend.intention.binOp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.term.concrete.Concrete

class CaretHelper(private val initialBinOp: PsiElement,
                  binOpSeqRange: TextRange,
                  document: Document) {
    private val canUseCaretMarker: Boolean = !document.getText(binOpSeqRange).contains(CARET_MARKER)

    fun shouldAddCaretMarker(binOp: Concrete.Expression) = canUseCaretMarker && binOp.data == initialBinOp

    fun removeCaretMarker(binOpSeqText: String): Pair<String, Int> {
        if (canUseCaretMarker) {
            val markerStart = binOpSeqText.indexOf(CARET_MARKER)
            if (markerStart >= 0) {
                return binOpSeqText.removeRange(markerStart, markerStart + CARET_MARKER.length) to markerStart
            }
        }
        return binOpSeqText to -1
    }

    companion object {
        const val CARET_MARKER = "<<<<caret-pos>>>>"
    }
}