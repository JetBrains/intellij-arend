package org.arend.toolWindow.repl.action

import org.arend.toolWindow.repl.IntellijRepl
import java.util.function.Supplier

object SetPromptCommand : IntellijReplCommand {
    override fun description() = "Change the REPL prompt"
    override fun help(api: IntellijRepl) = "Change the REPL prompt (current prompt: `${api.handler.consoleView.prompt}`)"
    override fun invoke(line: String, api: IntellijRepl, scanner: Supplier<String>) {
        api.handler.consoleView.prompt = line.removeSurrounding("\"")
    }
}
