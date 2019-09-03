package org.arend.module.config

import com.intellij.openapi.project.Project


class EmptyLibraryConfig(override val name: String, project: Project) : LibraryConfig(project) {
    override val rootDir: String?
        get() = null
}