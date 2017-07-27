package org.vclang.ide.module.source

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.frontend.namespace.ModuleRegistry
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.caching.CacheStorageSupplier
import com.jetbrains.jetpad.vclang.module.source.SourceSupplier
import com.jetbrains.jetpad.vclang.module.source.Storage
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace
import com.jetbrains.jetpad.vclang.naming.scope.primitive.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.primitive.NamespaceScope
import com.jetbrains.jetpad.vclang.naming.scope.primitive.Scope
import org.vclang.lang.VcFileType
import org.vclang.lang.core.parser.AbstractTreeFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class VcFileStorage(
        private val project: Project,
        private val sourceRoot: Path,
        private val cacheRoot: Path?,
        private val nameResolver: NameResolver,
        private val moduleRegistry: ModuleRegistry
) : Storage<VcFileStorage.SourceId> {
    private var globalScope: Scope = EmptyScope()
    private val sourceSupplier = VcFileSourceSupplier()
    private val cacheStorageSupplier = VcFileCacheStorageSupplier()

    fun setPreludeNamespace(ns: Namespace) {
        globalScope = NamespaceScope(ns)
    }

    override fun getCacheInputStream(sourceId: SourceId): InputStream? =
            cacheStorageSupplier.getCacheInputStream(sourceId)

    override fun getCacheOutputStream(sourceId: SourceId): OutputStream? =
            cacheStorageSupplier.getCacheOutputStream(sourceId)

    override fun locateModule(modulePath: ModulePath): SourceId? =
            sourceSupplier.locateModule(modulePath)

    override fun isAvailable(sourceId: SourceId): Boolean = sourceSupplier.isAvailable(sourceId)

    override fun loadSource(
            sourceId: SourceId,
            errorReporter: ErrorReporter
    ): SourceSupplier.LoadResult? = sourceSupplier.loadSource(sourceId, errorReporter)

    override fun getAvailableVersion(sourceId: SourceId): Long =
            sourceSupplier.getAvailableVersion(sourceId)

    inner class SourceId(
            private val modulePath: ModulePath
    ) : com.jetbrains.jetpad.vclang.module.source.SourceId {
        val storage: VcFileStorage = this@VcFileStorage
        val relativeFilePath: Path
            get() = Paths.get("", *modulePath.toArray())

        override fun getModulePath(): ModulePath = modulePath

        override fun equals(other: Any?): Boolean {
            return this === other
                    || other is SourceId
                    && modulePath == other.modulePath
                    && storage == other.storage
        }

        override fun hashCode(): Int = Objects.hash(storage, modulePath)

        override fun toString(): String = sourceFile(baseFile(sourceRoot, modulePath)).toString()
    }

    private inner class VcFileSourceSupplier: SourceSupplier<SourceId> {
        override fun locateModule(modulePath: ModulePath): SourceId? {
            val path = modulePathToSourcePath(modulePath)
            return if (Files.exists(path)) SourceId(modulePath) else null
        }

        override fun isAvailable(sourceId: SourceId): Boolean {
            if (sourceId.storage !== this@VcFileStorage) return false
            val path = modulePathToSourcePath(sourceId.modulePath)
            return Files.exists(path)
        }

        override fun loadSource(
                sourceId: SourceId,
                errorReporter: ErrorReporter
        ): SourceSupplier.LoadResult? {
            if (!isAvailable(sourceId)) return null
            val virtualFile = sourceFileForSource(sourceId) ?: return null
            val timeStamp = virtualFile.timeStamp
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
            val result = AbstractTreeFactory.createFromPsiFile(
                    sourceId,
                    psiFile,
                    errorReporter,
                    nameResolver,
                    globalScope,
                    moduleRegistry
            )
            if (virtualFile.timeStamp != timeStamp) return null
            return SourceSupplier.LoadResult.make(result, timeStamp)
        }

        override fun getAvailableVersion(sourceId: SourceId): Long =
                sourceFileForSource(sourceId)?.timeStamp ?: 0

        private fun modulePathToSourcePath(modulePath: ModulePath): Path =
                sourceFile(baseFile(sourceRoot, modulePath))

        private fun sourceFileForSource(sourceId: SourceId): VirtualFile? {
            val path = sourcePathForSource(sourceId)
            return LocalFileSystem.getInstance().findFileByPath(path.toString())
        }

        private fun sourcePathForSource(sourceId: SourceId): Path =
                modulePathToSourcePath(sourceId.modulePath)
    }

    private inner class VcFileCacheStorageSupplier : CacheStorageSupplier<SourceId> {
        override fun getCacheInputStream(sourceId: SourceId): InputStream? {
            cacheRoot ?: return null
            if (sourceId.storage !== this@VcFileStorage) return null
            val file = cacheFileForSource(sourceId)
            return try {
                file?.inputStream
            } catch (ignore: IOException) {
                null
            }
        }

        override fun getCacheOutputStream(sourceId: SourceId): OutputStream? {
            cacheRoot ?: return null
            if (sourceId.storage !== this@VcFileStorage) return null
            val path = cachePathForSource(sourceId)
            return try {
                Files.createDirectories(path.parent)
                Files.newOutputStream(path)
            } catch (ignored: IOException) {
                null
            }
        }

        private fun modulePathToSourcePath(modulePath: ModulePath): Path {
            val base = if (cacheRoot != null) {
                baseFile(cacheRoot, modulePath)
            } else {
                Paths.get("", *modulePath.toArray())
            }
            return cacheFile(base)
        }

        private fun cacheFileForSource(sourceId: SourceId): VirtualFile? {
            val path = cachePathForSource(sourceId)
            return LocalFileSystem.getInstance().findFileByPath(path.toString())
        }

        private fun cachePathForSource(sourceId: SourceId): Path =
                modulePathToSourcePath(sourceId.modulePath)
    }

    companion object {

        fun modulePath(path: Path): ModulePath? {
            assert(!path.isAbsolute)
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
