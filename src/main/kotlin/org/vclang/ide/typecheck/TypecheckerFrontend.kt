package org.vclang.ide.typecheck

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.frontend.resolving.HasOpens
import com.jetbrains.jetpad.vclang.frontend.resolving.NamespaceProviders
import com.jetbrains.jetpad.vclang.module.caching.CacheLoadingException
import com.jetbrains.jetpad.vclang.module.caching.CacheManager
import com.jetbrains.jetpad.vclang.module.caching.PersistenceProvider
import com.jetbrains.jetpad.vclang.module.caching.SourceVersionTracker
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
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcFile
import org.vclang.lang.core.psi.ext.adapters.DefinitionAdapter
import org.vclang.lang.core.resolve.namespace.VcDynamicNamespaceProvider
import org.vclang.lang.core.resolve.namespace.VcModuleNamespaceProvider
import org.vclang.lang.core.resolve.namespace.VcStaticNamespaceProvider
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.nio.file.Paths

typealias VcSourceIdT = CompositeSourceSupplier<
            VcFileStorage.SourceId,
            VcPreludeStorage.SourceId
        >.SourceId

class TypecheckerFrontend(project: Project, val sourceRootPath: Path) {
    private val sourceInfoProvider = VcSourceInfoProvider(sourceRootPath)

    private val logger = TypecheckConsoleLogger(sourceInfoProvider)
    var console: ConsoleView?
        get() = logger.console
        set(value) { logger.console = value }
    var eventsProcessor: TypecheckEventsProcessor? = null
    var hasErrors = false

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
                val definition = loadSource(sourceId)
                logger.hasErrors = false
                definition?.let { moduleNsProvider.registerModule(modulePath, it) }
                definition
            }
    )

    private val projectStorage = VcFileStorage(project, sourceRootPath, nameResolver)
    private val preludeStorage = VcPreludeStorage(project, nameResolver)
    private val storage = CompositeStorage<VcFileStorage.SourceId, VcPreludeStorage.SourceId>(
            projectStorage,
            preludeStorage
    )

    private val cacheManager = CacheManager(
            VcPersistenceProvider(),
            storage,
            VcSourceVersionTracker(),
            sourceInfoProvider
    )
    private val typecheckerState = cacheManager.typecheckerState
    private val dependencyCollector = DependencyCollector(typecheckerState)

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(TypechekerPsiTreeChangeListener())
    }

    fun typecheck(modulePath: Path, definitionName: String) {
        try {
            loadPrelude()

            val sourceId = sourceIdByPath(modulePath) ?: return
            val module = loadSource(sourceId) ?: return
            try {
                cacheManager.loadCache(sourceId, module)
            } catch (ignored: CacheLoadingException) {
            }

            val service = TypecheckingAdapter(
                    typecheckerState,
                    staticNsProvider,
                    dynamicNsProvider,
                    HasOpens.GET,
                    logger,
                    dependencyCollector,
                    eventsProcessor!!
            )

            if (definitionName.isEmpty()) {
                service.typecheckModule(module)
            } else {
                val definition = module.findDefinitionByFullName(definitionName)
                definition ?: throw IllegalStateException()
                service.typecheckDefinition(definition)
            }

//            for (module in cacheManager.cachedModules) {
//                if (module.actualSourceId == preludeStorage.preludeSourceId) continue
//                try {
//                    cacheManager.persistCache(module)
//                } catch (e: CachePersistenceException) {
//                    e.printStackTrace()
//                }
//            }
        } finally {
            hasErrors = logger.hasErrors
            logger.hasErrors = false
            moduleNsProvider.unregisterAllModules()
        }
    }

    fun loadPrelude(): Abstract.ClassDefinition {
        val sourceId = storage.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        val prelude = loadSource(sourceId) ?: throw IllegalStateException()

        moduleNsProvider.registerModule(VcPreludeStorage.PRELUDE_MODULE_PATH, prelude)
        val preludeNamespace = staticNsProvider.forDefinition(prelude)
        projectStorage.setPreludeNamespace(preludeNamespace)

        try {
            cacheManager.loadCache(sourceId, prelude)
        } catch (e: CacheLoadingException) {
            throw IllegalStateException("Prelude cache is not available")
        }

        Typechecking(
                typecheckerState,
                staticNsProvider,
                dynamicNsProvider,
                HasOpens.GET,
                DummyErrorReporter(),
                Prelude.UpdatePreludeReporter(typecheckerState),
                object : DependencyListener {}
        ).typecheckModules(listOf(prelude))

        return prelude
    }

    private fun loadSource(sourceId: VcSourceIdT): VcFile? =
            storage.loadSource(sourceId, logger)?.definition as? VcFile

    private fun sourceIdByPath(path: Path): VcSourceIdT? {
        val fileName = path.fileName.toString()
        if (!fileName.endsWith(VcFileType.defaultExtension)) {
            return null
        }
        val name = fileName.substring(0, fileName.lastIndexOf('.'))
        val sourcePath = path.resolveSibling(name)

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
        override fun getUri(sourceId: VcSourceIdT): URI {
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

        override fun getModuleId(sourceUri: URI): VcSourceIdT? {
            if (sourceUri.scheme == "file") {
                if (sourceUri.authority != null) return null
                try {
                    val path = Paths.get(URI("file", null, sourceUri.path, null))
                    val modulePath = VcFileStorage.modulePath(path.root.relativize(path))
                    modulePath ?: return null
                    val fileSourceId = projectStorage.locateModule(modulePath)
                    return fileSourceId?.let { storage.idFromFirst(it) }
                } catch (e: URISyntaxException) {
                    return null
                } catch (e: NumberFormatException) {
                    return null
                }
            } else if (sourceUri.scheme == "prelude") {
                if (sourceUri.authority != null || sourceUri.path != "/") return null
                return storage.idFromSecond(preludeStorage.preludeSourceId)
            } else {
                return null
            }
        }

        override fun getIdFor(definition: Abstract.Definition): String = definition.fullyQualifiedName

        override fun getFromId(sourceId: VcSourceIdT, id: String): Abstract.Definition? {
            val moduleNamespace = nameResolver.resolveModuleNamespace(sourceId.modulePath)
            val definitions = mutableMapOf<String, Abstract.Definition>()
            DefinitionIdsCollector.visitClass(moduleNamespace.registeredClass, definitions)
            return definitions[id]
        }
    }

    private inner class TypechekerPsiTreeChangeListener : PsiTreeChangeAdapter() {

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
                val ancestors = generateSequence(event.parent) { it.parent }
                val definition = ancestors.filterIsInstance<Abstract.Definition>().firstOrNull()
                if (definition != null && sourceInfoProvider.sourceOf(definition) != null) {
                    dependencyCollector.update(definition)
                }
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

        override fun getCurrentVersion(sourceId: VcSourceIdT): Long {
            return if (sourceId.source1 != null) {
                val virtualFile = projectStorage.sourceFileForSourceId(sourceId.source1)
                virtualFile?.timeStamp ?: 0
            } else if (sourceId.source2 == preludeStorage.preludeSourceId) {
                1
            } else {
                0
            }
        }

        override fun ensureLoaded(sourceId: VcSourceIdT, version: Long): Boolean =
                version == getCurrentVersion(sourceId)
    }

    private inner class VcSourceInfoProvider(private val sourceDir: Path)
        : SourceInfoProvider<VcSourceIdT> {

        override fun positionOf(sourceNode: Abstract.SourceNode?): String? = null

        override fun moduleOf(sourceNode: Abstract.SourceNode?): String? {
            return if (sourceNode is DefinitionAdapter) {
                val moduleFile = sourceNode.containingFile.originalFile as VcFile
                return moduleFile.modulePath.toString()
            } else {
                null
            }
        }

        override fun nameFor(definition: Abstract.Definition): String =
                definition.fullyQualifiedName

        override fun sourceOf(definition: Abstract.Definition): VcSourceIdT? {
            val classDefinition = if (definition is VcDefinition) {
                definition.containingFile.originalFile as VcFile
            } else {
                definition as VcFile
            }

            val virtualFile = classDefinition.virtualFile
            return if (virtualFile.nameWithoutExtension != "Prelude") {
                val filePath = sourceDir.relativize(Paths.get(virtualFile.path))
                sourceIdByPath(filePath)
            } else {
                storage.idFromSecond(preludeStorage.preludeSourceId)
            }
        }
    }
}

internal object DefinitionIdsCollector : AbstractDefinitionVisitor<MutableMap<String, Abstract.Definition>, Void> {

    override fun visitFunction(
            definition: Abstract.FunctionDefinition,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullyQualifiedName, definition)
        definition.globalDefinitions.forEach { it.accept(this, params) }
        return null
    }

    override fun visitClassField(
            definition: Abstract.ClassField,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullyQualifiedName, definition)
        return null
    }

    override fun visitData(
            definition: Abstract.DataDefinition,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullyQualifiedName, definition)
        definition.constructorClauses
                .flatMap { it.constructors }
                .forEach { it.accept(this, params) }
        return null
    }

    override fun visitConstructor(
            definition: Abstract.Constructor,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullyQualifiedName, definition)
        return null
    }

    override fun visitClass(
            definition: Abstract.ClassDefinition,
            params: MutableMap<String, Abstract.Definition>
    ): Void? {
        params.put(definition.fullyQualifiedName, definition)
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
        params.put(definition.fullyQualifiedName, definition)
        return null
    }
}
