package org.arend.toolWindow.repl

import org.arend.repl.CommandHandler
import org.arend.repl.action.NormalizeCommand
import org.arend.repl.action.PrettyPrintFlagCommand

fun getReplCompletion(commandName: String): Array<Any> =
    when (CommandHandler.INSTANCE.commandMap[commandName]) {
        is PrettyPrintFlagCommand -> PrettyPrintFlagCommand.AVAILABLE_OPTIONS.toTypedArray()
        is NormalizeCommand -> NormalizeCommand.AVAILABLE_OPTIONS.map { it }.toTypedArray()
        is CommandHandler.HelpCommand -> CommandHandler.INSTANCE.commandMap.keys.toTypedArray()
        else -> emptyArray()
    }