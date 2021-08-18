package org.arend.typechecking

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestModeFlags
import com.intellij.util.containers.MultiMap
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.*
import org.arend.core.expr.visitor.CompareVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.definition.CoreDefinition
import org.arend.ext.core.definition.CoreFunctionDefinition
import org.arend.ext.core.ops.CMP
import org.arend.ext.instance.InstanceSearchParameters
import org.arend.ext.instance.SubclassSearchParameters
import org.arend.ext.module.LongName
import org.arend.ext.typechecking.DefinitionListener
import org.arend.extImpl.ArendDependencyProviderImpl
import org.arend.extImpl.DefinitionRequester
import org.arend.library.Library
import org.arend.library.LibraryManager
import org.arend.module.*
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.MetaAdapter
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.ext.impl.fillAdditionalNames
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.ArendResolveCache
import org.arend.resolving.PsiConcreteProvider
import org.arend.settings.ArendProjectSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.instance.pool.RecursiveInstanceHoleExpression
import org.arend.typechecking.instance.provider.InstanceProviderSet
import org.arend.typechecking.instance.provider.SimpleInstanceProvider
import org.arend.typechecking.order.DFS
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.typechecking.visitor.CheckTypeVisitor
import org.arend.util.FullName
import org.arend.util.Range
import org.arend.util.Version
import org.arend.util.refreshLibrariesDirectory
import org.arend.yaml.YAMLFileListener
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

class TypeCheckingService(val project: Project) : ArendDefinitionChangeListener, DefinitionRequester, DefinitionListener {
    val dependencyListener = DependencyCollector()
    private val libraryErrorReporter = NotificationErrorReporter(project)
    val libraryManager = object : LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter, this, this) {
        override fun showLibraryNotFoundError(libraryName: String) {
            if (libraryName == AREND_LIB) {
                showDownloadNotification(project, false)
            } else {
                super.showLibraryNotFoundError(libraryName)
            }
        }

        override fun showIncorrectLanguageVersionError(libraryName: String?, range: Range<Version>?) {
            if (libraryName == AREND_LIB) {
                showDownloadNotification(project, true)
            } else {
                super.showIncorrectLanguageVersionError(libraryName, range)
            }
        }

        override fun getRegisteredLibraries(): MutableCollection<out Library> {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                val libs = LibraryManagerTestingOptions.getRegisteredLibraries()
                if (libs != null) {
                    return libs
                }
            }
            return super.getRegisteredLibraries()
        }
    }

    private val extensionDefinitions = HashMap<TCDefReferable, Library>()

    private val additionalNames = HashMap<String, ArrayList<PsiLocatedReferable>>()

    private val instances = MultiMap.createConcurrent<TCDefReferable, TCDefReferable>()

    val tcRefMaps = ConcurrentHashMap<ModuleLocation, HashMap<LongName, TCReferable>>()

    val updatedModules = HashSet<ModuleLocation>()

    var isInitialized = false
        private set

    var isLoaded = false
        private set

    fun initialize(): Boolean {
        if (isInitialized) {
            return false
        }

        synchronized(ArendPreludeLibrary::class.java) {
            if (isInitialized) {
                return false
            }

            // Initialize prelude
            val preludeLibrary = ArendPreludeLibrary(project)
            this.preludeLibrary = preludeLibrary
            libraryManager.loadLibrary(preludeLibrary, null)
            preludeLibrary.prelude?.generatedModuleLocation = Prelude.MODULE_LOCATION

            if (Prelude.isInitialized()) {
                val tcRefMap = preludeLibrary.prelude?.tcRefMap
                if (tcRefMap != null) {
                    Prelude.forEach {
                        tcRefMap[it.referable.refLongName] = it.referable
                    }
                }
            }

            val concreteProvider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null)
            preludeLibrary.resolveNames(concreteProvider, libraryManager.libraryErrorReporter)
            Prelude.PreludeTypechecking(InstanceProviderSet(), concreteProvider, ArendReferableConverter, PsiElementComparator).typecheckLibrary(preludeLibrary)
            preludeLibrary.prelude?.let {
                fillAdditionalNames(it, additionalNames)
            }
            Prelude.initializeArray((preludeScope.resolveName(Prelude.ARRAY_NAME) as MetaAdapter).metaRef)

            // Set the listener that updates typechecked definitions
            project.service<ArendPsiChangeService>().addListener(this)

            // Listen for YAML files changes
            YAMLFileListener(project).register()

            ModuleSynchronizer(project).install()

            isInitialized = true
            isLoaded = true
        }

        return true
    }

    private var preludeLibrary: ArendPreludeLibrary? = null

    val prelude: ArendFile?
        get() = preludeLibrary?.prelude

    val preludeScope: Scope
        get() = prelude?.let { LexicalScope.opened(it) } ?: EmptyScope.INSTANCE

    fun getPsiReferable(referable: LocatedReferable): PsiLocatedReferable? {
        (referable.underlyingReferable as? PsiLocatedReferable)?.let { return it }
        return Scope.Utils.resolveName(preludeScope, referable.refLongName.toList()) as? PsiLocatedReferable
    }

    fun getDefinitionPsiReferable(definition: Definition) = getPsiReferable(definition.referable)

    override fun typechecked(definition: CoreDefinition) {
        addInstance(definition)
    }

    override fun loaded(definition: CoreDefinition) {
        addInstance(definition)
    }

    private fun addInstance(definition: CoreDefinition) {
        if (definition !is FunctionDefinition) return
        if (definition.kind != CoreFunctionDefinition.Kind.INSTANCE) return
        val classCall = definition.resultType as? ClassCallExpression ?: return
        val dfs = object : DFS<ClassDefinition,Void>() {
            override fun forDependencies(classDef: ClassDefinition): Void? {
                for (superClass in classDef.superClasses) {
                    visit(superClass)
                }
                return null
            }
        }
        dfs.visit(classCall.definition)
        for (classDef in dfs.visited) {
            instances.putValue(classDef.referable, definition.referable)
        }
    }

    // Returns the list of possible solutions. Each solution is a list of functions that are required for this instance to work.
    fun findInstances(classRef: TCDefReferable, classifyingExpression: Expression?): List<List<FunctionDefinition>> {
        val classDef = classRef.typechecked as? ClassDefinition ?: return emptyList()
        val result = ArrayList<List<FunctionDefinition>>()
        val functions = ArrayList(instances[classRef])
        while (functions.isNotEmpty()) {
            val collected = getInstances(GlobalInstancePool(SimpleInstanceProvider(functions), null), classDef, classifyingExpression, SubclassSearchParameters(classDef))
            if (collected.isEmpty()) break
            result.add(collected)
            if (!functions.remove(collected[0].referable)) break
        }
        return result
    }

    private fun getInstances(pool: GlobalInstancePool, classDef: ClassDefinition, classifyingExpression: Expression?, parameters: InstanceSearchParameters): List<FunctionDefinition> {
        fun getFunction(expr: Concrete.Expression?) =
            (((expr as? Concrete.ReferenceExpression)?.referent as? TCDefReferable)?.typechecked as? FunctionDefinition)?.let { listOf(it) }

        val result = pool.getInstance(classifyingExpression, parameters, null, null)
        getFunction(result)?.let { return it }
        if (result !is Concrete.AppExpression) return emptyList()

        var isRecursive = false
        for (argument in result.arguments) {
            if (argument.getExpression() is RecursiveInstanceHoleExpression) {
                isRecursive = true
                break
            }
        }

        if (isRecursive) {
            val visitor = CheckTypeVisitor(DummyErrorReporter.INSTANCE, pool, null)
            visitor.instancePool = GlobalInstancePool(pool.instanceProvider, visitor)
            val tcResult = visitor.checkExpr(result, null)
            if (tcResult != null && classifyingExpression != null && classDef.classifyingField != null) {
                CompareVisitor.compare(visitor.equations, CMP.EQ, classifyingExpression, FieldCallExpression.make(classDef.classifyingField, tcResult.expression), null, null)
            }
            val resultExpr = visitor.finalize(tcResult, result, false)?.expression
            if (resultExpr != null) {
                val collected = ArrayList<FunctionDefinition>()
                fun collect(expr: Expression) {
                    if (expr is AppExpression) {
                        collect(expr.function)
                        collect(expr.argument)
                    } else if (expr is FunCallExpression) {
                        if (expr.definition.kind == CoreFunctionDefinition.Kind.INSTANCE) {
                            collected.add(expr.definition)
                        }
                        for (argument in expr.defCallArguments) {
                            collect(argument)
                        }
                    }
                }
                collect(resultExpr)
                if (collected.isNotEmpty()) return collected
            }
        }

        return getFunction(result.function) ?: emptyList()
    }

    fun reload(onlyInternal: Boolean, refresh: Boolean = true) {
        ComputationRunner.getCancellationIndicator().cancel()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reloading Arend libraries", false) {
            override fun run(indicator: ProgressIndicator) {
                if (refresh) {
                    refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                }

                runReadAction {
                    isLoaded = false
                    if (onlyInternal) {
                        libraryManager.reloadInternalLibraries {
                            project.service<ArendResolveCache>().clear()
                            val it = extensionDefinitions.iterator()
                            while (it.hasNext()) {
                                if (!it.next().value.isExternal) {
                                    it.remove()
                                }
                            }

                            prepareReload()
                        }
                    } else {
                        libraryManager.reload {
                            project.service<ArendResolveCache>().clear()
                            extensionDefinitions.clear()
                            tcRefMaps.clear()

                            prepareReload()
                        }
                    }
                }

                isLoaded = true
                project.service<ArendPsiChangeService>().incModificationCount()
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    private fun prepareReload(): ArendTypechecking {
        project.service<ErrorService>().clearAllErrors()
        project.service<ArendPsiChangeService>().incModificationCount()
        project.service<TypecheckingTaskQueue>().clearQueue()
        return ArendTypechecking.create(project)
    }

    override fun request(definition: Definition, library: Library) {
        extensionDefinitions[definition.referable] = library
    }

    fun getAdditionalReferables(name: String) = additionalNames[name] ?: emptyList()

    val additionalReferables: Map<String, List<PsiLocatedReferable>>
        get() = additionalNames

    private fun resetErrors(def: Referable, removeTCRef: Boolean) {
        if (removeTCRef) {
            (def as? ReferableAdapter<*>)?.dropTCCache()
        }
        if (def is TCDefinition) {
            project.service<ErrorService>().clearTypecheckingErrors(def)
        }
    }

    private fun removeDefinition(referable: LocatedReferable, removeTCRef: Boolean): TCReferable? {
        if (referable is PsiElement && !referable.isValid) {
            return null
        }

        val curRef = referable.underlyingReferable
        val fullName = FullName(referable)
        val tcRefMap = fullName.modulePath?.let { tcRefMaps[it] }
        val tcReferable = tcRefMap?.get(fullName.longName)
        if (tcReferable !is TCDefReferable) {
            resetErrors(curRef, removeTCRef)
            return tcReferable
        }

        instances.remove(tcReferable)
        val funcDef = tcReferable.typechecked as? FunctionDefinition
        if (funcDef != null) {
            val classDef = (funcDef.resultType as? ClassCallExpression)?.definition
            if (classDef != null) {
                instances.remove(classDef.referable, funcDef.referable)
            }
        }

        val library = extensionDefinitions[tcReferable]
        if (library != null) {
            project.service<TypecheckingTaskQueue>().addTask {
                val provider = ArendDependencyProviderImpl(ArendTypechecking.create(project), libraryManager.getAvailableModuleScopeProvider(library), libraryManager.definitionRequester, library)
                try {
                    runReadAction {
                        service<ArendExtensionChangeListener>().notifyIfNeeded(project)
                    }
                } finally {
                    provider.disable()
                }
            }
        }

        val prevRef = tcReferable.underlyingReferable
        if (curRef is PsiLocatedReferable && prevRef is PsiLocatedReferable && prevRef != curRef && prevRef.containingFile == curRef.containingFile) {
            return null
        }
        if (removeTCRef) {
            tcRefMap.remove(fullName.longName)
        }
        resetErrors(curRef, removeTCRef)

        val tcTypecheckable = tcReferable.typecheckable
        tcTypecheckable.location?.let { updatedModules.add(it) }
        return tcTypecheckable
    }

    enum class LastModifiedMode { SET, SET_NULL, DO_NOT_TOUCH }

    private fun updateDefinition(referable: LocatedReferable, file: ArendFile, mode: LastModifiedMode, removeTCRef: Boolean) {
        if (mode != LastModifiedMode.DO_NOT_TOUCH && referable is TCDefinition) {
            val isValid = referable.isValid
            if (mode == LastModifiedMode.SET) {
                file.lastModifiedDefinition = if (isValid) referable else null
            } else {
                if (file.lastModifiedDefinition != referable) {
                    file.lastModifiedDefinition = null
                }
            }
        }

        val tcReferable = removeDefinition(referable, removeTCRef) ?: return
        val dependencies = synchronized(project) {
            dependencyListener.update(tcReferable)
        }
        for (ref in dependencies) {
            removeDefinition(ref, removeTCRef)
        }

        if ((referable as? ArendDefFunction)?.functionKw?.useKw != null) {
            (referable.parentGroup as? TCDefinition)?.let { updateDefinition(it, file, LastModifiedMode.DO_NOT_TOUCH, removeTCRef) }
        }
    }

    override fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean) {
        if (!isExternalUpdate) {
            def.checkTCReferableName()
        }
        updateDefinition(def, file, if (isExternalUpdate) LastModifiedMode.SET_NULL else LastModifiedMode.SET, !isExternalUpdate)
    }

    class LibraryManagerTestingOptions {
        companion object {
            private val stdLib: Key<Library?> = Key.create("AREND_TEST_STD_LIBRARY")

            @TestOnly
            fun setStdLibrary(lib: Library, disposable: Disposable) {
                TestModeFlags.set(stdLib, lib, disposable)
            }

            @TestOnly
            internal fun getRegisteredLibraries(): MutableList<Library>? {
                return TestModeFlags.get(stdLib)?.let { mutableListOf(it) }
            }
        }
    }
}
