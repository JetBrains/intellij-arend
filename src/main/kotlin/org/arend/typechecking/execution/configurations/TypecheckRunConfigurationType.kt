package org.arend.typechecking.execution.configurations

import com.intellij.execution.configurations.ConfigurationTypeBase
import org.arend.ArendIcons

class TypecheckRunConfigurationType : ConfigurationTypeBase("TypecheckRunConfiguration", "Arend Typecheck", "Arend typechecking run configuration", ArendIcons.RUN_CONFIGURATION) {
    init {
        addFactory(ArendRunConfigurationFactory(this))
    }
}
