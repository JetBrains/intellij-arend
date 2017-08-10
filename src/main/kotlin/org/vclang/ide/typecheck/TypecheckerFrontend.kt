package org.vclang.ide.typecheck

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.error.ErrorClassifier
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.ListErrorReporter
import com.jetbrains.jetpad.vclang.frontend.ErrorFormatter
import com.jetbrains.jetpad.vclang.frontend.resolving.HasOpens
import com.jetbrains.jetpad.vclang.frontend.resolving.NamespaceProviders
import com.jetbrains.jetpad.vclang.module.caching.*
import com.jetbrains.jetpad.vclang.module.source.CompositeSourceSupplier
import com.jetbrains.jetpad.vclang.module.source.CompositeStorage
import com.jetbrains.jetpad.vclang.naming.ModuleResolver
import com.jetbrains.jetpad.vclang.naming.NameResolver
import com.jetbrains.jetpad.vclang.term.*
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.error.TypeCheckingError
import com.jetbrains.jetpad.vclang.typechecking.error.local.TerminationCheckError
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import org.vclang.ide.module.source.VcFileStorage
import org.vclang.ide.module.source.VcPreludeStorage
import org.vclang.lang.VcFileType
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.psi.VcDefinition
import org.vclang.lang.core.psi.VcFile
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

class TypecheckerFrontend(project: Project, sourceRoot: Path) {
    var console: ConsoleView? = null

    private val errorReporter = ListErrorReporter()
    private val errorFormatter = ErrorFormatter(SourceInfoProvider.TRIVIAL)

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
                val definition = storage.loadSource(sourceId, errorReporter)?.definition
                flushErrors()
                definition?.let { moduleNsProvider.registerModule(modulePath, it) }
                definition
            }
    )
    private val projectStorage = VcFileStorage(project, sourceRoot, nameResolver)
    private val preludeStorage = VcPreludeStorage(project, nameResolver)
    private val storage = CompositeStorage<VcFileStorage.SourceId, VcPreludeStorage.SourceId>(
            projectStorage,
            preludeStorage
    )
    private val definitionLocator = VcDefinitionLocator(sourceRoot)
    private val cacheManager = CacheManager(
            VcPersistenceProvider(),
            storage,
            VcSourceVersionTracker(),
            definitionLocator
    )
    private val typecheckerState = cacheManager.typecheckerState
    private val dependencyCollector = DependencyCollector(typecheckerState)

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(TypechekerPsiTreeChangeListener())
    }

    fun typecheck(modulePath: String, definitionName: String) {
        moduleNsProvider.unregisterAllModules()
        errorReporter.errorList.clear()
        loadPrelude()

        val requestedSource = sourceIdByPath(Paths.get(modulePath)) ?: return
        val result = typeCheckSource(requestedSource)
        flushErrors()
        reportTypeCheckResult(requestedSource, result)
        console?.print("--- Done ---", ConsoleViewContentType.SYSTEM_OUTPUT)

        for (module in cacheManager.cachedModules) {
            if (module.actualSourceId == preludeStorage.preludeSourceId) continue
            try {
                cacheManager.persistCache(module)
            } catch (e: CachePersistenceException) {
                e.printStackTrace()
            }
        }
    }

    fun loadPrelude(): Abstract.ClassDefinition {
        val sourceId = storage.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        val prelude = storage.loadSource(sourceId, errorReporter)?.definition
        assert(errorReporter.errorList.isEmpty())
        if (prelude !is VcFile) throw IllegalStateException()

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

    private fun sourceIdByPath(path: Path): VcSourceIdT? {
        val fileName = path.fileName.toString()
        if (!fileName.endsWith(VcFileType.defaultExtension)) {
            return null
        }
        val name = fileName.substring(0, fileName.lastIndexOf('.'))
        val sourcePath = path.resolveSibling(name)

        val modulePath = VcFileStorage.modulePath(sourcePath)
        if (modulePath == null) {
            System.err.println("[Not found] $path is an illegal module path")
            return null
        }

        val sourceId = storage.locateModule(modulePath)
        if (!storage.isAvailable(sourceId)) {
            System.err.println("[Not found] $path is not available")
            return null
        }

        return sourceId
    }

    private fun typeCheckSource(source: VcSourceIdT): ModuleResult {
        val definition = storage.loadSource(source, errorReporter)?.definition
        if (definition != null) {
            try {
                cacheManager.loadCache(source, definition)
            } catch (ignored: CacheLoadingException) {
            }
        }

        console?.print("--- Checking ---", ConsoleViewContentType.SYSTEM_OUTPUT)

        definition ?: return ModuleResult.ERRORS

        class ResultTracker : ErrorClassifier(errorReporter),
                              DependencyListener,
                              TypecheckedReporter {

            override fun reportedError(error: GeneralError) {
                val sourceId = definitionLocator.sourceOf(sourceDefinitionOf(error))
                sourceId?.let { updateSourceResult(it, ModuleResult.ERRORS) }
            }

            override fun reportedGoal(error: GeneralError) {
                val sourceId = definitionLocator.sourceOf(sourceDefinitionOf(error))
                sourceId?.let { updateSourceResult(it, ModuleResult.GOALS) }
            }

            override fun alreadyTypechecked(definition: Abstract.Definition) {
                val status = typecheckerState.getTypechecked(definition).status()
                if (status != Definition.TypeCheckingStatus.NO_ERRORS) {
                    val result = if (status != Definition.TypeCheckingStatus.HAS_ERRORS) {
                        ModuleResult.ERRORS
                    } else {
                        ModuleResult.UNKNOWN
                    }
                    val sourceId = definitionLocator.sourceOf(definition)
                    sourceId?.let { updateSourceResult(it, result) }
                }
            }

            override fun typecheckingFailed(definition: Abstract.Definition?) = flushErrors()

            private fun sourceDefinitionOf(error: GeneralError): Abstract.Definition {
                return when (error) {
                    is TypeCheckingError -> error.definition
                    is TerminationCheckError -> error.definition
                    else -> throw IllegalStateException("Non-typechecking error " +
                                                        "reported to typechecking reporter")
                }
            }

            private fun updateSourceResult(source: VcSourceIdT, result: ModuleResult) {
//                val prevResult = moduleResults[source]
//                if (prevResult == null || result.ordinal > prevResult.ordinal) {
//                    moduleResults.put(source, result)
//                }
            }
        }

        ResultTracker().let {
            Typechecking(
                    typecheckerState,
                    staticNsProvider,
                    dynamicNsProvider,
                    HasOpens.GET,
                    it,
                    it,
                    dependencyCollector
            ).typecheckModules(listOf(definition))
        }

        return ModuleResult.OK
    }

    private fun displaySource(source: VcSourceIdT, modulePathOnly: Boolean): String {
        val builder = StringBuilder()
        builder.append(source.modulePath)
        if (!modulePathOnly) {
            if (source.source1 != null) {
                builder.append(" (").append(source.source1).append(")")
            }
        }
        return builder.toString()
    }

    private fun reportTypeCheckResult(source: VcSourceIdT, result: ModuleResult) =
            println("[${result.glyph}] ${displaySource(source, true)}")

    private fun flushErrors() {
        errorReporter.errorList.forEach { println(errorFormatter.printError(it)) }
        errorReporter.errorList.clear()
    }

    enum class ModuleResult(val glyph: Char) {
        UNKNOWN('·'),
        OK(' '),
        GOALS('◯'),
        NOT_LOADED('✗'),
        ERRORS('✗')
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

        override fun getIdFor(definition: Abstract.Definition): String =
                definition.fullyQualifiedName

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
                if (definition != null && definitionLocator.sourceOf(definition) != null) {
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

    private inner class VcDefinitionLocator(private val sourceDir: Path)
        : DefinitionLocator<VcSourceIdT> {
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

internal object DefinitionIdsCollector
    : AbstractDefinitionVisitor<MutableMap<String, Abstract.Definition>, Void> {

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
            def: Abstract.Implementation,
            params: MutableMap<String, Abstract.Definition>
    ): Void? = null

    override fun visitClassView(
            def: Abstract.ClassView,
            params: MutableMap<String, Abstract.Definition>
    ): Void? = null

    override fun visitClassViewField(
            def: Abstract.ClassViewField,
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
