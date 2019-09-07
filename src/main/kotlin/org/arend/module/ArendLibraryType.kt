package org.arend.module

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.DummyLibraryProperties
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.roots.ui.configuration.FacetsProvider
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.ArendIcons
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.module.orderRoot.ArendLibraryRootsComponentDescriptor
import org.arend.util.FileTypeChooserDescriptor
import org.jetbrains.yaml.psi.YAMLFile
import javax.swing.JComponent

object ArendLibraryKind : PersistentLibraryKind<DummyLibraryProperties>("Arend") {
    override fun createDefaultProperties(): DummyLibraryProperties = DummyLibraryProperties.INSTANCE

    override fun getAdditionalRootTypes() = arrayOf(ArendConfigOrderRootType)
}

class ArendLibraryType : LibraryType<DummyLibraryProperties>(ArendLibraryKind) {
    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<DummyLibraryProperties>): LibraryPropertiesEditor? = null

    override fun getCreateActionName() = "Arend library"

    override fun createLibraryRootsComponentDescriptor() = ArendLibraryRootsComponentDescriptor

    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? {
        val file = FileChooser.chooseFile(FileTypeChooserDescriptor(listOf(".yaml")).apply {
            title = "Choose config file"
            description = "Select the yaml config file of an Arend library"
        }, project, null) ?: return null

        val libName = file.parent?.name ?: return null
        val yaml = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: return null
        val library = ExternalLibraryConfig(libName, yaml)

        return object : NewLibraryConfiguration(libName, this, kind.createDefaultProperties()) {
            override fun addRoots(editor: LibraryEditor) {
                editor.addRoot(VfsUtil.pathToUrl(yaml.virtualFile.path), ArendConfigOrderRootType)
                val srcDir = if (library.sourcesDir.isNotEmpty()) library.sourcesPath else null
                if (srcDir != null) {
                    editor.addRoot(VfsUtil.pathToUrl(srcDir.toString()), OrderRootType.SOURCES)
                }
            }
        }
    }

    override fun getExternalRootTypes() = arrayOf(ArendConfigOrderRootType, OrderRootType.SOURCES)

    override fun getIcon(properties: DummyLibraryProperties?) = ArendIcons.LIBRARY_ICON

    override fun isSuitableModule(module: Module, facetsProvider: FacetsProvider) = ArendModuleType.has(module)
}