package org.arend.actions

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendPattern
import org.arend.term.concrete.Concrete
import org.arend.util.appExprToConcrete
import org.arend.util.getBounds
import org.arend.util.patternToConcrete

class ArendExtendWordSelectionHandler : ExtendWordSelectionHandler {
    override fun canSelect(e: PsiElement): Boolean = e is ArendArgumentAppExpr || e is ArendPattern

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
        val rangeSet = HashSet<TextRange>()
        val rangesMap = HashMap<Concrete.SourceNode, TextRange>()
        when (e) {
            is ArendArgumentAppExpr -> appExprToConcrete(e)
            is ArendPattern -> patternToConcrete(e)
            else -> null
        }?.let {
            getBounds(it, e.node.getChildren(null).toList(), rangesMap)
        }
        rangeSet.addAll(rangesMap.values.filter { it.contains(cursorOffset) })
        return rangeSet.sortedBy { it.endOffset - it.startOffset }.toList()
    }
}