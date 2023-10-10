package org.arend.module.editor

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.ModuleRootManager
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
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ArendModuleConfiguration
import org.arend.module.starter.ArendStarterUtils.getLibraryDependencies
import org.arend.ui.DualList
import org.arend.ui.addBrowseAndChangeListener
import org.arend.ui.content
import org.arend.util.*
import java.nio.file.Paths
import javax.swing.BorderFactory
import javax.swing.JTextField


class ArendModuleConfigurationView(
    private val module: Module
) : ArendModuleConfiguration {
    private val root = if (module.isDisposed) null else ModuleRootManager.getInstance(module).contentEntries.firstOrNull()?.file?.let { if (it.isDirectory) it.path else null}
    private val moduleRoot = root?.let { FileUtil.toSystemDependentName(it) }

    private val textComponentAccessor = object : TextComponentAccessor<JTextField> {
        override fun getText(component: JTextField) =
            toAbsolute(moduleRoot, component.text)

        override fun setText(component: JTextField, text: String) {
            component.text = toRelative(moduleRoot, text)
        }
    }

    private val sourcesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Sources Directory", "Select the directory in which the source files${if (name == null) "" else " of module $name"} are located", module.project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val testsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Tests Directory", "Select the directory with test files${if (name == null) "" else " for module $name"}", module.project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val binariesSwitch = JBCheckBox("Save typechecker output to ", false)
    private val binariesTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Binaries Directory", "Select the directory in which the binary files${if (name == null) "" else " of module $name"} will be put", module.project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val extensionsSwitch = JBCheckBox("Load language extensions", false)
    private val extensionsTextField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener("Extensions Directory", "Select the directory in which the language extensions${if (name == null) "" else " of module $name"} are located", module.project, FileChooserDescriptorFactory.createSingleFolderDescriptor(), textComponentAccessor)
    }
    private val extensionMainClassTextField = JTextField()
    private val langVersionField = JTextField()
    private val versionField = JTextField()

    private val dualList = object : DualList<LibraryDependency>("Available libraries:", "Module dependencies:", true) {
        override fun isOK(t: LibraryDependency): Boolean {
            val libRoot = VfsUtil.findFile(Paths.get(librariesRoot), false)
            val newModule = ModuleManager.getInstance(module.project).modules.find { it.name == t.name }

            return libRoot?.findChild(t.name)?.configFile != null || libRoot?.findChild(t.name + FileUtils.ZIP_EXTENSION)?.configFile != null
                    || (newModule != null && dfsDependencyModuleGraph(newModule, mutableSetOf(newModule))) // || additionalModule?.name == t.name
        }

        private fun dfsDependencyModuleGraph(curModule: Module, visitedModules: MutableSet<Module>): Boolean {
            val configService = ArendModuleConfigService.getInstance(curModule)
            val libraryNames = configService?.dependencies?.map { it.name } ?: emptyList()
            val modules = ModuleManager.getInstance(module.project).modules.filter { libraryNames.contains(it.name) }
            for (otherModule in modules) {
                if (module == otherModule) {
                    return false
                }
                if (!visitedModules.contains(otherModule)) {
                    visitedModules.add(otherModule)
                    if (!dfsDependencyModuleGraph(otherModule, visitedModules)) {
                        return false
                    }
                }
            }
            return true
        }

        override fun isAvailable(t: LibraryDependency) = t.name == AREND_LIB || isOK(t)

        override fun getIcon(t: LibraryDependency) = ArendIcons.LIBRARY_ICON

        override fun updateConfig() {
            val configService = ArendModuleConfigService.getInstance(module)
            configService?.updateFromIDEA(this@ArendModuleConfigurationView)
        }
    }.apply {
        selectedList.setEmptyText("No dependencies")
    }

    private val libRootTextField = TextFieldWithBrowseButton()
    private val textFieldChangeListener = libRootTextField.addBrowseAndChangeListener("Path to libraries", "Select the directory in which dependencies of module ${module.name} are located", module.project, FileChooserDescriptorFactory.createSingleFolderDescriptor()) {
        dualList.availableList.content = (librariesList - dependencies.toSet()).sorted()
        dualList.availableList.updateUI()
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
            dualList.availableList.content -= value
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
        get() = getLibraryDependencies(librariesRoot, module)

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
            row {
                comment("In order for other modules to be able to see the newly added module in the project, after adding a new module, click OK and re-enter the \"Project Structure\" section")
            }
        }
    }.apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
    }

    companion object {
        fun getInstance(module: Module?) =
            if (module != null && ArendModuleType.has(module)) ModuleServiceManager.getService(module, ArendModuleConfigurationView::class.java) else null
    }
}