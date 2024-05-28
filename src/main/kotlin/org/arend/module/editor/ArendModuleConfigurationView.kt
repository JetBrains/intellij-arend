package org.arend.module.editor

import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import org.arend.ArendIcons
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.library.LibraryDependency
import org.arend.module.AREND_LIB
import org.arend.module.ArendModuleType
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

    private fun getLibRootAndNewModule(dependencyName: String): Pair<VirtualFile?, Module?> {
        return Pair(
            VfsUtil.findFile(Paths.get(librariesRoot), false),
            module.project.allModules.find { it.name == dependencyName }
        )
    }

    fun isModuleOrLibraryExists(dependencyName: String): Boolean {
        if (dependencyName == AREND_LIB) {
            return true
        }
        val (libRoot, newModule) = getLibRootAndNewModule(dependencyName)

        return libRoot?.findChild(dependencyName)?.configFile != null || libRoot?.findChild(dependencyName + FileUtils.ZIP_EXTENSION)?.configFile != null || newModule != null
    }

    private val dualList = object : DualList<LibraryDependency>(module, "Available libraries:", "Module dependencies:", true) {
        override fun isOK(t: LibraryDependency): Boolean {
            val (libRoot, newModule) = getLibRootAndNewModule(t.name)

            return libRoot?.findChild(t.name)?.configFile != null || libRoot?.findChild(t.name + FileUtils.ZIP_EXTENSION)?.configFile != null
                    || (newModule != null && dfsDependencyModuleGraph(module, newModule, mutableSetOf(newModule)))
        }

        override fun isValueExists(t: LibraryDependency): Boolean = isModuleOrLibraryExists(t.name)

        override fun isAvailable(t: LibraryDependency) = t.name == AREND_LIB || isOK(t)

        override fun getIcon(t: LibraryDependency) =
            if (t.name == AREND_LIB) {
                ArendIcons.LIBRARY_ICON
            } else {
                ArendIcons.AREND
            }

        override fun notAvailableNotification(notAvailableElements: List<LibraryDependency>) {
            val notification = notificationGroup.createNotification("If you add the selected modules `$notAvailableElements` to the module dependencies of module `${module.name}`, a cyclic dependency between the modules will appear", MessageType.WARNING)
            Notifications.Bus.notify(notification, module.project)
        }

        override fun updateOtherLists() {
            val modules = module.project.allModules
            for (otherModule in modules) {
                if (module != otherModule) {
                    getInstance(otherModule)?.updateAvailableLibrariesAndDependencies()
                }
            }
        }
    }.apply {
        selectedList.setEmptyText("No dependencies")
    }

    private val libRootTextField = TextFieldWithBrowseButton()
    private val textFieldChangeListener = libRootTextField.addBrowseAndChangeListener("Path to libraries", "Select the directory in which dependencies of module ${module.name} are located", module.project, FileChooserDescriptorFactory.createSingleFolderDescriptor()) {
        ApplicationManager.getApplication().executeOnPooledThread {
            dualList.availableList.content = (librariesList - dependencies.toSet()).sorted()
            dualList.availableList.updateUI()
            dualList.selectedList.updateUI()
        }
    }

    override var librariesRoot: String
        get() = libRootTextField.text
        set(value) {
            libRootTextField.text = value
            ApplicationManager.getApplication().executeOnPooledThread {
                textFieldChangeListener.fireEvent()
            }
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
        row("Current graph of Arend modules: ") {
            actionButton(object : AnAction("Show Current Graph of Arend Modules", "A Graph of Arend Modules", ArendIcons.ORTHOGONAL_GRAPH) {
                private val usedNodes = mutableSetOf<Module>()
                private val edges = mutableSetOf<GraphEdge>()

                private fun findEdges(currentNode: Module, modules: List<Module>) {
                    usedNodes.add(currentNode)

                    val from = currentNode.name
                    val view = getInstance(currentNode)
                    val dependencies = view?.dualList?.selectedList?.content?.map { it.name } ?: emptyList()
                    val children = modules.filter { dependencies.contains(it.name) && it.name != from }.onEach {
                        edges.add(GraphEdge(from, it.name))
                    }

                    for (child in children) {
                        if (!usedNodes.contains(child)) {
                            findEdges(child, modules)
                        }
                    }
                }

                override fun actionPerformed(e: AnActionEvent) {
                    usedNodes.clear()
                    edges.clear()

                    val modules = e.project?.allModules ?: emptyList()
                    for (module in modules) {
                        if (!usedNodes.contains(module)) {
                            findEdges(module, modules)
                        }
                    }

                    val simulator = GraphSimulator(e.project, this.toString(), edges, usedNodes.map { GraphNode(it.name) }.toSet())
                    simulator.displayOrthogonal()
                }
            })
        }
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
                comment("Libraries that do not belong to the project or modules that have a cyclic dependency are highlighted in red")
            }
        }
    }.apply {
        border = BorderFactory.createEmptyBorder(0, 10, 0, 10)
    }

    fun updateAvailableLibrariesAndDependencies() {
        dualList.selectedList.content = dualList.selectedList.content.filter { libraryDependency -> isModuleOrLibraryExists(libraryDependency.name) }
        textFieldChangeListener.textChanged()
    }

    companion object {
        fun getInstance(module: Module?) =
            if (module != null && ArendModuleType.has(module)) ModuleServiceManager.getService(module, ArendModuleConfigurationView::class.java) else null

        fun dfsDependencyModuleGraph(module: Module, curModule: Module, visitedModules: MutableSet<Module>): Boolean {
            if (curModule == module) {
                return false
            }
            val libraryNames = getInstance(curModule)?.dependencies?.map { it.name } ?: emptyList()
            val modules = module.project.allModules.filter { libraryNames.contains(it.name) }
            var flag = true
            for (otherModule in modules) {
                if (!visitedModules.contains(otherModule)) {
                    if (module != otherModule) {
                        visitedModules.add(otherModule)
                    }
                    if (!dfsDependencyModuleGraph(module, otherModule, visitedModules)) {
                        flag = false
                    }
                }
            }
            return flag
        }
    }
}
