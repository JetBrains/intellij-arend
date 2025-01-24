package org.arend.scratch

import org.jetbrains.kotlin.idea.scratch.ScratchExpression
import org.jetbrains.kotlin.idea.scratch.ScratchFile
import org.jetbrains.kotlin.idea.scratch.SequentialScratchExecutor

class ArendScratchReplExecutor(file: ScratchFile) : SequentialScratchExecutor(file) {
    override fun executeStatement(expression: ScratchExpression) {
//        TODO("Not yet implemented")
    }

    override fun needProcessToStart(): Boolean {
//        TODO("Not yet implemented")
        return false
    }

    override fun startExecution() {
//        TODO("Not yet implemented")
    }

    override fun stopExecution(callback: (() -> Unit)?) {
//        TODO("Not yet implemented")
    }
}
