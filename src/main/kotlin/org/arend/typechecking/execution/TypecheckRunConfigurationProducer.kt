package org.arend.typechecking.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.fullName
import org.arend.psi.parentOfType
import org.arend.typechecking.execution.configurations.ArendRunConfigurationFactory
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.typechecking.execution.configurations.TypecheckRunConfigurationType
import org.arend.util.getRelativePath

class TypecheckRunConfigurationProducer: LazyRunConfigurationProducer<TypeCheckConfiguration>() {
    override fun getConfigurationFactory() = TYPECHECK_CONFIGURATION_FACTORY

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
        when (val definition = element?.parentOfType<PsiLocatedReferable>(false) ?: element?.parentOfType<ArendFile>(false)) {
            is ArendFile -> {
                sourceElement?.set(definition)
                val fullName = definition.moduleLocation?.toString() ?: return null

                val test = ArendModuleConfigService.getInstance(context.module)?.testsDirFile
                val isTest = test?.getRelativePath(definition.virtualFile)?.joinToString(".")

                return MyConfiguration("Typecheck $fullName", TypeCheckCommand(definition.libraryName ?: "", isTest != null, fullName))
            }
            is PsiLocatedReferable -> {
                val file = definition.containingFile as? ArendFile ?: return null
                val modulePath = file.moduleLocation ?: return null
                sourceElement?.set(definition)
                val fullName = definition.fullName

                val test = ArendModuleConfigService.getInstance(context.module)?.testsDirFile
                val isTest = test?.getRelativePath(file.virtualFile)?.joinToString(".")

                return MyConfiguration("Typecheck $fullName", TypeCheckCommand(file.libraryName ?: "", isTest != null, modulePath.toString(), fullName))
            }
            else -> return null
        }
    }

    companion object {
        private val TYPECHECK_CONFIGURATION_FACTORY = ArendRunConfigurationFactory(TypecheckRunConfigurationType())
    }
}
