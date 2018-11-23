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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.layout.panel
import org.arend.module.util.*
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JTextField

class ArendLibraryType: LibraryType<LibraryVersionProperties>(AREND_LIB_KIND) {

    companion object {
        val AREND_LIB_KIND = object: PersistentLibraryKind<LibraryVersionProperties>("Arend") {
            override fun createDefaultProperties(): LibraryVersionProperties {
                return LibraryVersionProperties()
            }

        }
    }

    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<LibraryVersionProperties>): LibraryPropertiesEditor? {
        return null
    }

    override fun getCreateActionName(): String? {
        return "Arend library"
    }

    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? {
        /*
        val descriptor = DefaultLibraryRootsComponentDescriptor()
        val chooserDescriptor = FileChooserDescriptor(true, false, false, false, false, false)

        chooserDescriptor.title = "Select Arend Library File"
        chooserDescriptor.withFileFilter(object: Condition<VirtualFile> {
            override fun value(t: VirtualFile?): Boolean {
                return t?.name == "arend.yaml"
            }
        }) */
        val libHome = ProjectRootManager.getInstance(project).projectSdk?.homePath ?: return null
        val libNameDialog = ChooseLibrariesDialog(project, findAllLibrariesInDirectory(Paths.get(libHome)).map { it.fileName.toString() })

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

    override fun getIcon(): Icon? {
        return null
    }

    /*
    class ArendLibRootsDetector: LibraryRootsDetector() {
        override fun getRootTypeName(rootType: LibraryRootType): String? {
            return ""
        }

        override fun detectRoots(rootCandidate: VirtualFile, progressIndicator: ProgressIndicator): MutableCollection<DetectedLibraryRoot> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }*/

    class SetLibraryNameDialog(project: Project): DialogWrapper(project) {
        private val nameTextField = JTextField()

        val libName
            get() = nameTextField.text

        init {
            title = "Add external Arend library"
            init()
            //val fm = libNameDialog.contentPane?.getFontMetrics(libNameDialog.contentPane?.font)
            //fm?.let { libNameDialog.setSize(4000, 4000) } //setSize(it.stringWidth(title), size.height) }
        }

        override fun createCenterPanel(): JComponent? {
            return panel {
                row("Library name:") {  }
                row { nameTextField() }
            }
        }

    }

    class ChooseLibrariesDialog(project: Project, items: List<String>): ChooseElementsDialog<String>(project, items, "Libraries to add", null) {
        init {
            myChooser.setSingleSelectionMode()
        }

        override fun getItemIcon(item: String): Icon? {
            return null
        }

        override fun getItemText(item: String): String {
            return item
        }

    }
}