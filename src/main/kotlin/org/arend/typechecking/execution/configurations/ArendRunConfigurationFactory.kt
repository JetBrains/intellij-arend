package org.arend.typechecking.execution.configurations

import com.intellij.compiler.options.CompileStepBeforeRun
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

class ArendRunConfigurationFactory(configurationType: ConfigurationType) : ConfigurationFactory(configurationType) {
    override fun createTemplateConfiguration(project: Project) = TypeCheckConfiguration(project, "Arend", this)

    override fun configureBeforeRunTaskDefaults(providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>, task: BeforeRunTask<out BeforeRunTask<*>>) {
        if (providerID == CompileStepBeforeRun.ID) {
            task.isEnabled = false
        }
    }

    override fun getId() = "Arend Typecheck"
}