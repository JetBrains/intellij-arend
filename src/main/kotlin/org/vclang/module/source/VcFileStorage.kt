package org.vclang.module.source

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.caching.CacheStorageSupplier
import com.jetbrains.jetpad.vclang.module.source.SourceSupplier
import com.jetbrains.jetpad.vclang.module.source.Storage
import org.vclang.VcFileType
import org.vclang.getPsiFileFor
import org.vclang.psi.VcFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Objects

class VcFileStorage(private val project: Project): Storage<VcFileStorage.SourceId> {
    private val sourceRoot = Paths.get(project.basePath)
    private val cacheRoot = run {
        val basePath = Paths.get(project.basePath)
        val relativeModulePath = basePath.relativize(sourceRoot)
        basePath.resolve(".cache").resolve(relativeModulePath)
    }

    private val sourceSupplier = VcFileSourceSupplier()
    private val cacheStorageSupplier = VcFileCacheStorageSupplier()

    override fun getCacheInputStream(sourceId: SourceId): InputStream? =
            cacheStorageSupplier.getCacheInputStream(sourceId)

    override fun getCacheOutputStream(sourceId: SourceId): OutputStream? =
            cacheStorageSupplier.getCacheOutputStream(sourceId)

    override fun locateModule(modulePath: ModulePath): SourceId? =
            sourceSupplier.locateModule(modulePath)

    fun locateModule(module: VcFile): SourceId? = sourceSupplier.locateModule(module)

    override fun isAvailable(sourceId: SourceId): Boolean = sourceSupplier.isAvailable(sourceId)

    override fun loadSource(sourceId: SourceId, errorReporter: ErrorReporter): SourceSupplier.LoadResult? =
        sourceSupplier.loadSource(sourceId, errorReporter)

    override fun getAvailableVersion(sourceId: SourceId): Long =
            sourceSupplier.getAvailableVersion(sourceId)

    inner class SourceId(
            val module: VcFile
    ) : com.jetbrains.jetpad.vclang.module.source.SourceId {
        val storage = this@VcFileStorage

        override fun getModulePath(): ModulePath {
            val path = Paths.get(module.virtualFile.path)
            val name = module.virtualFile.nameWithoutExtension
            return modulePath(sourceRoot.relativize(path).resolveSibling(name))!!
        }

        override fun equals(other: Any?): Boolean {
            return this === other
                    || other is SourceId
                    && modulePath == other.modulePath
                    && storage == other.storage
        }

        override fun hashCode(): Int = Objects.hash(modulePath, storage)

        override fun toString(): String = module.virtualFile.path
    }

    private inner class VcFileSourceSupplier : SourceSupplier<SourceId> {

        override fun locateModule(modulePath: ModulePath): SourceId? {
            val path = modulePathToSourcePath(modulePath)
            val file = LocalFileSystem.getInstance().findFileByPath(path.toString())
            val module = project.getPsiFileFor(file) as? VcFile
            return module?.let { SourceId(it) }
        }

        fun locateModule(module: VcFile): SourceId? = SourceId(module)

        override fun isAvailable(sourceId: SourceId): Boolean =
                sourceId.storage === this@VcFileStorage && sourceId.module.virtualFile.exists()

        override fun loadSource(sourceId: SourceId, errorReporter: ErrorReporter): SourceSupplier.LoadResult? =
            if (isAvailable(sourceId)) SourceSupplier.LoadResult.make(sourceId.module, getAvailableVersion(sourceId)) else null

        override fun getAvailableVersion(sourceId: SourceId): Long =
                sourceId.module.virtualFile.timeStamp

        private fun modulePathToSourcePath(modulePath: ModulePath): Path =
                sourceFile(baseFile(sourceRoot, modulePath))
    }

    private inner class VcFileCacheStorageSupplier : CacheStorageSupplier<SourceId> {

        override fun getCacheInputStream(sourceId: SourceId): InputStream? {
            if (sourceId.storage !== this@VcFileStorage) return null
            val file = cacheFileForSourceId(sourceId)
            return try {
                file?.inputStream
            } catch (ignore: IOException) {
                null
            }
        }

        override fun getCacheOutputStream(sourceId: SourceId): OutputStream? {
            if (sourceId.storage !== this@VcFileStorage) return null
            val path = cachePathForSourceId(sourceId)
            return try {
                Files.createDirectories(path.parent)
                Files.newOutputStream(path)
            } catch (ignored: IOException) {
                null
            }
        }

        private fun modulePathToSourcePath(modulePath: ModulePath): Path =
                cacheFile(baseFile(cacheRoot, modulePath))

        private fun cacheFileForSourceId(sourceId: SourceId): VirtualFile? {
            val path = cachePathForSourceId(sourceId)
            return LocalFileSystem.getInstance().findFileByPath(path.toString())
        }

        private fun cachePathForSourceId(sourceId: SourceId): Path =
                modulePathToSourcePath(sourceId.modulePath)
    }

    companion object {

        fun modulePath(path: Path): ModulePath? {
            require(!path.isAbsolute) { "$path is not absolute" }
            val names = path.map { it.toString() }
            val pathRegex = "[a-zA-Z_][a-zA-Z0-9_']*".toRegex()
            return if (names.all { it.matches(pathRegex) }) ModulePath(names) else null
        }

        fun sourceFile(base: Path): Path =
                base.resolveSibling("${base.fileName}.${VcFileType.defaultExtension}")

        fun cacheFile(base: Path): Path =
                base.resolveSibling("${base.fileName}.${VcFileType.defaultCacheExtension}")

        private fun baseFile(root: Path, modulePath: ModulePath): Path =
                root.resolve(Paths.get("", *modulePath.toArray()))
    }
}
