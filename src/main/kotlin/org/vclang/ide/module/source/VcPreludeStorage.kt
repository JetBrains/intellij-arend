package org.vclang.ide.module.source

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.source.SourceSupplier
import com.jetbrains.jetpad.vclang.module.source.Storage
import com.jetbrains.jetpad.vclang.naming.NameResolver
import org.vclang.lang.VcFileType
import org.vclang.lang.VcLanguage
import org.vclang.lang.core.parser.AbstractTreeFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class VcPreludeStorage(
        private val project: Project,
        private val nameResolver: NameResolver
) : Storage<VcPreludeStorage.SourceId> {
    val preludeSourceId = SourceId()

    override fun getCacheInputStream(sourceId: SourceId): InputStream? {
        if (sourceId !== preludeSourceId) return null
        return CACHE_RESOURCE_PATH.toFile().inputStream()
    }

    override fun getCacheOutputStream(sourceId: SourceId): OutputStream? = null

    override fun locateModule(modulePath: ModulePath): SourceId? =
            if (modulePath.toString() == "::Prelude") preludeSourceId else null

    override fun isAvailable(sourceId: SourceId): Boolean = sourceId === preludeSourceId

    override fun loadSource(
            sourceId: SourceId,
            errorReporter: ErrorReporter
    ): SourceSupplier.LoadResult? {
        if (sourceId !== preludeSourceId) return null
        try {
            val text = String(Files.readAllBytes(SOURCE_RESOURCE_PATH), StandardCharsets.UTF_8)
            val psiFile = PsiFileFactory.getInstance(project)
                    .createFileFromText("Prelude.vc", VcLanguage, text)
            val result = AbstractTreeFactory.createFromPsiFile(
                    preludeSourceId,
                    psiFile,
                    errorReporter,
                    nameResolver
            )
            return SourceSupplier.LoadResult.make(result, 1)
        } catch (e: IOException) {
            throw IllegalStateException(e)
        }
    }

    override fun getAvailableVersion(sourceId: SourceId): Long =
            if (sourceId === preludeSourceId) 0 else 1


    inner class SourceId: com.jetbrains.jetpad.vclang.module.source.SourceId {

        override fun getModulePath(): ModulePath = PRELUDE_MODULE_PATH

        override fun toString(): String = "PRELUDE"
    }

    companion object {
        private val BASE_RESOURCE_PATH: Path =
                Paths.get(VcPreludeStorage::class.java.getResource("/lib").toURI())

        val SOURCE_RESOURCE_PATH: Path =
                BASE_RESOURCE_PATH.resolve(Paths.get("Prelude.${VcFileType.defaultExtension}"))

        val CACHE_RESOURCE_PATH: Path =
                BASE_RESOURCE_PATH.resolve(Paths.get("Prelude.${VcFileType.defaultCacheExtension}"))

        val PRELUDE_MODULE_PATH = ModulePath("Prelude")
    }
}
