package org.arend.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths

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

fun VirtualFile.getRelativeFile(path: Collection<String>, ext: String = "", create: Boolean = false): VirtualFile? {
    var cur = this
    for ((i, name) in path.withIndex()) {
        val eName = if (i == path.size - 1) name + ext else name
        if (eName == ".") {
            continue
        }
        cur = if (eName == "..") {
            cur.parent ?: return null
        } else {
            val child = cur.findChild(eName)
            if (child == null && !create) {
                return null
            }
            child ?: if (i == path.size - 1) cur.createChildData(this, eName) else cur.createChildDirectory(this, eName)
        }
    }
    return cur
}

val VirtualFile.configFile: VirtualFile?
    get() = when {
        isDirectory -> {
            var configFile: VirtualFile? = null
            ApplicationManager.getApplication().executeOnPooledThread {
                configFile = findChild(FileUtils.LIBRARY_CONFIG_FILE)
            }.get()
            configFile
        }
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
            parent?.name
        }
        else -> name.removeSuffixOrNull(FileUtils.ZIP_EXTENSION)
    }

val VirtualFile.libraryRootParent: VirtualFile?
    get() = when {
        isDirectory -> parent
        name == FileUtils.LIBRARY_CONFIG_FILE -> {
            JarFileSystem.getInstance().getVirtualFileForJar(parent)?.let {
                return it.parent
            }
            parent?.parent
        }
        else -> parent
    }

val VirtualFile.refreshed: VirtualFile
    get() {
        VfsUtil.markDirtyAndRefresh(false, false, false, this)
        val file = JarFileSystem.getInstance().getJarRootForLocalFile(this) ?: return this
        VfsUtil.markDirtyAndRefresh(false, false, false, file)
        return this
    }

fun refreshLibrariesDirectory(libRoot: Path): VirtualFile? {
    val file = VfsUtil.findFile(libRoot, true) ?: return null
    VfsUtil.markDirtyAndRefresh(false, false, false, file)
    for (child in file.children) {
        child.refreshed
    }
    return file
}

fun refreshLibrariesDirectory(dir: String): VirtualFile? =
    if (dir.isEmpty()) null else refreshLibrariesDirectory(Paths.get(dir))
