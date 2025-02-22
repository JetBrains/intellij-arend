package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyBrowserManager
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.SourceComparator
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.StructureTreeModel
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.fullNameText
import org.arend.settings.ArendProjectSettings
import java.util.*
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ArendClassHierarchyBrowser(project: Project, method: PsiElement) : TypeHierarchyBrowserBase(project, method) {

    private var typeToTree: MutableMap<String, JTree>? = null

    private var pathsToExpand = ArrayList<Any>()

    private var isFirstChangeViewCall = true

    override fun getQualifiedName(psiElement: PsiElement?): String = (psiElement as? ArendDefClass)?.name ?: ""

    override fun isInterface(psiElement: PsiElement) = true

    override fun createLegendPanel(): JPanel? = null

    override fun canBeDeleted(psiElement: PsiElement?) = true

    override fun isApplicableElement(element: PsiElement) = element is ArendDefClass

    override fun getComparator(): Comparator<NodeDescriptor<*>>? =
        if (HierarchyBrowserManager.getInstance(myProject).state!!.SORT_ALPHABETICALLY) AlphaComparator.getInstance() else SourceComparator.INSTANCE

    override fun getElementFromDescriptor(descriptor: HierarchyNodeDescriptor) = (descriptor as? ArendHierarchyNodeDescriptor)?.psiElement

    override fun changeView(typeName: String) {
        if (isFirstChangeViewCall) {
            super.changeView(myProject.service<ArendProjectSettings>().data.hierarchyViewType)
            isFirstChangeViewCall = false
        } else {
            myProject.service<ArendProjectSettings>().data.hierarchyViewType = typeName
            super.changeView(typeName)
        }
    }

    override fun createTrees(trees: MutableMap<in String, in JTree>) {
        val subTree = createTree(false)
        val superTree = createTree(false)

        trees[getSubtypesHierarchyType()] = subTree
        trees[getSupertypesHierarchyType()] = superTree

        typeToTree = hashMapOf(
                getSubtypesHierarchyType() to subTree,
                getSupertypesHierarchyType() to superTree
        )
    }

    override fun createHierarchyTreeStructure(type: String, psiElement: PsiElement): HierarchyTreeStructure? =
        when (type) {
            getSubtypesHierarchyType() -> ArendSubClassTreeStructure(myProject, psiElement, this)
            getSupertypesHierarchyType() -> ArendSuperClassTreeStructure(myProject, psiElement, this)
            else -> null
        }

    override fun prependActions(actionGroup: DefaultActionGroup) {
        super.prependActions(actionGroup)
        actionGroup.addAction(ArendShowImplFieldsAction())
        actionGroup.addAction(ArendShowNonImplFieldsAction())
        actionGroup.addAction(ArendHierarchyGraphAction())
    }

    fun getJTree(type: String): JTree? = typeToTree?.get(type)

    fun buildChildren(children: Array<ArendHierarchyNodeDescriptor>, treeType: String): Array<ArendHierarchyNodeDescriptor> {
        for (node in children) {
            node.update()
            if (ArendSuperClassTreeStructure.getChildren(node, myProject).isEmpty() || pathsToExpand.contains(ArendHierarchyNodeDescriptor.nodePath(node))) {
                ApplicationManager.getApplication().invokeLater {
                    runWriteAction {
                        val tree = getJTree(treeType)
                        if (tree != null) {
                            getTreeModel(treeType).expand(node, tree) { }
                        }
                    }
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

    inner class ArendShowImplFieldsAction : ToggleAction("Show Implemented Fields", "", ArendIcons.SHOW_FIELDS_IMPL) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun isSelected(e: AnActionEvent) = myProject.service<ArendProjectSettings>().data.showImplFields

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            myProject.service<ArendProjectSettings>().data.showImplFields = state
            doRefresh(false)
        }
    }

    inner class ArendShowNonImplFieldsAction : ToggleAction("Show Non-Implemented Fields", "", ArendIcons.SHOW_NON_IMPLEMENTED)  {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun isSelected(e: AnActionEvent) = myProject.service<ArendProjectSettings>().data.showNonImplFields

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            myProject.service<ArendProjectSettings>().data.showNonImplFields = state
            doRefresh(false)
        }
    }

    inner class ArendHierarchyGraphAction : AnAction("Visualize as Orthogonal Diagram", "A hierarchical class graph", ArendIcons.ORTHOGONAL_GRAPH) {
        private val usedNodes = mutableSetOf<ArendDefClass>()
        private val edges = mutableSetOf<GraphEdge>()

        private fun findEdges(currentNode: ArendDefClass, isSuperTypes: Boolean) {
            usedNodes.add(currentNode)

            val from = currentNode.fullNameText

            /* TODO[server2]
            val children = if (isSuperTypes) {
                currentNode.superClassReferences
            } else {
                myProject.service<ClassDescendantsSearch>().search(currentNode)
            }.mapNotNull { it as? ArendDefClass? }
            for (child in children) {
                val to = child.refLongName.toString()
                if (isSuperTypes) {
                    edges.add(GraphEdge(to, from))
                } else {
                    edges.add(GraphEdge(from, to))
                }

                if (!usedNodes.contains(child)) {
                    findEdges(child, isSuperTypes)
                }
            }
            */
        }

        override fun actionPerformed(e: AnActionEvent) {
            usedNodes.clear()
            edges.clear()

            val tree = getJTree(currentViewType) ?: return
            val root = ((tree.model.root as DefaultMutableTreeNode).userObject as ArendHierarchyNodeDescriptor).psiElement as ArendDefClass

            findEdges(root, myProject.service<ArendProjectSettings>().data.hierarchyViewType == getSupertypesHierarchyType())

            myProject.service<GraphSimulator>().displayOrthogonal(
                this.toString(),
                if (currentViewType == getSubtypesHierarchyType()) {
                    "Subtypes_${root.fullNameText}"
                } else {
                    "Supertypes_${root.fullNameText}"
                },
                edges,
                usedNodes.map { GraphNode(it.fullNameText) }.toSet()
            )
        }
    }
}
