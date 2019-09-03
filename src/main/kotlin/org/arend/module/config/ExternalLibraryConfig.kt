package org.arend.module.config

import org.jetbrains.yaml.psi.YAMLFile


class ExternalLibraryConfig(override val name: String, yaml: YAMLFile) : LibraryConfig(yaml.project) {
    override val sourcesDir = yaml.sourcesDir
    override val binariesDir = yaml.binariesDir
    override val modules = yaml.modules
    override val dependencies = yaml.dependencies

    override val rootDir = yaml.virtualFile?.parent?.path
}