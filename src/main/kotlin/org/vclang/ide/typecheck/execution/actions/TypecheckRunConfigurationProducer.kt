package org.vclang.ide.typecheck.execution.actions

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.vclang.ide.typecheck.execution.TypecheckCommand
import org.vclang.ide.typecheck.execution.configurations.TypecheckConfiguration
import org.vclang.ide.typecheck.execution.configurations.TypecheckConfigurationType
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.psi.VcDefFunction
import org.vclang.lang.core.psi.parentOfType

class TypecheckRunConfigurationProducer
    : RunConfigurationProducer<TypecheckConfiguration>(TypecheckConfigurationType()) {

    override fun isConfigurationFromContext(
            configuration: TypecheckConfiguration,
            context: ConfigurationContext
    ): Boolean {
        val location = context.location ?: return false
        val definition = location.psiElement.parentOfType<VcDefFunction>(false) ?: return false
        val vclangTypecheckCommand = TypecheckCommand(
                definition.containingFile.virtualFile.path,
                definition.fullyQualifiedName
        )
        return configuration.configurationModule.module == context.module
                && configuration.name == "Type check ${definition.fullyQualifiedName}"
                && configuration.vclangTypecheckCommand == vclangTypecheckCommand
    }

    override fun setupConfigurationFromContext(
            configuration: TypecheckConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>
    ): Boolean {
        val location = context.location ?: return false
        val definition = location.psiElement.parentOfType<VcDefFunction>(false) ?: return false
        sourceElement.set(definition)
        configuration.configurationModule.module = context.module
        configuration.name = "Type check ${definition.fullyQualifiedName}"
        configuration.vclangTypecheckCommand = TypecheckCommand(
                definition.containingFile.virtualFile.path,
                definition.fullyQualifiedName
        )
        return true
    }
}
