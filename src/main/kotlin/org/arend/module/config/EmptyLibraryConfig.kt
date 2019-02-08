package org.arend.module.config

import java.nio.file.Path


class EmptyLibraryConfig(override val name: String) : LibraryConfig() {
    override val rootPath: Path?
        get() = null
}