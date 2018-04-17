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

class TypeCheckRunConfigurationProducer: RunConfigurationProducer<TypeCheckConfiguration>(TypeCheckConfigurationType()) {

    override fun isConfigurationFromContext(configuration: TypeCheckConfiguration, context: ConfigurationContext): Boolean {
        val myConfiguration = configurationFromContext(context, null) ?: return false
        return configuration.configurationModule.module == context.module &&
               configuration.name == myConfiguration.name &&
               configuration.vclangTypeCheckCommand == myConfiguration.command
    }

    override fun setupConfigurationFromContext(configuration: TypeCheckConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val myConfiguration = configurationFromContext(context, sourceElement) ?: return false
        configuration.name = myConfiguration.name
        configuration.vclangTypeCheckCommand = myConfiguration.command
        configuration.configurationModule.module = context.module
        return true
    }

    private data class MyConfiguration(val name: String, val command: TypeCheckCommand)

    private fun configurationFromContext(context: ConfigurationContext, sourceElement: Ref<PsiElement>?): MyConfiguration? {
        val element = context.location?.psiElement
        val definition = element?.parentOfType<VcDefinition>(false) ?: element?.parentOfType<VcFile>(false)
        when (definition) {
            is VcDefinition -> {
                val file = definition.containingFile as? VcFile ?: return null
                sourceElement?.set(definition)
                val fullName = definition.fullName
                return MyConfiguration("Type check $fullName", TypeCheckCommand("", file.fullName, fullName))
            }
            is VcFile -> {
                sourceElement?.set(definition)
                val fullName = definition.fullName
                return MyConfiguration("Type check $fullName", TypeCheckCommand("", fullName))
            }
            else -> return null
        }
    }
}
