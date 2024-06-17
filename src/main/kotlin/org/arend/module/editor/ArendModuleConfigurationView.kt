package org.arend.module.editor

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import org.arend.ArendIcons
import org.arend.library.LibraryDependency
import org.arend.module.AREND_LIB
import org.arend.module.config.ArendModuleConfiguration
import org.arend.ui.DualList
import org.arend.ui.addBrowseAndChangeListener
import org.arend.ui.content
import org.arend.util.*
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.JTextField


class ArendModuleConfigurationView(project: Project?, root: String?, name: String? = null) : ArendModuleConfiguration {
    private val moduleRoot = root?.let { FileUtil.toSystemDependentName(it) }

    private val textComponentAccessor = object : TextComponentAccessor<JTextField> {
        override fun getText(component: JTextField) =
            toAbsolute(moduleRoot, component.text)

        override fun setText(component: JTextField, text: String) {
            component.text = toRelative(moduleRoot, text)
        }
    }

    private val sourcesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Sources Directory", "Select the directory in which the source files${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val testsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Tests Directory", "Select the directory with test files${if (name == null) "" else " for module $name"}", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val binariesSwitch = JBCheckBox("Save typechecker output to ", false)
    private val binariesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Binaries Directory", "Select the directory in which the binary files${if (name == null) "" else " of module $name"} will be put", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val extensionsSwitch = JBCheckBox("Load language extensions", false)
    private val extensionsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Extensions Directory", "Select the directory in which the language extensions${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val extensionMainClassTextField = JTextField()
    private val langVersionField = JTextField()
    private val versionField = JTextField()

    private val dualList = object : DualList<LibraryDependency>("Available libraries:", "Module dependencies:", true) {
        override fun isOK(t: LibraryDependency): Boolean {
            val libRoot = VfsUtil.findFile(Paths.get(librariesRoot), false) ?: return false
            return libRoot.findChild(t.name)?.configFile != null || libRoot.findChild(t.name + FileUtils.ZIP_EXTENSION)?.configFile != null
        }

        override fun isAvailable(t: LibraryDependency) = t.name == AREND_LIB || isOK(t)

        override fun getIcon(t: LibraryDependency) = ArendIcons.LIBRARY_ICON
    }.apply {
        selectedList.setEmptyText("No dependencies")
    }

    private val libRootTextField = TextFieldWithBrowseButton()
    private val textFieldChangeListener = libRootTextField.addBrowseAndChangeListener("Path to libraries", "Select the directory in which dependencies${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor()) {
        dualList.availableList.content = (librariesList - dependencies.toSet()).sorted()
        dualList.selectedList.updateUI()
    }

    override var librariesRoot: String
        get() = libRootTextField.text
        set(value) {
            libRootTextField.text = value
            textFieldChangeListener.fireEvent()
        }

    override var sourcesDir: String
        get() = sourcesTextField.text
        set(value) {
            sourcesTextField.text = value
        }

    override var testsDir: String
        get() = testsTextField.text
        set(value) {
            testsTextField.text = value
        }

    override var withBinaries: Boolean
        get() = binariesSwitch.isSelected
        set(value) {
            binariesSwitch.isSelected = value
        }

    override var binariesDirectory: String
        get() = binariesTextField.text
        set(value) {
            binariesTextField.text = value
        }

    override var withExtensions: Boolean
        get() = extensionsSwitch.isSelected
        set(value) {
            extensionsSwitch.isSelected = value
        }

    override var extensionsDirectory: String
        get() = extensionsTextField.text
        set(value) {
            extensionsTextField.text = value
        }

    override var extensionMainClassData: String
        get() = extensionMainClassTextField.text
        set(value) {
            extensionMainClassTextField.text = value
        }

    override var dependencies: List<LibraryDependency>
        get() = dualList.selectedList.content
        set(value) {
            dualList.selectedList.content = value
            dualList.availableList.content = dualList.availableList.content - value
        }

    override var versionString: String
        get() = versionField.text
        set(value) {
            versionField.text = value
        }

    override var langVersionString: String
        get() = langVersionField.text
        set(value) {
            langVersionField.text = value
        }

    private val librariesList: List<LibraryDependency>
        get() {
            val arendLib = LibraryDependency(AREND_LIB)
            val libRoot = VfsUtil.findFile(Paths.get(librariesRoot), true) ?: return listOf(arendLib)
            VfsUtil.markDirtyAndRefresh(false, false, true, libRoot)
            val list = libRoot.children.mapNotNull { file ->
                if (file.name != FileUtils.LIBRARY_CONFIG_FILE && file.refreshed.configFile != null) {
                    file.libraryName?.let { LibraryDependency(it) }
                } else null
            }
            return if (list.contains(arendLib)) list else list + arendLib
        }

    fun createComponent() = panel {
        aligned("Language version: ", langVersionField)
        aligned("Library version: ", versionField)
        aligned("Sources directory: ", sourcesTextField)
        aligned("Tests directory: ", testsTextField)
        checked(binariesSwitch, binariesTextField) { align(AlignX.FILL) }
            .layout(RowLayout.LABEL_ALIGNED)

        group("Extensions") {
            row { cell(extensionsSwitch) }
            aligned("Extensions directory: ", extensionsTextField) { enabledIf(extensionsSwitch.selected) }
            aligned("Extension main class: ", extensionMainClassTextField) { enabledIf(extensionsSwitch.selected) }
        }

        group("Libraries") {
            aligned("Path to libraries: ", libRootTextField)
            row { cell(ScrollPaneFactory.createScrollPane(dualList, true)) }
        }
    }.apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
    }
}