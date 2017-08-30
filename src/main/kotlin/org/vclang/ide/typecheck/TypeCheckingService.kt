package org.vclang.ide.typecheck

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.frontend.resolving.HasOpens
import com.jetbrains.jetpad.vclang.frontend.resolving.NamespaceProviders
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.caching.*
import com.jetbrains.jetpad.vclang.module.source.CompositeSourceSupplier
import com.jetbrains.jetpad.vclang.module.source.CompositeStorage
import com.jetbrains.jetpad.vclang.naming.ModuleResolver
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import com.jetbrains.jetpad.vclang.term.Prelude
import com.jetbrains.jetpad.vclang.term.SourceInfoProvider
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import org.vclang.ide.module.source.VcFileStorage
import org.vclang.ide.module.source.VcPreludeStorage
import org.vclang.lang.VcFileType
import org.vclang.lang.core.parser.fullName
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.ancestors
import org.vclang.lang.core.psi.ext.adapters.DefinitionAdapter
import org.vclang.lang.core.resolve.namespace.VcDynamicNamespaceProvider
import org.vclang.lang.core.resolve.namespace.VcModuleNamespaceProvider
import org.vclang.lang.core.resolve.namespace.VcStaticNamespaceProvider
import org.vclang.lang.core.stubs.VcDefinitionStub
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.nio.file.Paths

typealias VcSourceIdT = CompositeSourceSupplier<
        VcFileStorage.SourceId,
        VcPreludeStorage.SourceId
        >.SourceId

interface TypeCheckingService {
    var console: ConsoleView?
    var eventsProcessor: TypeCheckingEventsProcessor?

    fun typeCheck(modulePath: Path, definitionFullName: String)

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(private val project: Project): TypeCheckingService {
    override var console: ConsoleView?
        get() = logger.console
        set(value) {
            logger.console = value
        }
    override var eventsProcessor: TypeCheckingEventsProcessor? = null

    private val moduleNsProvider = VcModuleNamespaceProvider()
    private val staticNsProvider = VcStaticNamespaceProvider
    private val dynamicNsProvider = VcDynamicNamespaceProvider
    private val nameResolver: NameResolver = NameResolver(
            NamespaceProviders(
                    moduleNsProvider,
                    staticNsProvider,
                    dynamicNsProvider
            ),
            ModuleResolver { modulePath ->
                val sourceId = storage.locateModule(modulePath)
                val module = loadSource(sourceId)
                module?.let { moduleNsProvider.registerModule(modulePath, it) }
                module
            }
    )

    private val projectStorage = VcFileStorage(project, nameResolver)
    private val preludeStorage = VcPreludeStorage(project, nameResolver)
    private val storage = CompositeStorage<VcFileStorage.SourceId, VcPreludeStorage.SourceId>(
            projectStorage,
            preludeStorage
    )

    private val sourceInfoProvider = VcSourceInfoProvider()
    private val logger = TypeCheckConsoleLogger(sourceInfoProvider)

    private val persistenceProvider = VcPersistenceProvider()
    private val cacheManager = CacheManager(
        persistenceProvider,
        storage,
        VcSourceVersionTracker(),
        sourceInfoProvider
    )
    private val typeCheckerState = cacheManager.typecheckerState
    private val dependencyCollector = DependencyCollector(typeCheckerState)

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())
        loadPrelude()
    }

    override fun typeCheck(modulePath: Path, definitionFullName: String) {
        try {
            ApplicationManager.getApplication().saveAll()

            val sourceId = sourceIdByPath(modulePath) ?: return
            val module = loadSource(sourceId) ?: return

            try {
                cacheManager.loadCache(sourceId, module)
            } catch (ignored: CacheLoadingException) {
            }

            val typeChecking = TypeCheckingAdapter(
                    typeCheckerState,
                    staticNsProvider,
                    dynamicNsProvider,
                    HasOpens.GET,
                    logger,
                    dependencyCollector,
                    eventsProcessor!!
            )

            if (definitionFullName.isEmpty()) {
                typeChecking.typeCheckModule(module)
            } else {
                val definition = module.findDefinitionByFullName(definitionFullName)
                checkNotNull(definition) { "Definition $definitionFullName not found" }.let {
                    typeChecking.typeCheckDefinition(it)
                }
            }

            cacheManager.cachedModules
                    .filter { it.actualSourceId !== preludeStorage.preludeSourceId }
                    .forEach {
                        try {
                            cacheManager.persistCache(it)
                        } catch (e: CachePersistenceException) {
                            e.printStackTrace()
                        }
                    }
        } finally {
            moduleNsProvider.unregisterAllModules()
            persistenceProvider.clear()
        }
    }

    private fun loadPrelude(): Abstract.ClassDefinition {
        val sourceId = storage.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        val prelude = checkNotNull(loadSource(sourceId)) { "Failed to load prelude" }
        moduleNsProvider.registerModule(VcPreludeStorage.PRELUDE_MODULE_PATH, prelude)

        val preludeNamespace = staticNsProvider.forDefinition(prelude)
        projectStorage.setPreludeNamespace(preludeNamespace)

        try {
            cacheManager.loadCache(sourceId, prelude)
        } catch (e: CacheLoadingException) {
            throw IllegalStateException("Prelude cache is not available", e)
        }

        Typechecking(
                typeCheckerState,
                staticNsProvider,
                dynamicNsProvider,
                HasOpens.GET,
                DummyErrorReporter(),
                Prelude.UpdatePreludeReporter(typeCheckerState),
                object : DependencyListener {}
        ).typecheckModules(listOf(prelude))

        return prelude
    }

    private fun loadSource(sourceId: VcSourceIdT): VcFile? =
            storage.loadSource(sourceId, logger)?.definition as? VcFile

    private fun sourceIdByPath(path: Path): VcSourceIdT? {
        val base = Paths.get(project.basePath)
        val name = run {
            val fileName = path.fileName.toString()
            if (!fileName.endsWith('.' + VcFileType.defaultExtension)) {
                return null
            }
            fileName.removeSuffix('.' + VcFileType.defaultExtension)
        }
        val sourcePath = base.relativize(path.resolveSibling(name))

        val modulePath = VcFileStorage.modulePath(sourcePath)
        if (modulePath == null) {
            logger.reportError("[Not found] $path is an illegal module path")
            return null
        }

        val sourceId = storage.locateModule(modulePath)
        if (!storage.isAvailable(sourceId)) {
            logger.reportError("[Not found] $path is not available")
            return null
        }

        return sourceId
    }

    internal inner class VcPersistenceProvider : PersistenceProvider<VcSourceIdT> {
        private val cache = mutableMapOf<ModulePath, MutableMap<String, Abstract.Definition>>()

        override fun getUri(sourceId: VcSourceIdT): URI {
            return when {
                sourceId.source1 != null -> URI(
                        "file",
                        "",
                        Paths.get("/", *sourceId.source1.modulePath.toArray()).toString(),
                        null,
                        null
                )
                sourceId.source2 != null -> URI(
                        "prelude",
                        "",
                        "/",
                        "",
                        null
                )
                else -> error("Invalid sourceId")
            }
        }

        override fun getModuleId(sourceUri: URI): VcSourceIdT? {
            if (sourceUri.authority != null) return null
            when (sourceUri.scheme) {
                "file" -> {
                    try {
                        val path = Paths.get(URI("file", null, sourceUri.path, null))
                        val modulePath = VcFileStorage.modulePath(path.root.relativize(path))
                        val fileSourceId = modulePath?.let { projectStorage.locateModule(it) }
                        return fileSourceId?.let { storage.idFromFirst(it) }
                    } catch (e: URISyntaxException) {
                    } catch (e: NumberFormatException) {
                    }
                }
                "prelude" -> {
                    if (sourceUri.path == "/") {
                        return storage.idFromSecond(preludeStorage.preludeSourceId)
                    }
                }
            }
            return null
        }

        override fun getIdFor(definition: Abstract.Definition): String = definition.fullName

        override fun getFromId(sourceId: VcSourceIdT, id: String): Abstract.Definition? {
            val moduleNamespace = nameResolver.resolveModuleNamespace(sourceId.modulePath)
            val definitions = cache.getOrPut(sourceId.modulePath) {
                val definitions = mutableMapOf<String, Abstract.Definition>()
                DefinitionIdsCollector.visitClass(moduleNamespace.registeredClass, definitions)
                definitions
            }
            return definitions[id]
        }

        fun clear() = cache.clear()
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {

        override fun beforeChildAddition(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            processChildren(event)
            processParent(event)
        }

        private fun processParent(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                val ancestors = event.parent.ancestors
                val definition = ancestors.filterIsInstance<Abstract.Definition>().firstOrNull()
                definition?.let { dependencyCollector.update(definition) }
            }
        }

        private fun processChildren(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                event.child.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement?) {
                        super.visitElement(element)
                        if (element is Abstract.Definition) {
                            dependencyCollector.update(element)
                        }
                    }
                })
            }
        }
    }

    private inner class VcSourceVersionTracker : SourceVersionTracker<VcSourceIdT> {

        override fun getCurrentVersion(sourceId: VcSourceIdT): Long =
                storage.getAvailableVersion(sourceId)

        override fun ensureLoaded(sourceId: VcSourceIdT, version: Long): Boolean =
                getCurrentVersion(sourceId) == version
    }

    private inner class VcSourceInfoProvider : SourceInfoProvider<VcSourceIdT> {

        override fun positionOf(sourceNode: Abstract.SourceNode?): String? = null

        override fun moduleOf(sourceNode: Abstract.SourceNode?): String? {
            @Suppress("UNCHECKED_CAST")
            val definition = sourceNode as? DefinitionAdapter<VcDefinitionStub<VcDefinition>>
            val module = definition?.containingFile?.originalFile as? VcFile
            return module?.relativeModulePath?.toString()
        }

        override fun nameFor(definition: Abstract.Definition): String = definition.fullName

        override fun sourceOf(definition: Abstract.Definition): VcSourceIdT? {
            val module = when (definition) {
                is VcDefinition -> definition.containingFile.originalFile as VcFile
                is VcFile -> definition
                else -> error("Invalid definition")
            }
            return if (module.virtualFile.nameWithoutExtension != "Prelude") {
                storage.idFromFirst(projectStorage.locateModule(module))
            } else {
                storage.idFromSecond(preludeStorage.preludeSourceId)
            }
        }
    }
}

internal object DefinitionIdsCollector
    : AbstractDefinitionVisitor<MutableMap<String, Abstract.Definition>, Void> {

    override fun visitFunction(
            definition: Abstract.FunctionDefinition,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullName, definition)
        definition.globalDefinitions.forEach { it.accept(this, params) }
        return null
    }

    override fun visitClassField(
            definition: Abstract.ClassField,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullName, definition)
        return null
    }

    override fun visitData(
            definition: Abstract.DataDefinition,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullName, definition)
        definition.constructorClauses
                .flatMap { it.constructors }
                .forEach { it.accept(this, params) }
        return null
    }

    override fun visitConstructor(
            definition: Abstract.Constructor,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullName, definition)
        return null
    }

    override fun visitClass(
            definition: Abstract.ClassDefinition,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullName, definition)
        definition.globalDefinitions.forEach { it.accept(this, params) }
        definition.instanceDefinitions.forEach { it.accept(this, params) }
        definition.fields.forEach { it.accept(this, params) }
        return null
    }

    override fun visitImplement(
            definition: Abstract.Implementation,
            params: MutableMap<String, Abstract.Definition>
    ): Void? = null

    override fun visitClassView(
            definition: Abstract.ClassView,
            params: MutableMap<String, Abstract.Definition>
    ): Void? = null

    override fun visitClassViewField(
            definition: Abstract.ClassViewField,
            params: MutableMap<String, Abstract.Definition>
    ): Void? = null

    override fun visitClassViewInstance(
            definition: Abstract.ClassViewInstance,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullName, definition)
        return null
    }
}
