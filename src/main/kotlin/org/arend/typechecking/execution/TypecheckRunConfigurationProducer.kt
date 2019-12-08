package org.arend.typechecking.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.fullName
import org.arend.psi.parentOfType
import org.arend.typechecking.execution.configurations.ArendRunConfigurationFactory
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.typechecking.execution.configurations.TypecheckRunConfigurationType

class TypecheckRunConfigurationProducer: LazyRunConfigurationProducer<TypeCheckConfiguration>() {
    override fun getConfigurationFactory() = ArendRunConfigurationFactory(TypecheckRunConfigurationType())

    override fun isConfigurationFromContext(configuration: TypeCheckConfiguration, context: ConfigurationContext): Boolean {
        val myConfiguration = configurationFromContext(context, null) ?: return false
        return configuration.configurationModule.module == context.module &&
               configuration.name == myConfiguration.name &&
               configuration.arendTypeCheckCommand == myConfiguration.command
    }

    override fun setupConfigurationFromContext(configuration: TypeCheckConfiguration, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
        val myConfiguration = configurationFromContext(context, sourceElement) ?: return false
        configuration.name = myConfiguration.name
        configuration.arendTypeCheckCommand = myConfiguration.command
        configuration.configurationModule.module = context.module
        return true
    }

    private data class MyConfiguration(val name: String, val command: TypeCheckCommand)

    private fun configurationFromContext(context: ConfigurationContext, sourceElement: Ref<PsiElement>?): MyConfiguration? {
        val element = context.location?.psiElement
        when (val definition = element?.parentOfType<TCDefinition>(false) ?: element?.parentOfType<ArendFile>(false)) {
            is TCDefinition -> {
                val file = definition.containingFile as? ArendFile ?: return null
                val modulePath = file.modulePath ?: return null
                sourceElement?.set(definition)
                val fullName = definition.fullName
                return MyConfiguration("Typecheck $fullName", TypeCheckCommand(file.libraryName ?: "", modulePath.toString(), fullName))
            }
            is ArendFile -> {
                sourceElement?.set(definition)
                val fullName = definition.modulePath?.toString() ?: return null
                return MyConfiguration("Typecheck $fullName", TypeCheckCommand(definition.libraryName ?: "", fullName))
            }
            else -> return null
        }
    }
}
