package org.vclang.ide.typecheck.execution.configurations

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.vclang.ide.icons.VcIcons

class TypecheckConfigurationType : ConfigurationTypeBase(
        "TypecheckRunConfiguration",
        "Vclang Typecheck",
        "Vclang typechecking run configuration",
        VcIcons.VCLANG
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                    TypecheckConfiguration(project, "Vclang", this)

            override fun configureBeforeRunTaskDefaults(
                    providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                    task: BeforeRunTask<out BeforeRunTask<*>>
            ) {
                if (providerID == CompileStepBeforeRun.ID) {
                    task.isEnabled = false
                }
            }

            override fun isConfigurationSingletonByDefault(): Boolean = true
        })
    }

    val factory: ConfigurationFactory
        get() = configurationFactories.single()
}
