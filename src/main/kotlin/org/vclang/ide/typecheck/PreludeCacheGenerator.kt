package org.vclang.ide.typecheck

import com.jetbrains.jetpad.vclang.module.caching.CacheStorageSupplier
import com.jetbrains.jetpad.vclang.module.caching.PersistenceProvider
import com.jetbrains.jetpad.vclang.module.caching.SourceVersionTracker
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.DefinitionLocator
import org.vclang.ide.module.source.VcPreludeStorage
import org.vclang.lang.core.parser.fullName
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

object PreludeCacheGenerator {

    internal class PreludeBuildCacheSupplier(private val targetPath: Path)
        : CacheStorageSupplier<VcPreludeStorage.SourceId> {

        override fun getCacheInputStream(sourceId: VcPreludeStorage.SourceId): InputStream =
                throw UnsupportedOperationException()

        override fun getCacheOutputStream(sourceId: VcPreludeStorage.SourceId): OutputStream {
            val path = targetPath.resolve(VcPreludeStorage.CACHE_RESOURCE_PATH)
            Files.createDirectories(path.parent)
            return Files.newOutputStream(path)
        }
    }

    internal class PreludeDefLocator(private val preludeSourceId: VcPreludeStorage.SourceId)
        : DefinitionLocator<VcPreludeStorage.SourceId> {
        override fun sourceOf(definition: Abstract.Definition): VcPreludeStorage.SourceId =
                preludeSourceId
    }

    internal class PreludePersistenceProvider : PersistenceProvider<VcPreludeStorage.SourceId> {
        override fun getUri(sourceId: VcPreludeStorage.SourceId): URI =
                throw UnsupportedOperationException()

        override fun getModuleId(sourceUrl: URI): VcPreludeStorage.SourceId =
                throw UnsupportedOperationException()

        override fun getIdFor(definition: Abstract.Definition): String = definition.fullName

        override fun getFromId(
                sourceId: VcPreludeStorage.SourceId,
                id: String
        ): Abstract.Definition = throw UnsupportedOperationException()
    }

    internal class PreludeVersionTracker : SourceVersionTracker<VcPreludeStorage.SourceId> {

        override fun getCurrentVersion(sourceId: VcPreludeStorage.SourceId): Long = 1

        override fun ensureLoaded(sourceId: VcPreludeStorage.SourceId, version: Long): Boolean =
                throw UnsupportedOperationException()
    }
}
