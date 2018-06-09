package org.vclang.typechecking.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.SmartPointerManager
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.error.GeneralError
import com.jetbrains.jetpad.vclang.library.Library
import com.jetbrains.jetpad.vclang.library.SourceLibrary
import com.jetbrains.jetpad.vclang.library.error.LibraryError
import com.jetbrains.jetpad.vclang.library.error.ModuleInSeveralLibrariesError
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.error.ModuleNotFoundError
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.typechecking.CancellationIndicator
import com.jetbrains.jetpad.vclang.typechecking.order.Ordering
import com.jetbrains.jetpad.vclang.typechecking.order.listener.CollectingOrderingListener
import com.jetbrains.jetpad.vclang.typechecking.order.listener.TypecheckingOrderingListener
import org.jetbrains.ide.PooledThreadExecutor
import org.vclang.module.VcRawLibrary
import org.vclang.psi.VcFile
import org.vclang.psi.VcStatement
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.findGroupByFullName
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.typechecking.TestBasedTypechecking
import org.vclang.typechecking.TypeCheckingService
import org.vclang.typechecking.error.ParserError
import org.vclang.typechecking.error.TypecheckingErrorReporter
import java.io.OutputStream


class TypeCheckProcessHandler(
    private val typeCheckerService: TypeCheckingService,
    private val command: TypeCheckCommand
) : ProcessHandler() {
    var eventsProcessor: TypecheckingEventsProcessor? = null
    private val indicator: ProgressIndicator = ProgressIndicatorBase()

    override fun startNotify() {
        super.startNotify()

        val eventsProcessor = eventsProcessor ?: return
        ApplicationManager.getApplication().saveAll()

        val typecheckingErrorReporter = TypecheckingErrorReporter(PrettyPrinterConfig.DEFAULT, eventsProcessor)
        val modulePath = if (command.modulePath == "") null else ModulePath(command.modulePath.split('.'))
        if (modulePath != null) {
            eventsProcessor.onSuiteStarted(modulePath)
        }

        if (command.definitionFullName != "" && modulePath == null) {
            typecheckingErrorReporter.report(DefinitionNotFoundError(command.definitionFullName))
            eventsProcessor.onSuitesFinished()
            destroyProcessImpl()
            return
        }

        val registeredLibraries = typeCheckerService.libraryManager.registeredLibraries
        val libraries = if (command.library == "" && modulePath == null) registeredLibraries.filterIsInstance<VcRawLibrary>() else {
            val library = if (command.library != "") typeCheckerService.libraryManager.getRegisteredLibrary(command.library) else findLibrary(modulePath!!, registeredLibraries, typecheckingErrorReporter)
            if (library == null) {
                if (command.library != "") {
                    typecheckingErrorReporter.report(LibraryError.notFound(command.library))
                }
                eventsProcessor.onSuitesFinished()
                destroyProcessImpl()
                return
            }
            if (library !is VcRawLibrary) {
                typecheckingErrorReporter.report(LibraryError.incorrectLibrary(library.name))
                eventsProcessor.onSuitesFinished()
                destroyProcessImpl()
                return
            }
            listOf(library)
        }

        if (libraries.isEmpty()) {
            destroyProcessImpl()
            return
        }

        val referableConverter = typeCheckerService.referableConverter
        val concreteProvider = PsiConcreteProvider(referableConverter, typecheckingErrorReporter, typecheckingErrorReporter.eventsProcessor)
        val collector = CollectingOrderingListener()
        val ordering = Ordering(concreteProvider, collector, typeCheckerService.dependencyListener, referableConverter, typeCheckerService.typecheckerState)

        for (library in libraries) {
            val modulePaths = if (modulePath == null) library.loadedModules else listOf(modulePath)
            val modules = modulePaths.mapNotNull {
                val module = library.getModuleGroup(it)
                if (module == null) {
                    typecheckingErrorReporter.report(LibraryError.moduleNotFound(it, library.name))
                } else if (command.definitionFullName == "") {
                    DefinitionResolveNameVisitor(concreteProvider, typecheckingErrorReporter).resolveGroup(module, referableConverter, ScopeFactory.forGroup(module, typeCheckerService.libraryManager.moduleScopeProvider))
                }
                module
            }

            if (command.definitionFullName == "") {
                for (module in modules) {
                    reportParserErrors(module, module, typecheckingErrorReporter)
                }
                ordering.orderModules(modules)
            } else {
                val ref = modules.firstOrNull()?.findGroupByFullName(command.definitionFullName.split('.'))?.referable
                if (ref == null) {
                    if (modules.isNotEmpty()) {
                        typecheckingErrorReporter.report(DefinitionNotFoundError(command.definitionFullName, modulePath))
                    }
                } else {
                    val tcReferable = referableConverter.toDataLocatedReferable(ref)
                    val typechecked = typeCheckerService.typecheckerState.getTypechecked(tcReferable)
                    if (typechecked == null || typechecked.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                        val definition = concreteProvider.getConcrete(ref)
                        if (definition is Concrete.Definition) {
                            ordering.orderDefinition(definition)
                        }
                        else if (definition != null) error(command.definitionFullName + " is not a definition")
                    } else {
                        if (ref is PsiLocatedReferable) {
                            typecheckingErrorReporter.eventsProcessor.onTestStarted(ref)
                            typecheckingErrorReporter.eventsProcessor.onTestFinished(ref)
                        }
                    }
                }
            }
        }

        PooledThreadExecutor.INSTANCE.execute {
            try {
                TypecheckingOrderingListener.CANCELLATION_INDICATOR = CancellationIndicator { indicator.isCanceled }
                try {
                    val typechecking = TestBasedTypechecking(typecheckingErrorReporter.eventsProcessor, typeCheckerService.typecheckerState, concreteProvider, typecheckingErrorReporter, typeCheckerService.dependencyListener)

                    if (typechecking.typecheckCollected(collector)) {
                        for (module in typechecking.typecheckedModules) {
                            val library = typeCheckerService.libraryManager.getRegisteredLibrary(module.libraryName) as? SourceLibrary ?: continue
                            if (library.supportsPersisting()) {
                                runReadAction { library.persistModule(module.modulePath, referableConverter, typeCheckerService.libraryManager.libraryErrorReporter) }
                            }
                        }
                    }

                    typecheckingErrorReporter.eventsProcessor.onSuitesFinished()
                } finally {
                    TypecheckingOrderingListener.setDefaultCancellationIndicator()
                }
            }
            catch (e: ProcessCanceledException) {}
            catch (e: Exception) {
                Logger.getInstance(TypeCheckingService::class.java).error(e)
            }
            finally {
                ApplicationManager.getApplication().executeOnPooledThread {
                    destroyProcessImpl() //we prefer to call this method rather than "this@TypeCheckProcessHandler.destroyProcess()" for if processHandler state is not equal to PROCESS_RUNNING then destroyProcessImpl will not be invoked (this is true e. g. in the case when the user stops computation using Detach Process button)
                }
            }
        }
    }

    private fun reportParserErrors(group: PsiElement, module: VcFile, typecheckingErrorReporter: TypecheckingErrorReporter) {
        for (child in group.children) {
            when (child) {
                is PsiErrorElement -> {
                    val modulePath = module.modulePath
                    typecheckingErrorReporter.report(ParserError(SmartPointerManager.createPointer(child), group as? PsiLocatedReferable ?: ModuleReferable(modulePath), child.errorDescription))
                    if (group is PsiLocatedReferable) {
                        typecheckingErrorReporter.eventsProcessor.onTestFailure(group)
                    } else {
                        typecheckingErrorReporter.eventsProcessor.onSuiteFailure(modulePath)
                    }
                }
                is VcStatement -> child.definition?.let { reportParserErrors(it, module, typecheckingErrorReporter) }
            }
        }
    }

    private fun findLibrary(modulePath: ModulePath, registeredLibraries: Collection<Library>, typecheckingErrorReporter: TypecheckingErrorReporter): Library? {
        var library: Library? = null
        var libraries: MutableList<Library>? = null
        for (lib in registeredLibraries) {
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
            typecheckingErrorReporter.report(ModuleInSeveralLibrariesError(modulePath, libraries))
        }

        if (library == null) {
            typecheckingErrorReporter.report(ModuleNotFoundError(modulePath))
        }

        return library
    }

    override fun detachProcessImpl() {
        //Since we have no separate process to detach from, we simply interrupt current typechecking computation
        indicator.cancel()
    }

    override fun destroyProcessImpl() =
            notifyProcessTerminated(0)

    override fun detachIsDefault(): Boolean = true

    override fun getProcessInput(): OutputStream? = null
}

private class DefinitionNotFoundError(definitionName: String, modulePath: ModulePath? = null) :
    GeneralError(Level.ERROR, if (modulePath == null) "Definition '$definitionName' cannot be located without a module name" else "Definition $definitionName not found in module $modulePath") {
    override fun getAffectedDefinitions(): Collection<GlobalReferable> = emptyList()
}

