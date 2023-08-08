package org.arend.toolWindow.repl.action

import com.intellij.psi.PsiElement
import org.arend.repl.action.ShowContextCommand
import org.arend.toolWindow.repl.IntellijRepl
import java.util.function.Supplier

object ShowContextCommandIntellij: IntellijReplCommand {
    override fun invoke(line: String, api: IntellijRepl, scanner: Supplier<String>) {
        val builder = StringBuilder()
        for (statement in api.statements) {
            builder.append((statement as? PsiElement)?.text)
            builder.append("\n")
        }
        api.print(builder.toString())
    }

    override fun description(): String = ShowContextCommand.INSTANCE.description()

}