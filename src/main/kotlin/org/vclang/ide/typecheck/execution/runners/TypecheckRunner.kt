package org.vclang.ide.typecheck.execution.runners

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner
import org.vclang.ide.typecheck.execution.configurations.TypeCheckConfiguration

class TypecheckRunner : DefaultProgramRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
            executorId == DefaultRunExecutor.EXECUTOR_ID && profile is TypeCheckConfiguration

    override fun getRunnerId(): String = "TypecheckRunner"
}
