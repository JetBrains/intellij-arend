package org.arend.typechecking.execution.configurations

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.jar.JarApplicationConfiguration
import com.intellij.execution.jar.JarApplicationConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.arend.module.ArendRawLibrary
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.execution.TypeCheckCommand
import org.arend.typechecking.execution.TypeCheckRunConfigurationEditor
import org.arend.util.arendModules
import org.jdom.Element

class TypeCheckConfiguration(
        project: Project,
        name: String,
        factory: ConfigurationFactory
) : ModuleBasedConfiguration<TypeCheckRunConfigurationModule, Element>(
        name,
        TypeCheckRunConfigurationModule(project),
        factory
    ), RunConfigurationWithSuppressedDefaultDebugAction {

    @get: com.intellij.util.xmlb.annotations.Transient
    @set: com.intellij.util.xmlb.annotations.Transient
    var arendTypeCheckCommand: TypeCheckCommand
        get() = TypeCheckCommand(_arendArgs.library, _arendArgs.isTest, _arendArgs.modulePath, _arendArgs.definitionFullName)
        set(value) = with(value) {
            _arendArgs.library = library
            _arendArgs.isTest = isTest
            _arendArgs.modulePath = modulePath
            _arendArgs.definitionFullName = definitionFullName
        }

    @Property(surroundWithTag = false)
    private var _arendArgs = SerializableTypeCheckCommand()

    private val arendSettings = service<ArendSettings>()

    init {
        configurationModule.module = project.arendModules.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.arendModules

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
            TypeCheckRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        if (environment.runnerSettings is DebuggingRunnerData) {
            val libraryManager = project.service<TypeCheckingService>().libraryManager
            val libPaths = if (arendTypeCheckCommand.library.isEmpty()) {
                libraryManager.registeredLibraries.filterIsInstance<ArendRawLibrary>().mapNotNull { it.config.localFSRoot?.path }
            } else {
                (libraryManager.getRegisteredLibrary(arendTypeCheckCommand.library) as? ArendRawLibrary)?.config?.localFSRoot?.let { listOf(it.path) } ?: emptyList()
            }
            val projectPath = if (libPaths.isEmpty()) project.basePath else libPaths.joinToString(" ")
            val librariesRoot = project.service<ArendProjectSettings>().librariesRoot
            val jarConfiguration = JarApplicationConfigurationType.getInstance().createTemplateConfiguration(project) as JarApplicationConfiguration
            jarConfiguration.jarPath = arendSettings.pathToArendJar
            jarConfiguration.programParameters = "$projectPath --recompile" +
                    (if (arendTypeCheckCommand.modulePath.isEmpty()) ""
                    else ("=" + arendTypeCheckCommand.modulePath +
                            if (arendTypeCheckCommand.definitionFullName.isEmpty()) ""
                            else ":" + arendTypeCheckCommand.definitionFullName)) + " -L " + librariesRoot
            val jarAppState = jarConfiguration.getState(executor, environment)
            if (jarAppState != null) {
                return jarAppState
            }
        }
        return TypeCheckRunState(environment, arendTypeCheckCommand)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        XmlSerializer.serializeInto(this, element)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        XmlSerializer.deserializeInto(this, element)
    }
}

@Tag(value = "parameters")
data class SerializableTypeCheckCommand(
    var library: String = "",
    var isTest: Boolean = false,
    var modulePath: String = "",
    var definitionFullName: String = ""
)
