package org.arend.typechecking.execution.configurations

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.jdom.Element
import org.arend.typechecking.execution.TypeCheckCommand
import org.arend.typechecking.execution.TypeCheckRunConfigurationEditor
import org.arend.arendModules

class TypeCheckConfiguration(
        project: Project,
        name: String,
        factory: ConfigurationFactory
) : ModuleBasedConfiguration<TypeCheckRunConfigurationModule>(
        name,
        TypeCheckRunConfigurationModule(project),
        factory
    ),
    RunConfigurationWithSuppressedDefaultDebugAction {

    @get: com.intellij.util.xmlb.annotations.Transient
    @set: com.intellij.util.xmlb.annotations.Transient
    var arendTypeCheckCommand: TypeCheckCommand
        get() = TypeCheckCommand(_arendArgs.library, _arendArgs.modulePath, _arendArgs.definitionFullName)
        set(value) = with(value) {
            _arendArgs.library = library
            _arendArgs.modulePath = modulePath
            _arendArgs.definitionFullName = definitionFullName
        }

    @Property(surroundWithTag = false)
    private var _arendArgs = SerializableTypeCheckCommand()

    init {
        configurationModule.module = project.arendModules.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.arendModules

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
            TypeCheckRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
            TypeCheckRunState(environment, arendTypeCheckCommand)

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
    var modulePath: String = "",
    var definitionFullName: String = ""
)
