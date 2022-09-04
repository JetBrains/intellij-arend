package org.arend.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ide.SmartSelectProvider
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.psi.ext.ArendExpr
import org.arend.psi.ArendFile
import org.arend.resolving.util.parseBinOp
import org.arend.term.concrete.Concrete

class ArendSmartSelectProvider : SmartSelectProvider<ArendSmartSelectProvider.Context> {

    data class Context(val file: ArendFile, val editor: Editor, val selectionRange: TextRange)

    override fun canIncreaseSelection(source: Context) = true

    override fun canDecreaseSelection(source: Context?) = true

    override fun increaseSelection(source: Context) {
        val (file, editor, selectionRange) = source
        val elementAtSelection = file.findElementAt(selectionRange.startOffset) ?: return
        val defaultParent = elementAtSelection
                .parents(true)
                .firstOrNull { it.textRange.strictlyContains(selectionRange) }
                ?: return
        if (defaultParent is ArendExpr) {
            val refinedParent = findClosestNodeInBinOp(defaultParent) { it.strictlyContains(selectionRange) }
            editor.selectionModel.setSelection(refinedParent.startOffset, refinedParent.endOffset)
        } else {
            editor.selectionModel.setSelection(defaultParent.startOffset, defaultParent.endOffset)
        }
    }

    override fun decreaseSelection(source: Context) {
        val (file, editor, selectionRange) = source
        if (selectionRange.startOffset == selectionRange.endOffset) {
            return
        }
        val caretOffset = editor.caretModel.offset
        val elementAtCaret = file.findElementAt(caretOffset)
        if (elementAtCaret == null) {
            editor.selectionModel.setSelection(caretOffset, caretOffset)
            return
        }
        val selectionOwner = elementAtCaret.parents(true).first { it.textRange.contains(selectionRange) }

        if (selectionOwner is ArendExpr) {
            val candidateRanges = mutableListOf<TextRange>()
            findClosestNodeInBinOp(selectionOwner) {
                if (selectionRange.strictlyContains(it) && it.contains(caretOffset)) candidateRanges.add(it)
                false
            }
            val coarsestChild = candidateRanges.lastOrNull()
            if (coarsestChild != null) {
                editor.selectionModel.setSelection(coarsestChild.startOffset, coarsestChild.endOffset)
                return
            }
        }
        val coarsestChildNode = elementAtCaret.parents(true).lastOrNull { selectionRange.strictlyContains(it.textRange) }
        if (coarsestChildNode == null) {
            editor.selectionModel.setSelection(caretOffset, caretOffset)
        } else {
            editor.selectionModel.setSelection(coarsestChildNode.startOffset, coarsestChildNode.endOffset)
        }
    }

    override fun getSource(context: DataContext?): Context? {
        val project = context?.getData("project") as? Project ?: return null
        if (DumbService.isDumb(project)) {
            return null
        }
        val file = context.getData("psi.File") as? ArendFile ?: return null
        val editor = context.getData("editor") as? Editor ?: return null
        // the platform contains some logic on expanding selection having an empty one,
        // so we'll handle what goes after
        val selection = EditorUtil.getSelectionInAnyMode(editor).takeIf { !it.isEmpty } ?: return null
        return Context(file, editor, selection)
    }

    private fun findClosestNodeInBinOp(defaultParent: ArendExpr, predicate: (TextRange) -> Boolean): TextRange {
        val binOp = parseBinOp(defaultParent) ?: return defaultParent.textRange
        return visitParsedBinOp(binOp, predicate) ?: defaultParent.textRange
    }

    private fun visitParsedBinOp(concrete: Concrete.Expression, predicate: (TextRange) -> Boolean): TextRange? {
        var result: TextRange? = null

        fun doVisit(concrete: Concrete.Expression): TextRange? {
            when (concrete) {
                is Concrete.AppExpression -> {
                    val childRanges = mutableListOf<TextRange>()
                    for (arg in concrete.arguments) {
                        val argRange = doVisit(arg.expression) ?: return null
                        if (predicate(argRange)) {
                            result = argRange
                            return null
                        }
                        childRanges.add(argRange)
                    }
                    childRanges.add((concrete.function.data as PsiElement).textRange)
                    return TextRange(childRanges.minOf { it.startOffset }, childRanges.maxOf { it.endOffset })
                }
                else -> return (concrete.data as? PsiElement)?.textRange
            }
        }

        doVisit(concrete)
        return result
    }

    private fun TextRange.strictlyContains(other: TextRange): Boolean = this != other && this.contains(other)
}