package org.arend.projectView

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.*
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ext.module.ModulePath
import org.arend.module.ModuleLocation
import org.arend.module.config.LibraryConfig
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.util.FileUtils
import org.arend.util.findLibrary

class ArendProjectViewStructureProvider : TreeStructureProvider {
    override fun modify(parent: AbstractTreeNode<*>,
                        children: MutableCollection<AbstractTreeNode<*>>,
                        settings: ViewSettings?)
            : MutableCollection<AbstractTreeNode<*>> {
        if (parent !is NamedLibraryElementNode) {
            return children
        }
        val name = (parent.value?.orderEntry as? LibraryOrderEntry)?.library?.name ?: return children
        val config = parent.project?.findLibrary(name) ?: return children
        val server = parent.project?.service<ArendServerService>()?.server ?: return children
        val modules = server.modules.filter { it.locationKind == ModuleLocation.LocationKind.GENERATED && it.libraryName == name }
        if (modules.isEmpty()) return children
        return mutableListOf(ArendMetasNode(parent.project, config, modules, settings), *children.toTypedArray())
    }
}

private class ArendMetasNode(project: Project?,
                             val config: LibraryConfig,
                             val modules: List<ModuleLocation>,
                             settings: ViewSettings?) : ProjectViewNode<String>(project, "ext", settings) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = "ext"
        presentation.setIcon(AllIcons.Modules.GeneratedFolder)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return modules
                .mapNotNull {
                    config.findArendFile(it.modulePath, it.locationKind == ModuleLocation.LocationKind.GENERATED, it.locationKind == ModuleLocation.LocationKind.TEST)
                            ?.let { file -> ArendMetaModuleNode(parent.project, it.modulePath, file, settings) }
                }.toMutableList()
    }

    /**
     * @see com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.getSortOrder
     */
    @Suppress("UnstableApiUsage")
    override fun getSortOrder(settings: NodeSortSettings): NodeSortOrder =
            if (settings.isFoldersAlwaysOnTop) NodeSortOrder.FOLDER
            else super.getSortOrder(settings)

    /**
     * @see com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.getTypeSortWeight
     */
    override fun getTypeSortWeight(sortByType: Boolean): Int = 3

    /**
     * @see com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.getWeight
     */
    override fun getWeight(): Int = if (settings.isFoldersAlwaysOnTop) 20 else super.getWeight()

    /**
     * Used to calculate background color of the node.
     * @see com.intellij.ide.projectView.impl.ProjectViewTree.getFileColorFor
     */
    override fun getVirtualFile(): VirtualFile? = config.extensionDirFile

    override fun contains(file: VirtualFile): Boolean = false
}

private open class ArendMetaModuleNode(project: Project?,
                                       protected val modulePath: ModulePath,
                                       file: ArendFile,
                                       settings: ViewSettings?)
    : PsiFileNode(project, file, settings) {
    override fun updateImpl(data: PresentationData) {
        data.presentableText = modulePath.toString() + FileUtils.EXTENSION
    }
}