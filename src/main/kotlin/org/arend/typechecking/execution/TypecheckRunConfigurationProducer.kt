package org.arend.typechecking.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.fullName
import org.arend.psi.parentOfType
import org.arend.typechecking.execution.configurations.ArendRunConfigurationFactory
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.typechecking.execution.configurations.TypecheckRunConfigurationType
import org.arend.util.FileUtils.EXTENSION
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
        when (val definition = element?.parentOfType<TCDefinition>(false) ?: element?.parentOfType<ArendFile>(false) ?: if (element is PsiDirectory) element else null) {
            is TCDefinition -> {
                val file = definition.containingFile as? ArendFile ?: return null
                val modulePath = file.moduleLocation ?: return null
                sourceElement?.set(definition)
                val fullName = definition.fullName
                return MyConfiguration("Typecheck $fullName", TypeCheckCommand(file.libraryName ?: "", modulePath.toString(), fullName))
            }
            is ArendFile -> {
                sourceElement?.set(definition)
                val fullName = definition.moduleLocation?.toString() ?: return null
                val test = ArendModuleConfigService.getInstance(context.module)?.testsDirFile
                val fullNameTest = test?.getRelativePath(definition.virtualFile)?.joinToString(".")
                return if (fullNameTest != null) {
                    MyConfiguration("Typecheck $TEST_PREFIX.$fullName$EXTENSION", TypeCheckCommand(definition.libraryName ?: "", "$TEST_PREFIX.$fullName$EXTENSION"))
                } else {
                    MyConfiguration("Typecheck $fullName$EXTENSION", TypeCheckCommand(definition.libraryName ?: "", "$fullName$EXTENSION"))
                }
            }
            is PsiDirectory -> {
                sourceElement?.set(definition)
                val source = ArendModuleConfigService.getInstance(context.module)?.sourcesDirFile
                val test = ArendModuleConfigService.getInstance(context.module)?.testsDirFile
                val fullNameSrc = source?.getRelativePath(definition.virtualFile)?.joinToString(".")
                val fullNameTest = test?.getRelativePath(definition.virtualFile)?.joinToString(".")
                val libraryName = ArendModuleConfigService.getInstance(context.module)?.library?.name?: ""
                return if (fullNameSrc != null) {
                    getMyConfiguration(fullNameSrc, source.name, libraryName)
                } else if (fullNameTest != null) {
                    getMyConfiguration(if (fullNameTest.isEmpty()) "" else "$TEST_PREFIX.$fullNameTest", test.name, libraryName)
                } else {
                    null
                }
            }
            else -> return null
        }
    }

    private fun getMyConfiguration(fullName: String, virtualFileName: String, libraryName: String): MyConfiguration {
        return MyConfiguration("Typecheck ${fullName.ifEmpty { virtualFileName }}",
            TypeCheckCommand(libraryName, fullName.ifEmpty { virtualFileName }))
    }

    companion object {
        private val TYPECHECK_CONFIGURATION_FACTORY = ArendRunConfigurationFactory(TypecheckRunConfigurationType())
        const val TEST_PREFIX = "Test"
    }
}
