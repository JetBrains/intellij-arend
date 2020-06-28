package org.arend.debug

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.execution.configurations.RunProfile

class ArendDebuggerRunner: GenericDebuggerRunner() {
    override fun getRunnerId(): String {
        return "ArendDebugRunner"
    }

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return true
    }
}