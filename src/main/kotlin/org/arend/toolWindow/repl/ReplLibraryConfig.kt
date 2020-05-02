package org.arend.toolWindow.repl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig

class ReplLibraryConfig(override val name: String, project: Project) : LibraryConfig(project) {
    override val sourcesDir = project.guessProjectDir()?.path.orEmpty()
    override val modules = arrayListOf<ModulePath>()
    override val rootDir = project.guessProjectDir()?.path.orEmpty()
}