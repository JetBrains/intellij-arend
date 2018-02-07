package org.vclang.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SourceComparator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.vclang.hierarchy.VcHierarchyNodeDescriptor
import org.vclang.psi.VcDefClass
import java.util.*
import javax.swing.JPanel
import javax.swing.JTree



class VcClassHierarchyBrowser(project: Project, method: PsiElement) : TypeHierarchyBrowserBase(project, method) {
    override fun getQualifiedName(psiElement: PsiElement?): String {
        if (psiElement is VcDefClass) {
            return psiElement.name ?: ""
        }
        return ""
    }

    override fun isInterface(psiElement: PsiElement?): Boolean {
        return true
    }

    override fun createLegendPanel(): JPanel? {
        return null
    }

    override fun canBeDeleted(psiElement: PsiElement?): Boolean {
        return true
    }

    override fun isApplicableElement(element: PsiElement): Boolean {
        return element is VcDefClass
    }

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
        trees.put(SUBTYPES_HIERARCHY_TYPE, createTree(false))
        trees.put(SUPERTYPES_HIERARCHY_TYPE, createTree(false))
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? {
        if (type == SUBTYPES_HIERARCHY_TYPE) {
            return VcSubClassTreeStructure(myProject, psiElement)
        } else if (type == SUPERTYPES_HIERARCHY_TYPE) {
            return VcSuperClassTreeStructure(myProject, psiElement)
        }
        return null
    }
}