package org.vclang.ide.typecheck.execution.actions

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.vclang.ide.typecheck.execution.TypecheckCommand
import org.vclang.ide.typecheck.execution.configurations.TypecheckConfiguration
import org.vclang.ide.typecheck.execution.configurations.TypecheckConfigurationType
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.parentOfType

class TypecheckRunConfigurationProducer
    : RunConfigurationProducer<TypecheckConfiguration>(TypecheckConfigurationType()) {

    override fun isConfigurationFromContext(
            configuration: TypecheckConfiguration,
            context: ConfigurationContext
    ): Boolean {
        val element = context.location?.psiElement
        val definition = element?.parentOfType<VcDefinition>(false)
                ?: element?.parentOfType<VcFile>(false)
        if (definition is VcDefinition) {
            val vclangTypecheckCommand = TypecheckCommand(
                    definition.containingFile.virtualFile.path,
                    definition.fullyQualifiedName
            )
            return configuration.configurationModule.module == context.module
                    && configuration.name == "Type check ${definition.fullyQualifiedName}"
                    && configuration.vclangTypecheckCommand == vclangTypecheckCommand
        } else if (definition is VcFile) {
            val vclangTypecheckCommand = TypecheckCommand(definition.virtualFile.path)
            val name = "Type check ${definition.modulePath}"
            return configuration.configurationModule.module == context.module
                    && configuration.name == name
                    && configuration.vclangTypecheckCommand == vclangTypecheckCommand
        }
        return false
    }

    override fun setupConfigurationFromContext(
            configuration: TypecheckConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>
    ): Boolean {
        val element = context.location?.psiElement
        val definition = element?.parentOfType<VcDefinition>(false)
                ?: element?.parentOfType<VcFile>(false)
        when (definition) {
            is VcDefinition -> {
                sourceElement.set(definition)
                configuration.name = "Type check ${definition.fullyQualifiedName}"
                configuration.vclangTypecheckCommand = TypecheckCommand(
                    definition.containingFile.virtualFile.path,
                    definition.fullyQualifiedName
                )
            }
            is VcFile -> {
                sourceElement.set(definition)
                configuration.name = "Type check ${definition.modulePath}"
                configuration.vclangTypecheckCommand = TypecheckCommand(definition.virtualFile.path)
            }
            else -> return false
        }
        configuration.configurationModule.module = context.module
        return true
    }
}
