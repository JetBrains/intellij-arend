package org.arend.module

import com.intellij.framework.library.LibraryVersionProperties
import com.intellij.ide.util.ChooseElementsDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ArendIcons
import org.arend.module.util.*
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
        val libHome = ProjectRootManager.getInstance(project).projectSdk?.homePath ?: return null
        val libNameDialog = ChooseLibrariesDialog(project,
                findExternalLibrariesInDirectory(Paths.get(libHome)).map { it.fileName.toString() }.filter { getProjectDependencies(project).find { n -> it == n.name } == null })

        libNameDialog.show()

        val libName = libNameDialog.chosenElements.firstOrNull() ?: return null
        val libHeader = findLibHeaderInDirectory(Paths.get(libHome), libName)?.let { libHeaderByPath(it, project) } ?: return null

        return object : NewLibraryConfiguration(libName, this, kind.createDefaultProperties()) {
            override fun addRoots(editor: LibraryEditor) {
                val srcDir = libHeader.sourcesDirPath
                if (srcDir != null) editor.addRoot(srcDir.toString(), OrderRootType.SOURCES)
                val outDir = libHeader.outputPath
                if (outDir != null) editor.addRoot(outDir.toString(), OrderRootType.CLASSES)
            }
        }
    }

    override fun getIcon(properties: LibraryVersionProperties?) = ArendIcons.LIBRARY_ICON

    class ChooseLibrariesDialog(project: Project, items: List<String>): ChooseElementsDialog<String>(project, items, "Libraries to add", null) {
        init {
            myChooser.setSingleSelectionMode()
        }

        override fun getItemIcon(item: String): Icon? = ArendIcons.LIBRARY_ICON

        override fun getItemText(item: String) = item
    }
}