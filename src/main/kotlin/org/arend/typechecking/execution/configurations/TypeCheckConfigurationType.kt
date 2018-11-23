package org.arend.typechecking.execution.configurations

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.arend.ArendIcons

class TypeCheckConfigurationType : ConfigurationTypeBase(
        "TypeCheckRunConfiguration",
        "Arend TypeCheck",
        "Arend type checking run configuration",
        ArendIcons.AREND
) {
    init {
        addFactory(object : ConfigurationFactory(this) {

            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                    TypeCheckConfiguration(project, "Arend", this)

            override fun configureBeforeRunTaskDefaults(
                    providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                    task: BeforeRunTask<out BeforeRunTask<*>>
            ) {
                if (providerID == CompileStepBeforeRun.ID) {
                    task.isEnabled = false
                }
            }
        })
    }
}
