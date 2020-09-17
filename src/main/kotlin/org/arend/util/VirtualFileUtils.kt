package org.arend.util

import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile

fun VirtualFile.getRelativePath(file: VirtualFile, ext: String = ""): ArrayList<String>? {
    val result = ArrayList<String>()
    var cur: VirtualFile? = file
    while (cur != null) {
        if (cur == this) {
            result.reverse()
            return result
        }
        result.add(if (ext.isNotEmpty() && result.isEmpty()) cur.name.removeSuffix(ext) else cur.name)
        cur = cur.parent
    }
    return null
}

fun VirtualFile.getRelativeFile(path: Collection<String>, ext: String = ""): VirtualFile? {
    var cur = this
    var i = 0
    for (name in path) {
        cur = cur.findChild(if (++i == path.size) name + ext else name) ?: return null
    }
    return cur
}

val VirtualFile.configFile: VirtualFile?
    get() = when {
        isDirectory -> findChild(FileUtils.LIBRARY_CONFIG_FILE)
        name == FileUtils.LIBRARY_CONFIG_FILE -> this
        name.endsWith(FileUtils.ZIP_EXTENSION) -> JarFileSystem.getInstance().getJarRootForLocalFile(this)?.findChild(FileUtils.LIBRARY_CONFIG_FILE)
        else -> null
    }

fun String.removeSuffixOrNull(suffix: String): String? =
    if (endsWith(suffix)) substring(0, length - suffix.length) else null

val VirtualFile.libraryName: String?
    get() = when {
        isDirectory -> name
        name == FileUtils.LIBRARY_CONFIG_FILE -> {
            JarFileSystem.getInstance().getVirtualFileForJar(parent)?.let {
                return it.name.removeSuffixOrNull(FileUtils.ZIP_EXTENSION)
            }
            parent.name.removeSuffixOrNull(FileUtils.ZIP_EXTENSION)
        }
        else -> name.removeSuffixOrNull(FileUtils.ZIP_EXTENSION)
    }
