package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.caching.CacheLoadingException
import com.jetbrains.jetpad.vclang.module.caching.CacheManager
import com.jetbrains.jetpad.vclang.module.caching.CachePersistenceException
import com.jetbrains.jetpad.vclang.module.caching.PersistenceProvider
import com.jetbrains.jetpad.vclang.module.caching.SourceVersionTracker
import com.jetbrains.jetpad.vclang.module.source.CompositeSourceSupplier
import com.jetbrains.jetpad.vclang.module.source.CompositeStorage
import com.jetbrains.jetpad.vclang.naming.ModuleResolver
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.resolving.NamespaceProviders
import com.jetbrains.jetpad.vclang.term.Group
import com.jetbrains.jetpad.vclang.term.Prelude
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.provider.SourceInfoProvider
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.CachingConcreteProvider
import org.vclang.VcFileType
import org.vclang.module.source.VcFileStorage
import org.vclang.module.source.VcPreludeStorage
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.resolving.namespace.VcDynamicNamespaceProvider
import org.vclang.resolving.namespace.VcModuleNamespaceProvider
import org.vclang.resolving.namespace.VcStaticNamespaceProvider
import org.vclang.typechecking.execution.TypecheckingEventsProcessor
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.nio.file.Paths

typealias VcSourceIdT = CompositeSourceSupplier<
        VcFileStorage.SourceId,
        VcPreludeStorage.SourceId
        >.SourceId

interface TypeCheckingService {
    var eventsProcessor: TypecheckingEventsProcessor?

    fun typeCheck(modulePath: Path, definitionFullName: String)

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(private val project: Project) : TypeCheckingService {
    override var eventsProcessor: TypecheckingEventsProcessor? // TODO[abstract]: Why nullable?
        get() = logger.eventsProcessor
        set(value) {
            logger.eventsProcessor = value
        }

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
                val module = sourceId?.let { loadSource(it) }
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

            if (definitionFullName.isEmpty()) {
                val module = sourceId.source1.module
                eventsProcessor?.onSuiteStarted(TestSuiteStartedEvent(module.name, null))
                module.children
                    .filterIsInstance<VcStatement>()
                    .mapNotNull { it.definition }
                    .forEach { eventsProcessor?.onTestStarted(TestStartedEvent(it.fullName, null)) }
            } else {
                eventsProcessor?.onTestStarted(TestStartedEvent(definitionFullName, null))
            }

            val module = loadSource(sourceId) ?: return

            try {
                cacheManager.loadCache(sourceId, module)
            } catch (ignored: CacheLoadingException) {
            }

            val concreteProvider = CachingConcreteProvider(PsiConcreteProvider(nameResolver, logger))
            val testResultReporter = TestResultReporter(eventsProcessor!!)
            val typeChecking = Typechecking(
                    typeCheckerState,
                    staticNsProvider,
                    dynamicNsProvider,
                    concreteProvider,
                    logger,
                    testResultReporter,
                    dependencyCollector
            )

            if (definitionFullName.isEmpty()) {
                typeChecking.typecheckModules(listOf(module))
                eventsProcessor!!.onSuiteFinished(TestSuiteFinishedEvent(module.referable.textRepresentation()))
            } else {
                val group = module.findGroupByFullName(definitionFullName.split('.'))
                val ref = checkNotNull(group?.referable) { "Definition $definitionFullName not found" }
                val definition = concreteProvider.getConcrete(ref)
                if (definition is Concrete.Definition) typeChecking.typecheckDefinitions(listOf(definition))
                    else if (definition != null) error(definitionFullName + " is not a definition")
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

    private fun loadPrelude(): Group {
        val sourceId = storage.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        val prelude = checkNotNull(loadSource(sourceId)) { "Failed to load prelude" }
        moduleNsProvider.registerModule(VcPreludeStorage.PRELUDE_MODULE_PATH, prelude)

        val preludeNamespace = staticNsProvider.forReferable(prelude)
        projectStorage.setPreludeNamespace(preludeNamespace)

        try {
            cacheManager.loadCache(sourceId, prelude)
        } catch (e: CacheLoadingException) {
            throw IllegalStateException("Prelude cache is not available", e)
        }

        // TODO[abstract]: We do not need to typecheck prelude
        Typechecking(
                typeCheckerState,
                staticNsProvider,
                dynamicNsProvider,
                PsiConcreteProvider(nameResolver, logger),
                DummyErrorReporter.INSTANCE,
                Prelude.UpdatePreludeReporter(typeCheckerState),
                object : DependencyListener {}
        ).typecheckModules(listOf(prelude))

        return prelude
    }

    private fun loadSource(sourceId: VcSourceIdT): VcFile? =
            storage.loadSource(sourceId, logger)?.group as? VcFile

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
//            logger.report("[Not found] $path is an illegal module path") // TODO: handle error
            return null
        }

        val sourceId = storage.locateModule(modulePath)
        if (!storage.isAvailable(sourceId)) {
//            logger.report("[Not found] $path is not available") // TODO: handle error
            return null
        }

        return sourceId
    }

    companion object {
        private fun collectId(ref: GlobalReferable, map: MutableMap<String, GlobalReferable>) {
            if (ref is PsiGlobalReferable) {
                map[ref.fullName] = ref
            }
        }

        private fun collectIds(group: Group, map: MutableMap<String, GlobalReferable>) {
            collectId(group.referable, map)
            group.subgroups.map { collectIds(it, map) }
            group.constructors.map { collectId(it, map) }
            group.dynamicSubgroups.map { collectIds(it, map) }
            group.fields.map { collectId(it, map) }
        }
    }

    internal inner class VcPersistenceProvider : PersistenceProvider<VcSourceIdT> {
        private val cache = mutableMapOf<ModulePath, Map<String, GlobalReferable>>()

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

        override fun getIdFor(definition: GlobalReferable): String {
            if (definition !is PsiGlobalReferable) throw IllegalStateException()
            return definition.fullName
        }

        override fun getFromId(sourceId: VcSourceIdT, id: String): GlobalReferable? {
            val moduleNamespace = nameResolver.resolveModuleNamespace(sourceId.modulePath)
            val definitions = cache.getOrPut(sourceId.modulePath) {
                val regClass = moduleNamespace.registeredClass
                if (regClass is Group) {
                    val definitions = mutableMapOf<String, GlobalReferable>()
                    collectIds(regClass, definitions)
                    definitions
                } else {
                    mapOf()
                }
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
                val definition = ancestors.filterIsInstance<PsiGlobalReferable>().firstOrNull()
                definition?.let { dependencyCollector.update(definition) }
            }
        }

        private fun processChildren(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                event.child.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement?) {
                        super.visitElement(element)
                        if (element is GlobalReferable) {
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
        override fun nameFor(referable: Referable): String = referable.textRepresentation()

        override fun fullNameFor(definition: GlobalReferable): String {
            if (definition !is PsiGlobalReferable) throw IllegalStateException()
            return definition.fullName
        }

        override fun sourceOf(definition: GlobalReferable): VcSourceIdT? {
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
