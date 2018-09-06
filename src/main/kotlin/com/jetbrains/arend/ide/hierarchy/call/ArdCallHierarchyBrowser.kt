package com.jetbrains.arend.ide.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SourceComparator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.arend.ide.hierarchy.ArdHierarchyNodeDescriptor
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable
import java.util.*
import javax.swing.JTree

class ArdCallHierarchyBrowser(project: Project, method: PsiElement) : CallHierarchyBrowserBase(project, method) {
    override fun getComparator(): Comparator<NodeDescriptor<Any>>? {
        return if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) {
            AlphaComparator.INSTANCE
        } else {
            SourceComparator.INSTANCE
        }
    }

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor): PsiElement? {
        if (descriptor is ArdHierarchyNodeDescriptor) {
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
            return ArdCalleeTreeStructure(myProject, psiElement)
        } else if (type == CALLER_TYPE) {
            return ArdCallerTreeStructure(myProject, psiElement)
        }
        return null
    }

    override fun isApplicableElement(element: PsiElement): Boolean {
        return element is PsiLocatedReferable
    }
}