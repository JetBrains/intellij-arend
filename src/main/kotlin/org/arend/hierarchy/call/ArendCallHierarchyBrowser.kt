package org.arend.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SourceComparator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ext.PsiLocatedReferable
import java.util.*
import javax.swing.JTree

class ArendCallHierarchyBrowser(project: Project, method: PsiElement) : CallHierarchyBrowserBase(project, method) {
    override fun getComparator(): Comparator<NodeDescriptor<Any>>? {
        return if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) {
            AlphaComparator.INSTANCE
        } else {
            SourceComparator.INSTANCE
        }
    }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
        if (descriptor is ArendHierarchyNodeDescriptor) {
            return descriptor.psiElement
        }
        return null
    }

    override fun createTrees(trees: MutableMap<String, JTree>) {
        trees[CALLEE_TYPE] = createTree(false)
        trees[CALLER_TYPE] = createTree(false)
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? {
        if (type == CALLEE_TYPE) {
            return ArendCalleeTreeStructure(myProject, psiElement)
        } else if (type == CALLER_TYPE) {
            return ArendCallerTreeStructure(myProject, psiElement)
        }
        return null
    }

    override fun isApplicableElement(element: PsiElement): Boolean {
        return element is PsiLocatedReferable
    }
}