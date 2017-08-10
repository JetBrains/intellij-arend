package org.vclang.ide.module.source

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.jetbrains.jetpad.vclang.error.ErrorReporter
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
        private val nameResolver: NameResolver
) : Storage<VcFileStorage.SourceId> {
    private val cacheRoot: Path
    private val sourceSupplier = VcFileSourceSupplier()
    private val cacheStorageSupplier = VcFileCacheStorageSupplier()
    private var globalScope: Scope = EmptyScope()

    init {
        val basePath = Paths.get(project.basePath)
        val relativeModulePath = basePath.relativize(sourceRoot)
        cacheRoot = basePath.resolve(".cache").resolve(relativeModulePath)
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

    fun setPreludeNamespace(namespace: Namespace) {
        globalScope = NamespaceScope(namespace)
    }

    fun sourceFileForSourceId(sourceId: SourceId): VirtualFile? {
        val virtualFile = sourceSupplier.sourceFileForSourceId(sourceId)
//        val fileDocumentManager = FileDocumentManager.getInstance()
//        val document = virtualFile?.let { fileDocumentManager.getDocument(it) }
//        document?.let { fileDocumentManager.saveDocument(it) }
        return virtualFile
    }

    inner class SourceId(
            private val modulePath: ModulePath
    ) : com.jetbrains.jetpad.vclang.module.source.SourceId {
        val storage = this@VcFileStorage
        val relativeFilePath: Path = Paths.get("", *modulePath.toArray())

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
            val virtualFile = sourceFileForSourceId(sourceId) ?: return null
            val timeStamp = virtualFile.timeStamp
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
            val result = AbstractTreeFactory.createFromPsiFile(
                    sourceId,
                    psiFile,
                    errorReporter,
                    nameResolver,
                    globalScope
            )
            if (virtualFile.timeStamp != timeStamp) return null
            return SourceSupplier.LoadResult.make(result, timeStamp)
        }

        fun sourceFileForSourceId(sourceId: SourceId): VirtualFile? {
            val path = sourcePathForSourceId(sourceId)
            return LocalFileSystem.getInstance().findFileByPath(path.toString())
        }

        override fun getAvailableVersion(sourceId: SourceId): Long =
                sourceFileForSourceId(sourceId)?.timeStamp ?: 0

        private fun modulePathToSourcePath(modulePath: ModulePath): Path =
                sourceFile(baseFile(sourceRoot, modulePath))

        fun sourcePathForSourceId(sourceId: SourceId): Path =
                modulePathToSourcePath(sourceId.modulePath)
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
