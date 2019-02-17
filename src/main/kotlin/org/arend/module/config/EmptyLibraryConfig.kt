package org.arend.module.config

import com.intellij.openapi.project.Project
import java.nio.file.Path


class EmptyLibraryConfig(override val name: String, project: Project) : LibraryConfig(project) {
    override val rootPath: Path?
        get() = null
}