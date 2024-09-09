package org.arend.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
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
    override fun getComparator(): Comparator<NodeDescriptor<*>>? =
        if (HierarchyBrowserManager.getInstance(myProject).state?.SORT_ALPHABETICALLY == true)
            AlphaComparator.getInstance()
        else
            SourceComparator.INSTANCE

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) =
        (descriptor as? ArendHierarchyNodeDescriptor)?.psiElement

    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        trees[getCalleeType()] = createTree(false)
        trees[getCallerType()] = createTree(false)
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement) =
        when (type) {
            getCalleeType() -> ArendCalleeTreeStructure(myProject, psiElement)
            getCallerType() -> ArendCallerTreeStructure(myProject, psiElement)
            else -> null
        }

    override fun isApplicableElement(element: PsiElement) = element is PsiLocatedReferable
}