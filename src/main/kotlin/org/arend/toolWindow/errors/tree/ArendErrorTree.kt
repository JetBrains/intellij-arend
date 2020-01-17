package org.arend.toolWindow.errors.tree

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.psi.PsiElement
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.DocStringBuilder
import org.arend.highlight.BasePass
import org.arend.naming.reference.Referable
import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition
import org.arend.psi.navigate
import org.arend.typechecking.error.ArendError
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.tree.*
import kotlin.math.max
import kotlin.math.min


class ArendErrorTree(treeModel: DefaultTreeModel, private val listener: ArendErrorTreeListener? = null) : Tree(treeModel) {
    init {
        isRootVisible = false
        emptyText.text = "No errors"
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when (e.mouseButton) {
                    MouseButton.Left -> if (e.clickCount >= 2) {
                        navigate(true)
                    }
                    MouseButton.Right -> {}
                    else -> {}
                }
            }
        })
    }

    fun navigate(focus: Boolean) =
        ((selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ArendError)?.let { BasePass.getImprovedCause(it.error) }?.navigate(focus)

    fun select(error: GeneralError) = selectNode(error)

    fun selectFirst() = selectNode(null)

    private fun selectNode(error: GeneralError?): Boolean {
        val root = model.root as? DefaultMutableTreeNode ?: return false
        var node: DefaultMutableTreeNode? = null
        for (any in root.depthFirstEnumeration()) {
            if (any is DefaultMutableTreeNode && (error != null && (any.userObject as? ArendError)?.error == error || error == null && any.userObject is ArendError)) {
                node = any
                break
            }
        }

        val path = node?.path?.let { TreePath(it) } ?: return false
        selectionModel.selectionPath = path
        scrollPathToVisibleVertical(path)
        return true
    }

    fun scrollPathToVisibleVertical(path: TreePath) {
        makeVisible(path)
        val bounds = getPathBounds(path) ?: return
        val parent = parent
        if (parent is JViewport) {
            bounds.width = min(bounds.width, max(parent.width - bounds.x - ((parent.parent as? JScrollPane)?.verticalScrollBar?.width ?: 0), 0))
        } else {
            bounds.x = 0
        }
        scrollRectToVisible(bounds)
        (accessibleContext as? AccessibleJTree)?.fireVisibleDataPropertyChange()
    }

    fun getExistingPrefix(path: TreePath?): TreePath? {
        var lastCorrect = path
        var currentPath = path
        while (currentPath != null) {
            val parent = currentPath.parentPath
            if (parent != null && (currentPath.lastPathComponent as? TreeNode)?.parent == null) {
                lastCorrect = parent
            }
            currentPath = parent
        }
        return lastCorrect
    }

    override fun convertValueToText(value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): String =
        when (val obj = ((value as? DefaultMutableTreeNode)?.userObject)) {
            is ArendFile -> obj.modulePath?.toString() ?: obj.name
            is ArendError -> DocStringBuilder.build(obj.error.getShortHeaderDoc(PrettyPrinterConfig.DEFAULT))
            is Referable -> if ((obj as? PsiElement)?.isValid == false) "" else obj.textRepresentation()
            else -> obj?.toString() ?: ""
        }

    fun containsNode(definition: TCDefinition): Boolean {
        val file = definition.containingFile as? ArendFile ?: return false
        val root = treeModel.root as? DefaultMutableTreeNode ?: return false
        for (child in root.children()) {
            if ((child as? DefaultMutableTreeNode)?.userObject == file) {
                for (defChild in child.children()) {
                    if ((defChild as? DefaultMutableTreeNode)?.userObject == definition) {
                        return true
                    }
                }
                return false
            }
        }
        return false
    }

    private fun <T : MutableTreeNode> insertNode(child: T, parent: T, comparator: Comparator<T>): T {
        val arendError = (child as? DefaultMutableTreeNode)?.userObject as? ArendError
        return if (arendError != null) {
            var i = parent.childCount - 1
            while (i >= 0) {
                val anotherError = (parent.getChildAt(i) as? DefaultMutableTreeNode)?.userObject as? ArendError
                if (anotherError == null || arendError.error.level <= anotherError.error.level) {
                    break
                }
                i--
            }
            (treeModel as DefaultTreeModel).insertNodeInto(child, parent, i + 1)
            listener?.errorAdded(arendError)
            child
        } else {
            val index = TreeUtil.indexedBinarySearch(parent, child, comparator)
            if (index < 0) {
                (treeModel as DefaultTreeModel).insertNodeInto(child, parent, -(index + 1))
                child
            } else {
                @Suppress("UNCHECKED_CAST")
                parent.getChildAt(index) as T
            }
        }
    }

    private fun notifyRemoval(node: TreeNode) {
        ((node as? DefaultMutableTreeNode)?.userObject as? ArendError)?.let {
            listener?.errorRemoved(it)
        }
        for (child in node.children()) {
            if (child is TreeNode) {
                notifyRemoval(child)
            }
        }
    }

    fun update(node: DefaultMutableTreeNode, childrenFunc: (DefaultMutableTreeNode) -> Set<Any?>) {
        val children = childrenFunc(node).let { it as? HashSet ?: LinkedHashSet(it) }

        var i = node.childCount - 1
        while (i >= 0) {
            val child = node.getChildAt(i)
            if (child is DefaultMutableTreeNode && children.remove(child.userObject)) {
                update(child, childrenFunc)
            } else {
                node.remove(i)
                notifyRemoval(child)
            }
            i--
        }

        for (child in children) {
            update(insertNode(DefaultMutableTreeNode(child), node, TreeNodeComparator), childrenFunc)
        }
    }

    private object TreeNodeComparator : Comparator<DefaultMutableTreeNode> {
        override fun compare(d1: DefaultMutableTreeNode, d2: DefaultMutableTreeNode): Int {
            val obj1 = d1.userObject
            val obj2 = d2.userObject
            return when {
                obj1 == obj2 -> 0
                obj1 is ArendFile && obj2 is ArendFile -> fix((obj1.modulePath?.toString() ?: obj1.name).compareTo(obj2.modulePath?.toString() ?: obj2.name))
                obj1 is TCDefinition && obj2 is TCDefinition -> fix(obj1.textOffset.compareTo(obj2.textOffset))
                obj1 is ArendError && obj2 is ArendError -> fix(obj1.error.level.compareTo(obj2.error.level) * -1)
                obj1 is ArendError -> 1
                obj2 is ArendError -> -1
                obj1 is ArendFile -> 1
                obj2 is ArendFile -> -1
                else -> -1
            }
        }

        private fun fix(cmp: Int) = if (cmp == 0) -1 else cmp
    }
}