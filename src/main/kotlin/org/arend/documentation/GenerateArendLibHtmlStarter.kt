package org.arend.documentation

import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.editor.colors.EditorColorsManager

class GenerateArendLibHtmlStarter : ApplicationStarter {
    override val commandName: String
        get() = "generateArendLibHtml"

    override fun main(args: List<String>) {
        val arguments = args.map { it.ifEmpty { null } }

        val pathToArendLib = arguments.getOrNull(1) ?: run {
            println("The path to the Arend library is not specified")
            return
        }
        val pathToArendLibInArendSite = arguments.getOrNull(2) ?: run {
            println("The path to the Arend library in Arend site is not specified")
            return
        }

        val editorColorsManager = EditorColorsManager.getInstance()
        val colorScheme = arguments.getOrNull(3)?.let { editorColorsManager.getScheme(it) } ?: editorColorsManager.globalScheme

        val host = arguments.getOrNull(4) ?: "https://arend-lang.github.io/"
        val port = arguments.getOrNull(5)?.toInt()

        val versionArendLib = arguments.getOrNull(6)

        generateHtmlForArendLib(pathToArendLib, pathToArendLibInArendSite, colorScheme, host, port, versionArendLib)
    }
}