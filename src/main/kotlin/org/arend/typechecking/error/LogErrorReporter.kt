package org.arend.typechecking.error

import com.intellij.openapi.diagnostic.Logger
import org.arend.error.Error.Level
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.error.doc.DocStringBuilder
import org.arend.term.prettyprint.PrettyPrinterConfig


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