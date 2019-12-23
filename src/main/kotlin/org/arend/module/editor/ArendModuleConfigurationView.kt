package org.arend.module.editor

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.panel
import com.intellij.ui.layout.selected
import org.arend.ArendIcons
import org.arend.library.LibraryDependency
import org.arend.module.config.ArendModuleConfiguration
import org.arend.ui.DualList
import org.arend.ui.addBrowseAndChangeListener
import org.arend.ui.content
import org.arend.util.FileUtils
import org.arend.util.cellRow
import org.arend.util.checked
import org.arend.util.labeled
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.JTextField
import kotlin.streams.toList


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
        addBrowseFolderListener("Sources directory", "Select the directory in which the source files${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val binariesSwitch = JBCheckBox("Save typechecker output to ", false)
    private val binariesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Binaries directory", "Select the directory in which the binary files${if (name == null) "" else " of module $name"} will be put", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val extensionsSwitch = JBCheckBox("Load language extensions", false)
    private val extensionsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Extensions directory", "Select the directory in which the language extensions${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val extensionMainClassTextField = JTextField()
    private val langVersionField = JTextField()

    private val dualList = object : DualList<LibraryDependency>("Available libraries:", "Module dependencies:", true) {
        override fun isAvailable(t: LibraryDependency) = Files.isRegularFile(Paths.get(librariesRoot, t.name, FileUtils.LIBRARY_CONFIG_FILE))

        override fun getIcon(t: LibraryDependency) = ArendIcons.LIBRARY_ICON
    }.apply {
        selectedList.setEmptyText("No dependencies")
    }

    private val libRootTextField = TextFieldWithBrowseButton()
    private val textFieldChangeListener = libRootTextField.addBrowseAndChangeListener("Path to libraries", "Select the directory in which dependencies${if (name == null) "" else " of module $name"} are located", project, FileChooserDescriptorFactory.createSingleFolderDescriptor()) {
        dualList.availableList.content = (librariesList - dependencies).sorted()
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
            dualList.availableList.content -= value
        }

    override var langVersionString: String
        get() = langVersionField.text
        set(value) {
            langVersionField.text = value
        }

    private val librariesList: List<LibraryDependency>
        get() = try {
            Files.list(Paths.get(librariesRoot)).toList().mapNotNull {
                if (Files.isDirectory(it) && Files.isRegularFile(it.resolve(FileUtils.LIBRARY_CONFIG_FILE))) {
                    LibraryDependency(it.fileName.toString())
                } else null
            }
        } catch (e: IOException) {
            emptyList()
        }

    fun createComponent() = panel {
        labeled("Language version: ", langVersionField)
        labeled("Sources directory: ", sourcesTextField)
        checked(binariesSwitch, binariesTextField)

        titledRow("Extensions") {
            row { extensionsSwitch() }
            cellRow {
                label("Extensions directory: ")
                extensionsTextField().enableIf(extensionsSwitch.selected)
            }
            cellRow {
                label("Extension main class: ")
                extensionMainClassTextField().enableIf(extensionsSwitch.selected)
            }
        }

        titledRow("Libraries") {
            labeled("Path to libraries: ", libRootTextField)
            row { ScrollPaneFactory.createScrollPane(dualList, true)() }
        }
    }.apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
    }
}