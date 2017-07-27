package org.vclang.ide.typecheck

import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.error.ErrorClassifier
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.error.ListErrorReporter
import com.jetbrains.jetpad.vclang.frontend.BaseModuleLoader
import com.jetbrains.jetpad.vclang.frontend.ErrorFormatter
import com.jetbrains.jetpad.vclang.frontend.resolving.HasOpens
import com.jetbrains.jetpad.vclang.frontend.resolving.OneshotSourceInfoCollector
import com.jetbrains.jetpad.vclang.frontend.storage.FileStorage
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.caching.*
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.module.source.SourceSupplier
import com.jetbrains.jetpad.vclang.module.source.Storage
import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import com.jetbrains.jetpad.vclang.term.Prelude
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.error.TypeCheckingError
import com.jetbrains.jetpad.vclang.typechecking.error.local.TerminationCheckError
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyListener
import org.vclang.ide.module.source.VcPreludeStorage
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

abstract class VcBaseTypechekerFrontend<SourceIdT : SourceId>(
        storage: Storage<SourceIdT>,
        recompile: Boolean
) {
    protected val definitionIds = mutableMapOf<SourceIdT, MutableMap<String, Abstract.Definition>>()

    protected val errorReporter = ListErrorReporter()

    // Modules
    protected val moduleTracker = ModuleTracker(storage)
    protected val loadedSources = mutableMapOf<SourceIdT, SourceSupplier.LoadResult>()
    private val requestedSources = LinkedHashSet<SourceIdT>()

    private val srcInfoProvider = moduleTracker.sourceInfoCollector.sourceInfoProvider
    private val cacheManager = CacheManager(
            createPersistenceProvider(),
            storage,
            moduleTracker,
            srcInfoProvider
    )

    private val errorFormatter = ErrorFormatter(srcInfoProvider)

    // Typechecking
    private val useCache = !recompile
    private val state = cacheManager.typecheckerState
    val moduleResults = LinkedHashMap<SourceIdT, ModuleResult>()

    fun run(argFiles: Collection<String>) {
        // Typecheck sources
        argFiles.forEach { requestFileTypechecking(Paths.get(it)) }
        typeCheckSources(requestedSources)
        flushErrors()

        // Output nice per-module typechecking results
        var numWithErrors = 0
        for ((key, result) in moduleResults) {
            if (!requestedSources.contains(key)) {
                val fixedResult = if (result == ModuleResult.OK) ModuleResult.UNKNOWN else result
                reportTypeCheckResult(key, fixedResult)
                if (result == ModuleResult.ERRORS) numWithErrors += 1
            }
        }

        // Explicitly requested sources go last
        for (source in requestedSources) {
            val result = moduleResults[source]
            reportTypeCheckResult(source, result ?: ModuleResult.OK)
            if (result == ModuleResult.ERRORS) numWithErrors += 1
        }

        println("--- Done ---")
        if (numWithErrors > 0) {
            println("Number of modules with errors: $numWithErrors")
        }

        // Persist cache
        for (module in cacheManager.cachedModules) {
            try {
                cacheManager.persistCache(module)
            } catch (e: CachePersistenceException) {
                e.printStackTrace()
            }
        }
    }

    open fun loadPrelude(): Abstract.ClassDefinition {
        val sourceId = moduleTracker.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        val prelude = moduleTracker.load(sourceId)
        assert(errorReporter.errorList.isEmpty())
        if (prelude == null) throw IllegalStateException()

        try {
            cacheManager.loadCache(sourceId, prelude)
        } catch (e: CacheLoadingException) {
            throw IllegalStateException("Prelude cache is not available")
        }

        Typechecking(
                state,
                staticNsProvider,
                dynamicNsProvider,
                HasOpens.GET,
                DummyErrorReporter(),
                Prelude.UpdatePreludeReporter(state),
                object : DependencyListener {}
        ).typecheckModules(listOf(prelude))

        return prelude
    }

    protected abstract val staticNsProvider: StaticNamespaceProvider

    protected abstract val dynamicNsProvider: DynamicNamespaceProvider

    protected abstract fun createPersistenceProvider(): PersistenceProvider<SourceIdT>

    protected abstract fun displaySource(source: SourceIdT, modulePathOnly: Boolean): String

    private fun requestFileTypechecking(path: Path) {
        val fileName = path.fileName.toString()
        if (!fileName.endsWith(FileStorage.EXTENSION)) return
        val name = fileName.substring(0, fileName.length - FileStorage.EXTENSION.length)
        val sourcePath = path.resolveSibling(name)

        val modulePath = FileStorage.modulePath(sourcePath)
        if (modulePath == null) {
            System.err.println("[Not found] $path is an illegal module path")
            return
        }

        val sourceId = moduleTracker.locateModule(modulePath)
        if (!moduleTracker.isAvailable(sourceId)) {
            System.err.println("[Not found] $path is not available")
            return
        }

        requestedSources.add(sourceId)
    }

    private fun typeCheckSources(sources: Set<SourceIdT>) {
        val modulesToTypeCheck = LinkedHashSet<Abstract.ClassDefinition>()
        for (source in sources) {
            val definition: Abstract.ClassDefinition?
            val result = loadedSources[source]
            if (result == null) {
                definition = moduleTracker.load(source)
                if (definition == null) continue

                if (useCache) {
                    try {
                        cacheManager.loadCache(source, definition)
                    } catch (e: CacheLoadingException) {
//                        e.printStackTrace()
                    }
                }

                flushErrors()
            } else {
                definition = result.definition
            }
            modulesToTypeCheck.add(definition)
        }

        println("--- Checking ---")

        class ResultTracker : ErrorClassifier(errorReporter),
                              DependencyListener,
                              TypecheckedReporter {

            override fun reportedError(error: GeneralError) {
                val source = srcInfoProvider.sourceOf(sourceDefinitionOf(error))
                updateSourceResult(source, ModuleResult.ERRORS)
            }

            override fun reportedGoal(error: GeneralError) {
                val source = srcInfoProvider.sourceOf(sourceDefinitionOf(error))
                updateSourceResult(source, ModuleResult.GOALS)
            }

            override fun alreadyTypechecked(definition: Abstract.Definition?) {
                val status = state.getTypechecked(definition).status()
                if (status != Definition.TypeCheckingStatus.NO_ERRORS) {
                    val result = if (status != Definition.TypeCheckingStatus.HAS_ERRORS) {
                        ModuleResult.ERRORS
                    } else {
                        ModuleResult.UNKNOWN
                    }
                    updateSourceResult(srcInfoProvider.sourceOf(definition), result)
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

            private fun updateSourceResult(source: SourceIdT, result: ModuleResult) {
                val prevResult = moduleResults[source]
                if (prevResult == null || result.ordinal > prevResult.ordinal) {
                    moduleResults.put(source, result)
                }
            }
        }

        ResultTracker().let {
            Typechecking(
                    state,
                    staticNsProvider,
                    dynamicNsProvider,
                    HasOpens.GET,
                    it, it, it
            ).typecheckModules(modulesToTypeCheck)
        }
    }

    private fun reportTypeCheckResult(source: SourceIdT, result: ModuleResult) =
            println("[${resultChar(result)}] ${displaySource(source, true)}")

    private fun resultChar(result: ModuleResult): Char = when (result) {
        VcBaseTypechekerFrontend.ModuleResult.NOT_LOADED -> '✗'
        VcBaseTypechekerFrontend.ModuleResult.OK -> ' '
        VcBaseTypechekerFrontend.ModuleResult.GOALS -> '◯'
        VcBaseTypechekerFrontend.ModuleResult.ERRORS -> '✗'
        else -> '·'
    }

    private fun flushErrors() {
        errorReporter.errorList.forEach { println(errorFormatter.printError(it)) }
        errorReporter.errorList.clear()
    }

    enum class ModuleResult { UNKNOWN, OK, GOALS, NOT_LOADED, ERRORS }

    inner class ModuleTracker(
            storage: Storage<SourceIdT>
    ) : BaseModuleLoader<SourceIdT>(storage, errorReporter),
            SourceVersionTracker<SourceIdT> {
        private val defIdCollector = DefinitionIdsCollector()
        internal val sourceInfoCollector = OneshotSourceInfoCollector<SourceIdT>()

        fun isAvailable(sourceId: SourceIdT): Boolean = myStorage.isAvailable(sourceId)

        override fun loadingSucceeded(module: SourceIdT, result: SourceSupplier.LoadResult) {
            defIdCollector.visitClass(
                    result.definition,
                    definitionIds.getOrPut(module) { mutableMapOf() }
            )
            sourceInfoCollector.visitModule(module, result.definition)
            loadedSources.put(module, result)
            println("[Loaded] ${displaySource(module, false)}")
        }

        override fun loadingFailed(module: SourceIdT) {
            moduleResults.put(module, ModuleResult.NOT_LOADED)
            println("[Failed] " + displaySource(module, false))
        }

        override fun load(sourceId: SourceIdT): Abstract.ClassDefinition? {
            assert(!loadedSources.containsKey(sourceId))
            moduleResults[sourceId]?.let {
                assert(it === ModuleResult.NOT_LOADED)
                return null
            }
            return super.load(sourceId)
        }

        override fun load(modulePath: ModulePath): Abstract.ClassDefinition? =
                load(locateModule(modulePath))

        override fun locateModule(modulePath: ModulePath): SourceIdT =
                myStorage.locateModule(modulePath) ?: throw IllegalStateException()

        override fun getCurrentVersion(sourceId: SourceIdT): Long =
                loadedSources[sourceId]?.version ?: 0

        override fun ensureLoaded(sourceId: SourceIdT, version: Long): Boolean {
            val result = loadedSources[sourceId]
                    ?: throw IllegalStateException("Cache manager trying to load a new module")
            return result.version == version
        }
    }

    protected class DefinitionIdsCollector
        : AbstractDefinitionVisitor<MutableMap<String, Abstract.Definition>, Void> {

        override fun visitFunction(
                def: Abstract.FunctionDefinition,
                params: MutableMap<String, Abstract.Definition>
        ): Void? {
            params.put(getIdFor(def), def)
            def.globalDefinitions.forEach { it.accept(this, params) }
            return null
        }

        override fun visitClassField(
                def: Abstract.ClassField,
                params: MutableMap<String, Abstract.Definition>
        ): Void? {
            params.put(getIdFor(def), def)
            return null
        }

        override fun visitData(
                def: Abstract.DataDefinition,
                params: MutableMap<String, Abstract.Definition>
        ): Void? {
            params.put(getIdFor(def), def)
            def.constructorClauses
                    .flatMap { it.constructors }
                    .forEach { it.accept(this, params) }
            return null
        }

        override fun visitConstructor(
                def: Abstract.Constructor,
                params: MutableMap<String, Abstract.Definition>
        ): Void? {
            params.put(getIdFor(def), def)
            return null
        }

        override fun visitClass(
                def: Abstract.ClassDefinition,
                params: MutableMap<String, Abstract.Definition>
        ): Void? {
            params.put(getIdFor(def), def)
            def.globalDefinitions.forEach { it.accept(this, params) }
            def.instanceDefinitions.forEach { it.accept(this, params) }
            def.fields.forEach { it.accept(this, params) }
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
                def: Abstract.ClassViewInstance,
                params: MutableMap<String, Abstract.Definition>
        ): Void? {
            params.put(getIdFor(def), def)
            return null
        }

        companion object {
            fun getIdFor(definition: Abstract.Definition): String {
                return definition.name ?: "abstract-definition"
//                if (definition is Abstract.ReferableSourceNode) {
//                    val position = definition.position
//                    if (position != null) {
//                        return "${position.line};${position.column}"
//                    }
//                }
//                throw IllegalStateException()
            }
        }
    }
}
