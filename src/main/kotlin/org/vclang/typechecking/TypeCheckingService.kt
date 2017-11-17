package org.vclang.typechecking

import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.caching.CacheLoadingException
import com.jetbrains.jetpad.vclang.module.caching.CachePersistenceException
import com.jetbrains.jetpad.vclang.module.caching.ModuleUriProvider
import com.jetbrains.jetpad.vclang.module.caching.SourceVersionTracker
import com.jetbrains.jetpad.vclang.module.caching.sourceless.CacheModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.caching.sourceless.CacheSourceInfoProvider
import com.jetbrains.jetpad.vclang.module.caching.sourceless.SourcelessCacheManager
import com.jetbrains.jetpad.vclang.module.scopeprovider.EmptyModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.source.CompositeSourceSupplier
import com.jetbrains.jetpad.vclang.module.source.CompositeStorage
import com.jetbrains.jetpad.vclang.naming.FullName
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.LexicalScope
import com.jetbrains.jetpad.vclang.term.Prelude
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.term.provider.SourceInfoProvider
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.CachingConcreteProvider
import org.vclang.module.PsiModuleScopeProvider
import org.vclang.module.source.VcFileStorage
import org.vclang.module.source.VcPreludeStorage
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
import org.vclang.psi.ext.fullName_
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.resolving.ResolvingPsiConcreteProvider
import org.vclang.typechecking.execution.TypecheckingEventsProcessor
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths

typealias VcSourceIdT = CompositeSourceSupplier<
        VcFileStorage.SourceId,
        VcPreludeStorage.SourceId
        >.SourceId

interface TypeCheckingService {
    var eventsProcessor: TypecheckingEventsProcessor?

    val moduleScopeProvider: ModuleScopeProvider

    fun typeCheck(modulePath: ModulePath, definitionFullName: String)

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(private val project: Project) : TypeCheckingService {
    override var eventsProcessor: TypecheckingEventsProcessor?
        get() = logger.eventsProcessor
        set(value) {
            logger.eventsProcessor = value
        }

    private val projectStorage = VcFileStorage(project)
    private val preludeStorage = VcPreludeStorage(project)
    private val storage = CompositeStorage<VcFileStorage.SourceId, VcPreludeStorage.SourceId>(
            projectStorage,
            preludeStorage
    )

    private val sourceInfoProvider = CacheSourceInfoProvider(VcSourceInfoProvider())
    private val logger = TypeCheckConsoleLogger(PrettyPrinterConfig.DEFAULT)

    private val cacheManager = SourcelessCacheManager(
        storage,
        VcPersistenceProvider(),
        moduleScopeProvider,
        sourceInfoProvider,
        VcSourceVersionTracker()
    )
    private val typeCheckerState = cacheManager.typecheckerState
    private val dependencyCollector = DependencyCollector(typeCheckerState)

    override val moduleScopeProvider: CacheModuleScopeProvider
        get() {
            val modules = ModuleManager.getInstance(project).modules // TODO[library]
            return CacheModuleScopeProvider(if (modules.isEmpty()) EmptyModuleScopeProvider.INSTANCE else PsiModuleScopeProvider(modules[0]))
        }

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())
        loadPrelude()
    }

    override fun typeCheck(modulePath: ModulePath, definitionFullName: String) {
        ApplicationManager.getApplication().saveAll()

        val sourceId = sourceIdByPath(modulePath) ?: return

        val eventsProcessor = eventsProcessor!!
        if (definitionFullName.isEmpty()) {
            val module = sourceId.source1.module
            eventsProcessor.onSuiteStarted(TestSuiteStartedEvent(module.textRepresentation(), null))
            module.children
                .filterIsInstance<VcStatement>()
                .mapNotNull { it.definition }
                .forEach { eventsProcessor.onTestStarted(TestStartedEvent(it.fullName, null)) }
        } else {
            eventsProcessor.onTestStarted(TestStartedEvent(definitionFullName, null))
        }

        val module = loadSource(sourceId) ?: return

        try {
            cacheManager.loadCache(sourceId)
        } catch (ignored: CacheLoadingException) {
        }

        val concreteProvider = CachingConcreteProvider()
        val testResultReporter = TestResultReporter(eventsProcessor)
        val typeChecking = Typechecking(
                typeCheckerState,
                concreteProvider,
                logger,
                testResultReporter,
                dependencyCollector
        )

        if (definitionFullName.isEmpty()) {
            concreteProvider.setProvider(PsiConcreteProvider(logger))
            DefinitionResolveNameVisitor(logger).resolveGroup(module, CachingScope.make(module.scope), concreteProvider)
            concreteProvider.setProvider(ResolvingPsiConcreteProvider(logger))
            typeChecking.typecheckModules(listOf(module))
            eventsProcessor.onSuiteFinished(TestSuiteFinishedEvent(module.textRepresentation()))

            try {
                cacheManager.persistCache(sourceId)
            } catch (e: CachePersistenceException) {
                e.printStackTrace()
            }
        } else {
            val group = module.findGroupByFullName(definitionFullName.split('.'))
            val ref = checkNotNull(group?.referable) { "Definition $definitionFullName not found" }
            val typechecked = typeCheckerState.getTypechecked(ref)
            if (typechecked == null || typechecked.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                concreteProvider.setProvider(ResolvingPsiConcreteProvider(logger))
                val definition = concreteProvider.getConcrete(ref)
                if (definition is Concrete.Definition) typeChecking.typecheckDefinitions(listOf(definition))
                    else if (definition != null) error(definitionFullName + " is not a definition")
            } else {
                testResultReporter.typecheckingFinished(typechecked)
            }
        }
    }

    private fun loadPrelude() {
        val sourceId = storage.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        val prelude = checkNotNull(loadSource(sourceId)) { "Failed to load prelude" }
        PsiModuleScopeProvider.preludeScope = LexicalScope.opened(prelude)

        try {
            cacheManager.loadCache(sourceId)
        } catch (e: CacheLoadingException) {
            throw IllegalStateException("Prelude cache is not available", e)
        }

        prelude.subgroups
            .mapNotNull { typeCheckerState.getTypechecked(it) }
            .forEach { Prelude.update(it) }
    }

    private fun loadSource(sourceId: VcSourceIdT): VcFile? =
            storage.loadSource(sourceId, logger)?.group as? VcFile

    private fun sourceIdByPath(modulePath: ModulePath): VcSourceIdT? {
        val sourceId = storage.locateModule(modulePath)
        if (storage.isAvailable(sourceId)) {
            return sourceId
        } else {
            error(modulePath.toString() + " is not available")
        }
    }

    internal inner class VcPersistenceProvider : ModuleUriProvider<VcSourceIdT> {
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
                val definition = ancestors.filterIsInstance<VcDefinition>().firstOrNull()
                definition?.let { dependencyCollector.update(definition) }
            }
        }

        private fun processChildren(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                event.child.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement?) {
                        super.visitElement(element)
                        if (element is VcDefinition) {
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
        override fun fullNameFor(definition: GlobalReferable): FullName {
            if (definition !is PsiGlobalReferable) throw IllegalStateException()
            return definition.fullName_
        }

        override fun sourceOf(definition: GlobalReferable): VcSourceIdT? {
            val module = (definition as? PsiElement)?.containingFile?.originalFile as? VcFile ?: error("Invalid definition")
            return if (module.virtualFile.nameWithoutExtension != "Prelude") {
                storage.idFromFirst(projectStorage.locateModule(module))
            } else {
                storage.idFromSecond(preludeStorage.preludeSourceId)
            }
        }
    }
}
