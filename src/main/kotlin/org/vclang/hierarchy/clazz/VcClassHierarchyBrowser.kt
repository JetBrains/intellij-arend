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
    override fun getQualifiedName(psiElement: PsiElement?): String = (psiElement as? VcDefClass)?.name ?: ""

    override fun isInterface(psiElement: PsiElement) = true

    override fun createLegendPanel(): JPanel? = null

    override fun canBeDeleted(psiElement: PsiElement?) = true

    override fun isApplicableElement(element: PsiElement) = element is VcDefClass

    override fun getComparator(): Comparator<NodeDescriptor<Any>>? =
        if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) AlphaComparator.INSTANCE else SourceComparator.INSTANCE

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) = (descriptor as? VcHierarchyNodeDescriptor)?.psiElement

    override fun createTrees(trees: MutableMap<String, JTree>) {
        trees[SUBTYPES_HIERARCHY_TYPE] = createTree(false)
        trees[SUPERTYPES_HIERARCHY_TYPE] = createTree(false)
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? =
        when (type) {
            SUBTYPES_HIERARCHY_TYPE -> VcSubClassTreeStructure(myProject, psiElement)
            SUPERTYPES_HIERARCHY_TYPE -> VcSuperClassTreeStructure(myProject, psiElement)
            else -> null
        }
}