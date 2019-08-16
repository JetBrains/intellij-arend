package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SourceComparator
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiElement
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass
import java.util.*
import javax.swing.JPanel
import javax.swing.JTree


class ArendClassHierarchyBrowser(project: Project, method: PsiElement) : TypeHierarchyBrowserBase(project, method) {
    var showImplFields: Boolean = true
        private set

    var showNonimplFields: Boolean = true
        private set

    private var superTree: JTree? = null

    override fun getQualifiedName(psiElement: PsiElement?): String = (psiElement as? ArendDefClass)?.name ?: ""

    override fun isInterface(psiElement: PsiElement) = true

    override fun createLegendPanel(): JPanel? = null

    override fun canBeDeleted(psiElement: PsiElement?) = true

    override fun isApplicableElement(element: PsiElement) = element is ArendDefClass

    override fun getComparator(): Comparator<NodeDescriptor<Any>>? =
        if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) AlphaComparator.INSTANCE else SourceComparator.INSTANCE

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) = (descriptor as? ArendHierarchyNodeDescriptor)?.psiElement

    override fun createTrees(trees: MutableMap<String, JTree>) {
        trees[SUBTYPES_HIERARCHY_TYPE] = createTree(false)
        trees[SUPERTYPES_HIERARCHY_TYPE] = createTree(false)
        superTree = trees[SUPERTYPES_HIERARCHY_TYPE]
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? =
        when (type) {
            SUBTYPES_HIERARCHY_TYPE -> ArendSubClassTreeStructure(myProject, psiElement)
            SUPERTYPES_HIERARCHY_TYPE -> ArendSuperClassTreeStructure(myProject, psiElement, this)
            else -> null
        }

    override fun prependActions(actionGroup: DefaultActionGroup) {
        super.prependActions(actionGroup)
        actionGroup.addAction(ArendShowImplFieldsAction())
        actionGroup.addAction(ArendShowNonimplFieldsAction())
    }

    fun getSuperJTree(): JTree? = superTree

    inner class ArendShowImplFieldsAction : ToggleAction("Show implemented fields", "",
            IconLoader.getIcon("icons/showFieldImpl.png")) {

        override fun isSelected(e: AnActionEvent): Boolean {
            return showImplFields
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            showImplFields = state
            doRefresh(false)
        }
    }

    inner class ArendShowNonimplFieldsAction : ToggleAction("Show non-implemented fields", "", IconLoader.getIcon("icons/showNonImpl.png"))  {

        override fun isSelected(e: AnActionEvent): Boolean {
            return showNonimplFields
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            showNonimplFields = state
            doRefresh(false)
        }
    }

}