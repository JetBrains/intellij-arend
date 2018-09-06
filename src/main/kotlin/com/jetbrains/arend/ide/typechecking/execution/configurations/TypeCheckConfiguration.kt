package com.jetbrains.arend.ide.typechecking.execution.configurations

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.jetbrains.arend.ide.typechecking.execution.TypeCheckCommand
import com.jetbrains.arend.ide.typechecking.execution.TypeCheckRunConfigurationEditor
import com.jetbrains.arend.ide.vcModules
import org.jdom.Element

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
    var vclangTypeCheckCommand: TypeCheckCommand
        get() = TypeCheckCommand(_vclangArgs.library, _vclangArgs.modulePath, _vclangArgs.definitionFullName)
        set(value) = with(value) {
            _vclangArgs.library = library
            _vclangArgs.modulePath = modulePath
            _vclangArgs.definitionFullName = definitionFullName
        }

    @Property(surroundWithTag = false)
    private var _vclangArgs = SerializableTypeCheckCommand()

    init {
        configurationModule.module = project.vcModules.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.vcModules

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
            TypeCheckRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
            TypeCheckRunState(environment, vclangTypeCheckCommand)

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
