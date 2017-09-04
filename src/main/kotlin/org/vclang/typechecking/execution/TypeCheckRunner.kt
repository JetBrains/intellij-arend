package org.vclang.typechecking.execution

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner
import org.vclang.typechecking.execution.configurations.TypeCheckConfiguration

class TypeCheckRunner : DefaultProgramRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
            executorId == DefaultRunExecutor.EXECUTOR_ID && profile is TypeCheckConfiguration

    override fun getRunnerId(): String = "TypeCheckRunner"
}
