package org.arend.projectView

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.*
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ext.module.ModulePath
import org.arend.module.AREND_LIB
import org.arend.module.ArendLibraryKind
import org.arend.module.ArendRawLibrary
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.psi.ArendFile
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.arend.util.configFile

class ArendProjectViewStructureProvider : TreeStructureProvider {
    override fun modify(parent: AbstractTreeNode<*>,
                        children: MutableCollection<AbstractTreeNode<*>>,
                        settings: ViewSettings?)
            : MutableCollection<AbstractTreeNode<*>> {
        if (parent !is NamedLibraryElementNode) {
            return children
        }
        val library = findArendLibrary(parent)
        if (library == null || library.config.additionalModulesSet.isEmpty()) {
            return children
        }
        return mutableListOf(ArendMetasNode(parent.project, library, settings), *children.toTypedArray())
    }

    private fun findArendLibrary(parent: NamedLibraryElementNode): ArendRawLibrary? {
        val ideaLibrary = (parent.value?.orderEntry as? LibraryOrderEntry)?.library
        if (ideaLibrary is LibraryEx && ideaLibrary.kind is ArendLibraryKind) {
            val configUrl = ideaLibrary.getUrls(ArendConfigOrderRootType.INSTANCE).singleOrNull() ?: return null
            return parent.project?.service<TypeCheckingService>()?.libraryManager
                    ?.getRegisteredLibrary { it is ArendRawLibrary && it.config.root?.configFile?.url == configUrl }
                    as? ArendRawLibrary
        }
        return null
    }
}

private class ArendMetasNode(project: Project?,
                             val library: ArendRawLibrary,
                             settings: ViewSettings?) : ProjectViewNode<String>(project, "ext", settings) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = "ext"
        presentation.setIcon(AllIcons.Modules.GeneratedFolder)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        val childConstructor = if (library.name == AREND_LIB) ::ArendStdLibMetaModuleNode else ::ArendMetaModuleNode
        return library.config.additionalModulesSet
                .mapNotNull {
                    library.config.findArendFile(it, withAdditional = true, withTests = false)
                            ?.let { file -> childConstructor(parent.project, it, file, settings) }
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
    override fun getVirtualFile(): VirtualFile? = library.config.extensionDirFile

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

private class ArendStdLibMetaModuleNode(project: Project?,
                                        modulePath: ModulePath,
                                        file: ArendFile,
                                        settings: ViewSettings?)
    : ArendMetaModuleNode(project, modulePath, file, settings) {
    // "Meta.ard" is always on top of the metas list
    override fun getSortKey(): String =
            if (modulePath.toString() == "Meta") "" else modulePath.toString()

    override fun getTypeSortKey(): Comparable<ExtensionSortKey?>? =
            if (modulePath.toString() == "Meta") ExtensionSortKey("") else super.getTypeSortKey()
}