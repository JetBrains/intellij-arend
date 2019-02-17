package org.arend.module

import com.intellij.framework.library.LibraryVersionProperties
import com.intellij.ide.util.ChooseElementsDialog
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.roots.ui.configuration.FacetsProvider
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ArendIcons
import org.arend.findPsiFileByPath
import org.arend.module.config.ExternalLibraryConfig
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JComponent

private object ArendLibKind: PersistentLibraryKind<LibraryVersionProperties>("Arend") {
    override fun createDefaultProperties() = LibraryVersionProperties()
}

class ArendLibraryType: LibraryType<LibraryVersionProperties>(ArendLibKind) {

    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<LibraryVersionProperties>): LibraryPropertiesEditor? = null

    override fun getCreateActionName() = "Arend library"

    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? {
        val projectDependencies = LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.mapNotNullTo(HashSet()) { it.name }
        val libHome = ProjectRootManager.getInstance(project).projectSdk?.homePath?.let { Paths.get(it) } ?: return null
        val externalLibs =
            Files.newDirectoryStream(libHome) { Files.isDirectory(it) }.use { stream ->
                stream.mapNotNull { subDir -> if (Files.isRegularFile(subDir.resolve(FileUtils.LIBRARY_CONFIG_FILE))) subDir.fileName.toString() else null }
            }.filter { !projectDependencies.contains(it) }

        val libNameDialog = ChooseLibrariesDialog(project, externalLibs)
        libNameDialog.show()

        val libName = libNameDialog.chosenElements.firstOrNull() ?: return null
        val library = ExternalLibraryConfig(libName, project.findPsiFileByPath(libHome.resolve(Paths.get(libName, FileUtils.LIBRARY_CONFIG_FILE))) as? YAMLFile ?: return null)

        return object : NewLibraryConfiguration(libName, this, kind.createDefaultProperties()) {
            override fun addRoots(editor: LibraryEditor) {
                val srcDir = if (library.sourcesDir != null) library.sourcesPath else null
                if (srcDir != null) {
                    editor.addRoot(VfsUtil.pathToUrl(srcDir.toString()), OrderRootType.SOURCES)
                }
                val outDir = if (library.binariesDir != null) library.binariesPath else null
                if (outDir != null) {
                    editor.addRoot(VfsUtil.pathToUrl(outDir.toString()), OrderRootType.CLASSES)
                }
            }
        }
    }

    override fun getIcon(properties: LibraryVersionProperties?) = ArendIcons.LIBRARY_ICON

    override fun isSuitableModule(module: Module, facetsProvider: FacetsProvider) = ArendModuleType.has(module)

    class ChooseLibrariesDialog(project: Project, items: List<String>): ChooseElementsDialog<String>(project, items, "Libraries to add", null) {
        init {
            myChooser.setSingleSelectionMode()
        }

        override fun getItemIcon(item: String): Icon? = ArendIcons.LIBRARY_ICON

        override fun getItemText(item: String) = item
    }
}