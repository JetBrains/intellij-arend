package org.arend.module.config

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Path
import java.nio.file.Paths


class ExternalLibraryConfig(override val name: String, yaml: YAMLFile) : LibraryConfig(yaml.project) {
    override val sourcesDir = yaml.sourcesDir
    override val outputDir = yaml.outputDir
    override val modules = yaml.modules
    override val dependencies = yaml.dependencies

    override val rootPath: Path = Paths.get(FileUtil.toSystemDependentName(yaml.virtualFile.path))

    override val sourcesDirFile: VirtualFile?
        get() = null
}