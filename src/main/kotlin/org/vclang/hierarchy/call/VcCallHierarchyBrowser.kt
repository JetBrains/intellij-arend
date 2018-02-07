package org.vclang.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SourceComparator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.vclang.hierarchy.VcHierarchyNodeDescriptor
import org.vclang.psi.ext.PsiGlobalReferable
import java.util.*
import javax.swing.JTree

class VcCallHierarchyBrowser(project: Project, method: PsiElement) : CallHierarchyBrowserBase(project, method) {
    override fun getComparator(): Comparator<NodeDescriptor<Any>>? {
        return if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) {
            AlphaComparator.INSTANCE
        } else {
            SourceComparator.INSTANCE
        }
    }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
        if (descriptor is VcHierarchyNodeDescriptor) {
            return descriptor.psiElement
        }
        return null
    }

    override fun createTrees(trees: MutableMap<String, JTree>) {
        trees.put(CALLEE_TYPE, createTree(false))
        trees.put(CALLER_TYPE, createTree(false))
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? {
        if (type == CALLEE_TYPE) {
            return VcCalleeTreeStructure(myProject, psiElement)
        } else if (type == CALLER_TYPE) {
            return VcCallerTreeStructure(myProject, psiElement)
        }
        return null
    }

    override fun isApplicableElement(element: PsiElement): Boolean {
        return element is PsiGlobalReferable
    }
}