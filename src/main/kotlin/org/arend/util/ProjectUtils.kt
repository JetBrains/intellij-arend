package org.arend.util

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.InlayHintsFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import org.arend.ArendLanguage
import org.arend.injection.InjectedArendEditor
import org.arend.module.ArendModuleType
import org.arend.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.DataModuleReferable
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.settings.ArendProjectSettings
import org.arend.term.group.ConcreteGroup
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.typechecking.ArendExtensionChangeService
import org.arend.typechecking.error.NotificationErrorReporter
import org.jetbrains.yaml.psi.YAMLFile
import java.lang.StringBuilder
import java.nio.file.Path
import java.nio.file.Paths

val Project.arendModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { ArendModuleType.has(it) } }

val Project.allModules: List<Module>
    get() = runReadAction {
        ProjectStructureConfigurable.getInstance(this)
            ?.modulesConfig?.context?.modulesConfigurator?.moduleModel?.modules
            ?.filter { ArendModuleType.has(it) } ?: arendModules
    }

fun Project.findInternalLibrary(name: String): ArendModuleConfigService? =
    ModuleManager.getInstance(this).modules.firstOrNull { ArendModuleType.has(it) && it.name == name }?.service<ArendModuleConfigService>()

val Project.moduleConfigs: List<ArendModuleConfigService>
    get() = allModules.map { it.service<ArendModuleConfigService>() }

fun Project.findLibrary(name: String): LibraryConfig? {
    val config = findInternalLibrary(name)
    return if (config != null) {
        config
    } else {
        val libRoot = service<ArendProjectSettings>().librariesRoot
        if (libRoot.isEmpty()) null else findExternalLibrary(Paths.get(libRoot), name)
    }
}

private fun Project.findConfigInZip(zipFile: VirtualFile): YAMLFile? {
    val zipRoot = JarFileSystem.getInstance().getJarRootForLocalFile(zipFile) ?: return null
    val configFile = zipRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null
    return PsiManager.getInstance(this).findFile(configFile) as? YAMLFile ?: return null
}

fun Project.findExternalLibrary(root: VirtualFile, libName: String): ExternalLibraryConfig? {
    root.findChild(libName + FileUtils.ZIP_EXTENSION)?.let { zip ->
        findConfigInZip(zip)?.let { return ExternalLibraryConfig(libName, it) }
    }

    val configFile = root.findChild(libName)?.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null
    val yaml = PsiManager.getInstance(this).findFile(configFile) as? YAMLFile ?: return null
    return ExternalLibraryConfig(libName, yaml)
}

fun Project.findExternalLibrary(root: Path, libName: String): ExternalLibraryConfig? {
    val dir = VfsUtil.findFile(root, true) ?: return null
    return runReadAction { findExternalLibrary(dir, libName) }
}

fun Module.register() {
    val config = runReadAction {
        val config = ArendModuleConfigService.getInstance(this) ?: return@runReadAction null
        config.copyFromYAML(false)
        config
    } ?: return
    refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)

    val server = project.service<ArendServerService>().server
    server.updateLibrary(config, NotificationErrorReporter(project))
    for (module in server.modules) {
        if (module.locationKind == ModuleLocation.LocationKind.GENERATED && module.libraryName == name) {
            project.addGeneratedModule(module, server.getGroup(module) ?: continue)
        }
    }

    ApplicationManager.getApplication().getService(ArendExtensionChangeService::class.java).initializeModule(config)
    config.isInitialized = true
    DaemonCodeAnalyzer.getInstance(project).restart()
}

private fun Project.addGeneratedModule(module: ModuleLocation, group: ConcreteGroup) {
    val builder = StringBuilder()
    PrettyPrintVisitor(builder, 0).printStatements(group.statements())
    runReadAction {
        val file = PsiFileFactory.getInstance(this).createFileFromText(module.modulePath.toString() + FileUtils.EXTENSION, ArendLanguage.INSTANCE, builder.toString()) as? ArendFile ?: return@runReadAction
        file.virtualFile.isWritable = false
        file.generatedModuleLocation = module
        (group.referable as? DataModuleReferable)?.data = file
        group.match(file) { subgroup1, subgroup2 ->
            (subgroup1.referable as? MetaReferable)?.data = subgroup2.referable
            true
        }
    }
}

fun Editor.isDetailedViewEditor() : Boolean = getUserData(InjectedArendEditor.AREND_GOAL_EDITOR) != null

fun Project.afterTypechecking(files: Collection<ArendFile>) {
    for (editor in EditorFactory.getInstance().allEditors) {
        InlayHintsFactory.clearModificationStamp(editor) // editor.putUserData(PSI_MODIFICATION_STAMP, null)
    }
    runReadAction {
        for (file in files) {
            DaemonCodeAnalyzer.getInstance(this).restart(file)
        }
    }
}