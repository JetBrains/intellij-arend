package org.vclang.ide.typecheck

import com.intellij.openapi.project.Project
import com.jetbrains.jetpad.vclang.frontend.namespace.SimpleDynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.frontend.namespace.SimpleModuleNamespaceProvider
import com.jetbrains.jetpad.vclang.frontend.namespace.SimpleStaticNamespaceProvider
import com.jetbrains.jetpad.vclang.frontend.resolving.NamespaceProviders
import com.jetbrains.jetpad.vclang.module.caching.PersistenceProvider
import com.jetbrains.jetpad.vclang.module.source.CompositeSourceSupplier
import com.jetbrains.jetpad.vclang.module.source.CompositeStorage
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.ide.module.source.VcFileStorage
import org.vclang.ide.module.source.VcPreludeStorage
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.nio.file.Paths

typealias SourceIdT = CompositeSourceSupplier<
            VcFileStorage.SourceId,
            VcPreludeStorage.SourceId
        >.SourceId

class VcTypecheckerFrontend(
        val storageManager: VcStorageManager,
        recompile: Boolean
) : VcBaseTypechekerFrontend<SourceIdT>(storageManager.storage, recompile) {

    init {
        storageManager.nameResolver.setModuleResolver(moduleTracker)
    }

    constructor(project: Project, sourceDir: Path, cacheDir: Path?, recompile: Boolean)
            : this(VcStorageManager(project, sourceDir, cacheDir), recompile)

    override fun displaySource(source: SourceIdT, modulePathOnly: Boolean): String {
        val builder = StringBuilder()
        builder.append(source.modulePath)
        if (!modulePathOnly) {
            if (source.source1 != null) {
                builder.append(" (").append(source.source1).append(")")
            }
        }
        return builder.toString()
    }

    override val staticNsProvider: StaticNamespaceProvider = storageManager.staticNsProvider

    override val dynamicNsProvider: DynamicNamespaceProvider = storageManager.dynamicNsProvider

    override fun createPersistenceProvider(): PersistenceProvider<SourceIdT> =
            MyPersistenceProvider()

    override fun loadPrelude(): Abstract.ClassDefinition {
        val prelude = super.loadPrelude()
        val preludeNamespace = staticNsProvider.forDefinition(prelude)
        storageManager.projectStorage.setPreludeNamespace(preludeNamespace)
        storageManager.moduleNsProvider.registerModule(
                VcPreludeStorage.PRELUDE_MODULE_PATH,
                prelude
        )
        return prelude
    }

    class VcStorageManager(project: Project, projectDir: Path, cacheDir: Path?) {
        val moduleNsProvider = SimpleModuleNamespaceProvider()
        val staticNsProvider = SimpleStaticNamespaceProvider()
        val dynamicNsProvider = SimpleDynamicNamespaceProvider()
        val nameResolver = NameResolver(
                NamespaceProviders(
                        moduleNsProvider,
                        staticNsProvider,
                        dynamicNsProvider
                )
        )

        val projectStorage = VcFileStorage(
                project,
                projectDir,
                cacheDir,
                nameResolver,
                moduleNsProvider
        )
        val preludeStorage = VcPreludeStorage(project, nameResolver)
        val storage = CompositeStorage<VcFileStorage.SourceId, VcPreludeStorage.SourceId>(
                projectStorage,
                preludeStorage
        )

        fun idForProjectSource(sourceId: VcFileStorage.SourceId): SourceIdT =
                storage.idFromFirst(sourceId)

        fun idForPreludeSource(sourceId: VcPreludeStorage.SourceId): SourceIdT =
                storage.idFromSecond(sourceId)
    }

    internal inner class MyPersistenceProvider : PersistenceProvider<SourceIdT> {
        override fun getUri(sourceId: SourceIdT): URI {
            try {
                return if (sourceId.source1 != null) {
                    URI(
                            "file",
                            "",
                            Paths.get("/").resolve(sourceId.source1.relativeFilePath).toUri().path,
                            null,
                            null
                    )
                } else if (sourceId.source2 != null) {
                    URI(
                            "prelude",
                            "",
                            "/",
                            "",
                            null
                    )
                } else {
                    throw IllegalStateException()
                }
            } catch (e: URISyntaxException) {
                throw IllegalStateException()
            }
        }

        override fun getModuleId(sourceUri: URI): SourceIdT? {
            if ("file" == sourceUri.scheme) {
                if (sourceUri.authority != null) return null
                try {
                    val path = Paths.get(URI("file", null, sourceUri.path, null))
                    val modulePath = VcFileStorage.modulePath(path.root.relativize(path))
                    modulePath ?: return null
                    val fileSourceId = storageManager.projectStorage.locateModule(modulePath)
                    return fileSourceId?.let { storageManager.idForProjectSource(it) }
                } catch (e: URISyntaxException) {
                    return null
                } catch (e: NumberFormatException) {
                    return null
                }
            } else if ("prelude" == sourceUri.scheme) {
                if (sourceUri.authority != null || sourceUri.path != "/") return null
                return storageManager.idForPreludeSource(
                        storageManager.preludeStorage.preludeSourceId
                )
            } else {
                return null
            }
        }

        override fun getIdFor(definition: Abstract.Definition): String =
                DefinitionIdsCollector.getIdFor(definition)

        override fun getFromId(sourceId: SourceIdT, id: String): Abstract.Definition? =
                definitionIds[sourceId]?.let { it[id] }
    }
}
