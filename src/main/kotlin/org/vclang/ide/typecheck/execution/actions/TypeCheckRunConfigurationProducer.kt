package org.vclang.ide.typecheck.execution.actions

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.vclang.ide.typecheck.execution.TypeCheckCommand
import org.vclang.ide.typecheck.execution.configurations.TypeCheckConfiguration
import org.vclang.ide.typecheck.execution.configurations.TypeCheckConfigurationType
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.parentOfType

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
                    definition.fullyQualifiedName
            )
            return configuration.configurationModule.module == context.module
                    && configuration.name == "Type check ${definition.fullyQualifiedName}"
                    && configuration.vclangTypeCheckCommand == vclangTypeCheckCommand
        } else if (definition is VcFile) {
            val vclangTypeCheckCommand = TypeCheckCommand(definition.virtualFile.path)
            val name = "Type check ${definition.modulePath}"
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
                configuration.name = "Type check ${definition.fullyQualifiedName}"
                configuration.vclangTypeCheckCommand = TypeCheckCommand(
                    definition.containingFile.virtualFile.path,
                    definition.fullyQualifiedName
                )
            }
            is VcFile -> {
                sourceElement.set(definition)
                configuration.name = "Type check ${definition.modulePath}"
                configuration.vclangTypeCheckCommand = TypeCheckCommand(definition.virtualFile.path)
            }
            else -> return false
        }
        configuration.configurationModule.module = context.module
        return true
    }
}
