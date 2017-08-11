package org.vclang.ide.typecheck.execution.configurations

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
import org.vclang.ide.typecheck.execution.TypecheckCommand
import org.vclang.ide.typecheck.execution.options.TypecheckRunConfigurationEditor
import org.vclang.lang.core.modulesWithVclangProject

class TypecheckConfiguration(
        project: Project,
        name: String,
        factory: ConfigurationFactory
) : ModuleBasedConfiguration<TypecheckRunConfigurationModule>(
        name,
        TypecheckRunConfigurationModule(project),
        factory
    ),
    RunConfigurationWithSuppressedDefaultDebugAction {

    @get: com.intellij.util.xmlb.annotations.Transient
    @set: com.intellij.util.xmlb.annotations.Transient
    var vclangTypecheckCommand: TypecheckCommand
        get() = TypecheckCommand(_vclangArgs.modulePath, _vclangArgs.definitionName)
        set(value) = with(value) {
            _vclangArgs.modulePath = modulePath
            _vclangArgs.definitionName = definitionName
        }

    @Property(surroundWithTag = false)
    private var _vclangArgs = SerializableTypecheckCommand()

    init {
        configurationModule.module = project.modulesWithVclangProject.firstOrNull()
    }

    override fun getValidModules(): Collection<Module> = project.modulesWithVclangProject

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
            TypecheckRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
            TypecheckRunState(environment, vclangTypecheckCommand)

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
data class SerializableTypecheckCommand(
        var modulePath: String = "",
        var definitionName: String = ""
)
