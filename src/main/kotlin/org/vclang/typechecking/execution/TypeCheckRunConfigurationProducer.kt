package org.vclang.typechecking.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.ext.fullName
import org.vclang.psi.parentOfType
import org.vclang.typechecking.execution.configurations.TypeCheckConfiguration
import org.vclang.typechecking.execution.configurations.TypeCheckConfigurationType

class TypeCheckRunConfigurationProducer
    : RunConfigurationProducer<TypeCheckConfiguration>(TypeCheckConfigurationType()) {

    override fun isConfigurationFromContext(
            configuration: TypeCheckConfiguration,
            context: ConfigurationContext
    ): Boolean {
        val element = context.location?.psiElement
        val definition = element?.parentOfType<VcDefinition>(false)
                ?: element?.parentOfType<VcFile>(false)
        if (definition is VcDefinition) {
            val vclangTypeCheckCommand = TypeCheckCommand(
                    definition.containingFile.virtualFile.path,
                    definition.fullName
            )
            return configuration.configurationModule.module == context.module
                    && configuration.name == "Type check ${definition.fullName}"
                    && configuration.vclangTypeCheckCommand == vclangTypeCheckCommand
        } else if (definition is VcFile) {
            val vclangTypeCheckCommand = TypeCheckCommand(definition.virtualFile.path)
            val name = "Type check ${definition.relativeModulePath}"
            return configuration.configurationModule.module == context.module
                    && configuration.name == name
                    && configuration.vclangTypeCheckCommand == vclangTypeCheckCommand
        }
        return false
    }

    override fun setupConfigurationFromContext(
            configuration: TypeCheckConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>
    ): Boolean {
        val element = context.location?.psiElement
        val definition = element?.parentOfType<VcDefinition>(false)
                ?: element?.parentOfType<VcFile>(false)
        when (definition) {
            is VcDefinition -> {
                sourceElement.set(definition)
                configuration.name = "Type check ${definition.fullName}"
                configuration.vclangTypeCheckCommand = TypeCheckCommand(
                        definition.containingFile.virtualFile.path,
                        definition.fullName
                )
            }
            is VcFile -> {
                sourceElement.set(definition)
                configuration.name = "Type check ${definition.relativeModulePath}"
                configuration.vclangTypeCheckCommand = TypeCheckCommand(definition.virtualFile.path)
            }
            else -> return false
        }
        configuration.configurationModule.module = context.module
        return true
    }
}
