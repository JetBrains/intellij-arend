package org.arend.module.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile


class EmptyLibraryConfig(override val name: String, project: Project) : LibraryConfig(project) {
    override val root: VirtualFile?
        get() = null
}