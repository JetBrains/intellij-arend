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
import com.intellij.psi.PsiElement
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.editor.ArendOptions
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass
import java.util.*
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath
import kotlin.collections.HashMap

class ArendClassHierarchyBrowser(project: Project, method: PsiElement) : TypeHierarchyBrowserBase(project, method) {

    private var typeToTree: MutableMap<String, JTree>? = null

    private var pathsToExpand = ArrayList<Any>()

    private var isFirstChangeViewCall = true

    override fun getQualifiedName(psiElement: PsiElement?): String = (psiElement as? ArendDefClass)?.name ?: ""

    override fun isInterface(psiElement: PsiElement) = true

    override fun createLegendPanel(): JPanel? = null

    override fun canBeDeleted(psiElement: PsiElement?) = true

    override fun isApplicableElement(element: PsiElement) = element is ArendDefClass

    override fun getComparator(): Comparator<NodeDescriptor<Any>>? =
        if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) AlphaComparator.INSTANCE else SourceComparator.INSTANCE

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) = (descriptor as? ArendHierarchyNodeDescriptor)?.psiElement

    override fun changeView(typeName: String) {
        if (isFirstChangeViewCall) {
            super.changeView(ArendOptions.instance.hierarchyViewType)
            isFirstChangeViewCall = false
            return
        }
        ArendOptions.instance.hierarchyViewType = typeName
        super.changeView(typeName)
    }

    override fun createTrees(trees: MutableMap<String, JTree>) {
        val subTree = createTree(false)
        val superTree = createTree(false)

        trees[SUBTYPES_HIERARCHY_TYPE] = subTree
        trees[SUPERTYPES_HIERARCHY_TYPE] = superTree

        typeToTree = HashMap()

        typeToTree!![SUBTYPES_HIERARCHY_TYPE] = subTree
        typeToTree!![SUPERTYPES_HIERARCHY_TYPE] = superTree
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? =
        when (type) {
            SUBTYPES_HIERARCHY_TYPE -> ArendSubClassTreeStructure(myProject, psiElement, this)
            SUPERTYPES_HIERARCHY_TYPE -> ArendSuperClassTreeStructure(myProject, psiElement, this)
            else -> null
        }

    override fun prependActions(actionGroup: DefaultActionGroup) {
        super.prependActions(actionGroup)
        actionGroup.addAction(ArendShowImplFieldsAction())
        actionGroup.addAction(ArendShowNonImplFieldsAction())
    }

    fun getJTree(type: String): JTree? = typeToTree?.get(type)

    fun buildChildren(children: Array<ArendHierarchyNodeDescriptor>, treeType: String): Array<ArendHierarchyNodeDescriptor> {
        for (node in children) {
            node.update()
            if (ArendSuperClassTreeStructure.getChildren(node, myProject).isEmpty() || pathsToExpand.contains(ArendHierarchyNodeDescriptor.nodePath(node))) {
                val tree = getJTree(treeType)
                if (tree != null) {
                    getTreeModel(treeType).expand(node, tree) { }
                }
            }
        }
        return children
    }

    private fun storePaths(tree: JTree, root: DefaultMutableTreeNode, pathsToExpand: MutableList<Any>) {
        val childNodes = TreeUtil.listChildren(root)
        for (childNode1 in childNodes) {
            val childNode = childNode1 as DefaultMutableTreeNode
            val path = TreePath(childNode.path)
            val userObject = childNode.userObject
            if (tree.isExpanded(path)) {
                val newPath = ArendHierarchyNodeDescriptor.nodePath(userObject as ArendHierarchyNodeDescriptor)
                    pathsToExpand.add(newPath)
                    storePaths(tree, childNode, pathsToExpand)
            }
        }
    }

    override fun doRefresh(currentBuilderOnly: Boolean) {
        val currentViewType = currentViewType ?: return
        val tree = getJTree(currentViewType) ?: return
        val root = tree.model.root as DefaultMutableTreeNode

        pathsToExpand.clear()
        storePaths(tree, root, pathsToExpand)

        val element = hierarchyBase
        if (element == null || !isApplicableElement(element)) {
            return
        }

        validate()

        val structure = createHierarchyTreeStructure(currentViewType, element)
        val comparator = comparator
        val myModel = StructureTreeModel(structure!!, comparator ?: Comparator { _, _ -> 1}, myProject)
        tree.model = AsyncTreeModel(myModel, false, myProject)
    }

    inner class ArendShowImplFieldsAction : ToggleAction("Show implemented fields", "", ArendIcons.SHOW_FIELDS_IMPL) {

        override fun isSelected(e: AnActionEvent): Boolean {
            return ArendOptions.instance.showImplFields
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            ArendOptions.instance.showImplFields = state
            doRefresh(false)
        }
    }

    inner class ArendShowNonImplFieldsAction : ToggleAction("Show non-implemented fields", "", ArendIcons.SHOW_NON_IMPLEMENTED)  {

        override fun isSelected(e: AnActionEvent): Boolean {
            return ArendOptions.instance.showNonImplFields
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            ArendOptions.instance.showNonImplFields = state
            doRefresh(false)
        }
    }

}