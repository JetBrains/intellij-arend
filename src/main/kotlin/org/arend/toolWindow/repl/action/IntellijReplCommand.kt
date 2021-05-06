package org.arend.toolWindow.repl.action

import org.arend.repl.QuitReplException
import org.arend.repl.Repl
import org.arend.repl.action.ReplCommand
import org.arend.toolWindow.repl.IntellijRepl
import java.util.function.Supplier

interface IntellijReplCommand : ReplCommand {
    @Throws(QuitReplException::class)
    override operator fun invoke(line: String, api: Repl, scanner: Supplier<String>) {
        assert(api is IntellijRepl)
        invoke(line, api as IntellijRepl, scanner)
    }

    override fun help(api: Repl): String {
        assert(api is IntellijRepl)
        return help(api as IntellijRepl)
    }

    fun help(api: IntellijRepl): String = super.help(api)

    /**
     * @param line    the command prefix is already removed.
     * @param api     repl context
     * @param scanner user input reader
     */
    @Throws(QuitReplException::class)
    operator fun invoke(line: String, api: IntellijRepl, scanner: Supplier<String>)
}