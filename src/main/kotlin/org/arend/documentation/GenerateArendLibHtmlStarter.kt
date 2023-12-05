package org.arend.documentation

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.editor.colors.EditorColorsManager

class GenerateArendLibHtmlStarter : ApplicationStarter {
    override val commandName: String
        get() = "generateArendLibHtml"

    override fun main(args: List<String>) {
        val arguments = args.map { it.ifEmpty { null } }

        val path = arguments.getOrNull(1) ?: run {
            println("The path to the Arend library is not specified")
            return
        }
        val editorColorsManager = EditorColorsManager.getInstance()
        val scheme = arguments.getOrNull(2)?.let { editorColorsManager.getScheme(it) } ?: editorColorsManager.globalScheme
        val version = arguments.getOrNull(3) ?: ""
        val host = arguments.getOrNull(4) ?: "https://arend-lang.github.io/"
        val port = arguments.getOrNull(5)?.toInt()

        generateHtmlForArendLib(path, scheme, version, host, port)
    }
}