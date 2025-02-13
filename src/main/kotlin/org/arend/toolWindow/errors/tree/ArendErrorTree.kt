package org.arend.toolWindow.errors.tree

import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.DocStringBuilder
import org.arend.ext.reference.ArendRef
import org.arend.highlight.BasePass
import org.arend.module.ModuleLocation
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.navigate
import org.arend.util.ArendBundle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.tree.*
import kotlin.math.max
import kotlin.math.min


class ArendErrorTree(treeModel: DefaultTreeModel) : Tree(treeModel) {
    init {
        isRootVisible = false
        emptyText.text = ArendBundle.message("arend.messages.view.no.messages")
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
        ((selectionPath?.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ArendErrorTreeElement)?.let { BasePass.getImprovedCause(it.sampleError) }?.navigate(focus)

    fun select(error: GeneralError) = selectNode(error)

    fun selectFirst() = selectNode(null)

    private fun selectNode(error: GeneralError?): Boolean {
        val root = model.root as? DefaultMutableTreeNode ?: return false
        var node: DefaultMutableTreeNode? = null
        for (any in root.depthFirstEnumeration()) {
            if (any is DefaultMutableTreeNode && (error != null && (any.userObject as? ArendErrorTreeElement)?.errors?.any { it == error } == true || error == null && any.userObject is ArendErrorTreeElement)) {
                node = any
                break
            }
        }

        val path = node?.path?.let { TreePath(it) } ?: return false
        selectionModel.selectionPath = path
        scrollPathToVisibleVertical(path)
        return true
    }

    private fun scrollPathToVisibleVertical(path: TreePath) {
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

    override fun convertValueToText(value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): String {
        var result: String? = null
        ApplicationManager.getApplication().executeOnPooledThread {
            result = runReadAction {
                when (val obj = ((value as? DefaultMutableTreeNode)?.userObject)) {
                    is ModuleLocation -> obj.toString()
                    is ArendErrorTreeElement -> {
                        val messages = LinkedHashSet<String>()
                        for (error in obj.errors) {
                            messages.add(DocStringBuilder.build(error.getShortHeaderDoc(PrettyPrinterConfig.DEFAULT)))
                        }
                        messages.joinToString("; ")
                    }
                    is Referable -> if ((obj as? PsiElement)?.isValid == false) "" else obj.textRepresentation()
                    else -> obj?.toString() ?: ""
                }
            }
        }.get()
        return result!!
    }

    fun containsNode(definition: PsiLocatedReferable): Boolean {
        val file = (if (definition.isValid) definition.containingFile as? ArendFile else null) ?: return false
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

    private fun insertNode(child: DefaultMutableTreeNode, parent: DefaultMutableTreeNode): DefaultMutableTreeNode {
        val treeElement = child.userObject as? ArendErrorTreeElement
        return if (treeElement != null) {
            var i = parent.childCount - 1
            while (i >= 0) {
                val anotherError = (parent.getChildAt(i) as? DefaultMutableTreeNode)?.userObject as? ArendErrorTreeElement
                if (anotherError == null || treeElement.highestError.level <= anotherError.highestError.level) {
                    break
                }
                i--
            }
            (treeModel as DefaultTreeModel).insertNodeInto(child, parent, i + 1)
            child
        } else {
            val index = TreeUtil.indexedBinarySearch(parent, child, TreeNodeComparator)
            if (index < 0) {
                (treeModel as DefaultTreeModel).insertNodeInto(child, parent, -(index + 1))
                child
            } else {
                parent.getChildAt(index) as DefaultMutableTreeNode
            }
        }
    }

    fun update(node: DefaultMutableTreeNode, childrenFunc: (DefaultMutableTreeNode) -> Collection<Any?>) {
        val children = childrenFunc(node).let { it as? HashSet ?: LinkedHashSet(it) }

        var i = node.childCount - 1
        while (i >= 0) {
            val child = node.getChildAt(i)
            if (child is DefaultMutableTreeNode && children.remove(child.userObject)) {
                update(child, childrenFunc)
            } else {
                node.remove(i)
            }
            i--
        }

        for (child in children) {
            update(insertNode(DefaultMutableTreeNode(child), node), childrenFunc)
        }
    }

    private object TreeNodeComparator : Comparator<DefaultMutableTreeNode> {
        override fun compare(d1: DefaultMutableTreeNode, d2: DefaultMutableTreeNode): Int {
            val obj1 = d1.userObject
            val obj2 = d2.userObject
            return when {
                obj1 == obj2 -> 0
                obj1 is ModuleLocation && obj2 is ModuleLocation -> fix(obj1.toString().compareTo(obj2.toString()))
                obj1 is ArendErrorTreeElement && obj2 is ArendErrorTreeElement -> fix(obj1.highestError.level.compareTo(obj2.highestError.level) * -1)
                obj1 is ArendErrorTreeElement -> 1
                obj2 is ArendErrorTreeElement -> -1
                obj1 is ModuleLocation -> -1
                obj2 is ModuleLocation -> 1
                obj1 is LocatedReferable && obj2 is LocatedReferable -> fix(obj1.refLongName.toString().compareTo(obj2.refLongName.toString()))
                obj1 is LocatedReferable -> -1
                obj2 is LocatedReferable -> 1
                obj1 is ArendRef && obj2 is ArendRef -> fix(obj1.refName.compareTo(obj2.refName))
                else -> -1
            }
        }

        private fun fix(cmp: Int) = if (cmp == 0) -1 else cmp
    }
}