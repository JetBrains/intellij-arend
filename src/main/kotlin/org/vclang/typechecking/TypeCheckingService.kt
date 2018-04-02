package org.vclang.typechecking

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.library.Library
import com.jetbrains.jetpad.vclang.library.LibraryManager
import com.jetbrains.jetpad.vclang.library.error.LibraryError
import com.jetbrains.jetpad.vclang.library.error.ModuleError
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.error.ModuleNotFoundError
import com.jetbrains.jetpad.vclang.module.scopeprovider.EmptyModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.scopeprovider.LocatingModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.typechecking.CancellationIndicator
import com.jetbrains.jetpad.vclang.typechecking.SimpleTypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.CachingConcreteProvider
import org.vclang.module.VcRawLibrary
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.findGroupByFullName
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.resolving.VcResolveCache
import org.vclang.typechecking.execution.TypecheckingEventsProcessor
import org.vclang.vcModules

interface TypeCheckingService {
    var eventsProcessor: TypecheckingEventsProcessor?

    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    fun typeCheck(libraryName: String, modulePath: ModulePath?, definitionFullName: String, cancellationIndicator: CancellationIndicator)

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(private val project: Project) : TypeCheckingService {
    override var eventsProcessor: TypecheckingEventsProcessor?
        get() = typecheckingErrorReporter.eventsProcessor
        set(value) {
            typecheckingErrorReporter.eventsProcessor = value
        }

    override val typecheckerState = SimpleTypecheckerState()
    private val dependencyCollector = DependencyCollector(typecheckerState)
    private val typecheckingErrorReporter = TypecheckingErrorReporter(PrettyPrinterConfig.DEFAULT)
    override val libraryManager = LibraryManager(VcLibraryResolver(project), EmptyModuleScopeProvider.INSTANCE, typecheckingErrorReporter, LogErrorReporter(PrettyPrinterConfig.DEFAULT))

    init {
        libraryManager.moduleScopeProvider = LocatingModuleScopeProvider(libraryManager)

        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())
        project.messageBus.connect(project).subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, object : AnyPsiChangeListener.Adapter() {
            override fun beforePsiChanged(isPhysical: Boolean) {
                VcResolveCache.clearCache()
            }
        })
    }

    override fun typeCheck(libraryName: String, modulePath: ModulePath?, definitionFullName: String, cancellationIndicator: CancellationIndicator) {
        Typechecking.CANCELLATION_INDICATOR = cancellationIndicator
        try {
            val eventsProcessor = eventsProcessor!!
            if (modulePath != null) {
                eventsProcessor.onSuiteStarted(modulePath)
            }

            if (definitionFullName != "" && modulePath == null) {
                libraryManager.typecheckingErrorReporter.report(DefinitionNotFoundError(definitionFullName))
                eventsProcessor.onSuitesFinished()
                return
            }

            val libraries = if (libraryName == "" && modulePath == null) project.vcModules.map { VcRawLibrary(it, typecheckerState) } else {
                val library = if (libraryName != "") libraryManager.getLibrary(libraryName) else findLibrary(modulePath!!)
                if (library == null) {
                    if (libraryName != "") {
                        libraryManager.typecheckingErrorReporter.report(LibraryError.notFound(libraryName))
                    }
                    eventsProcessor.onSuitesFinished()
                    return
                }
                listOf(library)
            }

            val psiConcreteProvider = PsiConcreteProvider(typecheckingErrorReporter, eventsProcessor)
            val concreteProvider = CachingConcreteProvider(psiConcreteProvider)
            val typeChecking = TestBasedTypechecking(
                eventsProcessor,
                typecheckerState,
                concreteProvider,
                typecheckingErrorReporter,
                dependencyCollector
            )

            var computationFinished = true

            for (library in libraries) {
                val modulePaths = if (modulePath == null) library.loadedModules else listOf(modulePath)
                val modules = modulePaths.mapNotNull {
                    val module = library.getModuleGroup(it)
                    if (module == null) {
                        libraryManager.typecheckingErrorReporter.report(LibraryError.moduleNotFound(it, library.name))
                    }
                    module
                }

                if (definitionFullName == "") {
                    for (module in modules) {
                        DefinitionResolveNameVisitor(typecheckingErrorReporter).resolveGroup(module, CachingScope.make(ScopeFactory.forGroup(module, libraryManager.moduleScopeProvider)), concreteProvider)
                    }
                    psiConcreteProvider.isResolving = true
                    computationFinished = typeChecking.typecheckModules(modules) && computationFinished
                } else {
                    val ref = modules.firstOrNull()?.findGroupByFullName(definitionFullName.split('.'))?.referable
                    if (ref == null) {
                        if (modules.isNotEmpty()) {
                            libraryManager.typecheckingErrorReporter.report(DefinitionNotFoundError(definitionFullName, modulePath))
                        }
                    } else {
                        val typechecked = typecheckerState.getTypechecked(ref)
                        if (typechecked == null || typechecked.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                            psiConcreteProvider.isResolving = true
                            val definition = concreteProvider.getConcrete(ref)
                            if (definition is Concrete.Definition) computationFinished = typeChecking.typecheckDefinitions(listOf(definition)) && computationFinished
                            else if (definition != null) error(definitionFullName + " is not a definition")
                        } else {
                            if (ref is PsiLocatedReferable) {
                                eventsProcessor.onTestStarted(ref)
                                typeChecking.typecheckingBodyFinished(ref, typechecked)
                            }
                        }
                    }
                }

                /* TODO[references]
                if (library is SourceLibrary && library.supportsPersisting()) {
                    for (updatedModule in typeChecking.typecheckedModulesWithoutErrors) {
                        library.persistModule(updatedModule, libraryManager.libraryErrorReporter)
                    }
                }
                */
            }

            if (computationFinished) eventsProcessor.onSuitesFinished()
        } finally {
            Typechecking.setDefaultCancellationIndicator()
        }
    }

    private class DefinitionNotFoundError(definitionName: String, modulePath: ModulePath? = null) :
        GeneralError(Level.ERROR, if (modulePath == null) "Definition '$definitionName' cannot be located without a module name" else "Definition $definitionName not found in module $modulePath") {
        override fun getAffectedDefinitions(): Collection<GlobalReferable> = emptyList()
    }

    private fun findLibrary(modulePath: ModulePath): Library? {
        var library: Library? = null
        var libraries: MutableList<Library>? = null
        for (lib in libraryManager.registeredLibraries) {
            if (lib.containsModule(modulePath)) {
                if (library == null) {
                    library = lib
                } else {
                    if (libraries == null) {
                        libraries = ArrayList()
                        libraries.add(library)
                    }
                    libraries.add(lib)
                }
            }
        }

        if (libraries != null) {
            libraryManager.typecheckingErrorReporter.report(ModuleError(modulePath, libraries))
        }

        if (library == null) {
            libraryManager.typecheckingErrorReporter.report(ModuleNotFoundError(modulePath))
        }

        return library
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
         override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
            processParent(event)
        }

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
            if (event.child is VcFile) { // whole file has been removed
                for (child in event.child.children) invalidateChild(child)
            } else {
                processChildren(event)
                processParent(event)
            }
        }

        private fun processParent(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                val ancestors = event.parent.ancestors
                val definition = ancestors.filterIsInstance<VcDefinition>().firstOrNull()
                definition?.let {
                    dependencyCollector.update(definition)
                }
            }
        }

        private fun invalidateChild(element : PsiElement) {
            element.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement?) {
                    super.visitElement(element)
                    if (element is GlobalReferable) {
                        dependencyCollector.update(element)
                    }
                }
            })
        }

        private fun processChildren(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                invalidateChild(event.child)
            }
        }
    }
}
