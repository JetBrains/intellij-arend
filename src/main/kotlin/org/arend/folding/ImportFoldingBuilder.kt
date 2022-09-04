package org.arend.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendGroup


class ImportFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun getPlaceholderText(node: ASTNode) = "..."

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        if (root !is ArendFile) {
            return emptyArray()
        }

        val descriptors = mutableListOf<FoldingDescriptor>()
        buildForGroup(root, descriptors)
        return descriptors.toTypedArray()
    }

    private fun buildForGroup(group: ArendGroup, descriptors: MutableList<FoldingDescriptor>) {
        val statements = group.statements
        var i = 0
        while (i + 1 < statements.size) {
            val statCmd = statements[i++].namespaceCommand
            val start = statCmd?.longName ?: continue
            val isImport = statCmd.importKw != null
            if (statements[i].namespaceCommand.let { it != null && (it.importKw != null) == isImport }) {
                i++
                while (i < statements.size && statements[i].namespaceCommand.let { it != null && (it.importKw != null) == isImport }) {
                    i++
                }

                val descriptor = FoldingDescriptor(group.node, TextRange(start.textRange.startOffset, statements[i - 1].textRange.endOffset), null, "...", isImport, emptySet())
                descriptor.setCanBeRemovedWhenCollapsed(true)
                descriptors.add(descriptor)
            }
        }

        for (statement in statements) {
            statement.group?.let {
                buildForGroup(it, descriptors)
            }
        }
    }

    override fun isCollapsedByDefault(node: ASTNode) = false
}