package org.arend.module.config

import com.intellij.openapi.util.io.FileUtil
import org.arend.library.LibraryDependency
import java.nio.file.Paths


interface ArendModuleConfiguration {
    var librariesRoot: String
    var sourcesDir: String
    var withBinaries: Boolean
    var binariesDirectory: String
    var dependencies: List<LibraryDependency>
    var langVersionString: String

    var flaggedBinariesDir: String?
        get() = if (withBinaries) binariesDirectory else null
        set(value) {
            withBinaries = value != null
            if (value != null) {
                binariesDirectory = value
            }
        }

    fun copyFrom(another: ArendModuleConfiguration) {
        librariesRoot = another.librariesRoot
        sourcesDir = another.sourcesDir
        withBinaries = another.withBinaries
        binariesDirectory = another.binariesDirectory
        dependencies = ArrayList(another.dependencies)
        langVersionString = another.langVersionString
    }

    fun compare(another: ArendModuleConfiguration) =
        librariesRoot == another.librariesRoot &&
        sourcesDir == another.sourcesDir &&
        withBinaries == another.withBinaries &&
        binariesDirectory == another.binariesDirectory &&
        dependencies == another.dependencies &&
        langVersionString == another.langVersionString

    fun toRelative(root: String?, str: String) = if (root == null || str.isEmpty()) str else {
        val rootPath = Paths.get(root)
        val path = Paths.get(str)
        if (path.startsWith(rootPath)) {
            FileUtil.toSystemIndependentName(rootPath.relativize(path).toString())
        } else {
            path.toString()
        }
    }

    fun toAbsolute(root: String?, str: String): String {
        val dStr = FileUtil.toSystemDependentName(str)
        return when {
            root == null -> dStr
            str.isEmpty() -> root
            else -> {
                val path = Paths.get(dStr)
                if (path.isAbsolute) dStr else Paths.get(root).resolve(path).toString()
            }
        }
    }
}