package org.vclang.typechecking.error

import com.intellij.openapi.diagnostic.Logger
import com.jetbrains.jetpad.vclang.error.Error.Level
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.doc.DocStringBuilder
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig


class LogErrorReporter(private val ppConfig: PrettyPrinterConfig): ErrorReporter {
    override fun report(error: GeneralError) {
        val logger = Logger.getInstance(LogErrorReporter::class.java)
        val msg = DocStringBuilder.build(error.getDoc(ppConfig))
        when (error.level) {
            Level.INFO -> logger.info(msg)
            Level.WARNING -> logger.warn(msg)
            Level.GOAL -> logger.info(msg)
            Level.ERROR -> logger.error(msg)
        }
    }
}