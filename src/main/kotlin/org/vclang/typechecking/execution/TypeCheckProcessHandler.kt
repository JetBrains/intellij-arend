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
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.typechecking.CancellationIndicator
import com.jetbrains.jetpad.vclang.typechecking.order.Ordering
import com.jetbrains.jetpad.vclang.typechecking.order.listener.CollectingOrderingListener
import com.jetbrains.jetpad.vclang.typechecking.order.listener.TypecheckingOrderingListener
import org.jetbrains.ide.PooledThreadExecutor
import org.vclang.module.VcRawLibrary
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcFile
import org.vclang.psi.VcStatement
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.findGroupByFullName
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.typechecking.PsiInstanceProviderSet
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
            return
        }

        val registeredLibraries = typeCheckerService.libraryManager.registeredLibraries.filterIsInstance<VcRawLibrary>()
        val libraries = if (command.library == "" && modulePath == null) registeredLibraries else {
            val library = if (command.library != "") typeCheckerService.libraryManager.getRegisteredLibrary(command.library) else findLibrary(modulePath!!, registeredLibraries, typecheckingErrorReporter)
            if (library == null) {
                if (command.library != "") {
                    typecheckingErrorReporter.report(LibraryError.notFound(command.library))
                }
                eventsProcessor.onSuitesFinished()
                return
            }
            if (library !is VcRawLibrary) {
                typecheckingErrorReporter.report(LibraryError.incorrectLibrary(command.library))
                eventsProcessor.onSuitesFinished()
                return
            }
            listOf(library)
        }

        if (libraries.isEmpty()) {
            return
        }

        PooledThreadExecutor.INSTANCE.execute {
            val referableConverter = typeCheckerService.referableConverter
            val concreteProvider = PsiConcreteProvider(referableConverter, typecheckingErrorReporter, typecheckingErrorReporter.eventsProcessor)
            val collector = CollectingOrderingListener()
            val instanceProviderSet = PsiInstanceProviderSet(concreteProvider)
            val ordering = Ordering(instanceProviderSet, concreteProvider, collector, typeCheckerService.dependencyListener, referableConverter, typeCheckerService.typecheckerState)

            try {
                for (library in libraries) {
                    if (indicator.isCanceled) {
                        break
                    }

                    val modulePaths = if (modulePath == null) runReadAction { library.loadedModules } else listOf(modulePath)
                    val modules = modulePaths.mapNotNull {
                        val module = runReadAction { library.getModuleGroup(it) }
                        if (module == null) {
                            runReadAction { typecheckingErrorReporter.report(LibraryError.moduleNotFound(it, library.name)) }
                        } else if (command.definitionFullName == "") {
                            runReadAction { DefinitionResolveNameVisitor(concreteProvider, typecheckingErrorReporter).resolveGroup(module, referableConverter, ScopeFactory.forGroup(module, typeCheckerService.libraryManager.moduleScopeProvider)) }
                        }
                        module
                    }

                    if (command.definitionFullName == "") {
                        for (module in modules) {
                            runReadAction {
                                reportParserErrors(module, module, typecheckingErrorReporter)
                            }
                            orderGroup(module, ordering)
                        }
                    } else {
                        val ref = runReadAction {
                            val group = modules.firstOrNull()?.findGroupByFullName(command.definitionFullName.split('.'))
                            if (group is VcDefClass && group.fatArrow != null) null else group?.referable
                        }
                        if (ref == null) {
                            if (modules.isNotEmpty()) {
                                runReadAction { typecheckingErrorReporter.report(DefinitionNotFoundError(command.definitionFullName, modulePath)) }
                            }
                        } else {
                            val tcReferable = runReadAction { referableConverter.toDataLocatedReferable(ref) }
                            val typechecked = typeCheckerService.typecheckerState.getTypechecked(tcReferable)
                            if (typechecked == null || typechecked.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                                val definition = concreteProvider.getConcrete(ref)
                                if (definition is Concrete.Definition) {
                                    ordering.orderDefinition(definition)
                                } else if (definition != null) error(command.definitionFullName + " is not a definition")
                            } else {
                                if (ref is PsiLocatedReferable) {
                                    runReadAction {
                                        typecheckingErrorReporter.eventsProcessor.onTestStarted(ref)
                                        typecheckingErrorReporter.eventsProcessor.onTestFinished(ref)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!indicator.isCanceled) {
                    TypecheckingOrderingListener.CANCELLATION_INDICATOR = CancellationIndicator { indicator.isCanceled }
                    try {
                        val typechecking = TestBasedTypechecking(typecheckingErrorReporter.eventsProcessor, instanceProviderSet, typeCheckerService.typecheckerState, concreteProvider, typecheckingErrorReporter, typeCheckerService.dependencyListener)

                        if (typechecking.typecheckCollected(collector)) {
                            for (module in typechecking.typecheckedModules) {
                                val library = typeCheckerService.libraryManager.getRegisteredLibrary(module.libraryName) as? SourceLibrary
                                    ?: continue
                                if (library.supportsPersisting()) {
                                    runReadAction { library.persistModule(module.modulePath, referableConverter, typeCheckerService.libraryManager.libraryErrorReporter) }
                                }
                            }
                        }
                    } finally {
                        TypecheckingOrderingListener.setDefaultCancellationIndicator()
                    }
                }
            }
            catch (e: ProcessCanceledException) {}
            catch (e: Exception) {
                Logger.getInstance(TypeCheckingService::class.java).error(e)
            }
            finally {
                typecheckingErrorReporter.eventsProcessor.onSuitesFinished()
                ApplicationManager.getApplication().executeOnPooledThread {
                    destroyProcessImpl() //we prefer to call this method rather than "this@TypeCheckProcessHandler.destroyProcess()" for if processHandler state is not equal to PROCESS_RUNNING then destroyProcessImpl will not be invoked (this is true e. g. in the case when the user stops computation using Detach Process button)
                }
            }
        }
    }

    private fun orderGroup(group: Group, ordering: Ordering) {
        if (indicator.isCanceled) {
            return
        }

        val referable = group.referable
        val tcReferable = runReadAction { ordering.referableConverter.toDataLocatedReferable(referable) }
        if (tcReferable == null || ordering.getTypechecked(tcReferable) == null) {
            (ordering.concreteProvider.getConcrete(referable) as? Concrete.Definition)?.let { ordering.orderDefinition(it) }
        }

        for (subgroup in runReadAction { group.subgroups }) {
            orderGroup(subgroup, ordering)
        }
        for (subgroup in runReadAction { group.dynamicSubgroups }) {
            orderGroup(subgroup, ordering)
        }
    }

    private fun reportParserErrors(group: PsiElement, module: VcFile, typecheckingErrorReporter: TypecheckingErrorReporter) {
        for (child in group.children) {
            when (child) {
                is PsiErrorElement -> {
                    val modulePath = module.modulePath
                    typecheckingErrorReporter.report(ParserError(child, group as? PsiLocatedReferable ?: ModuleReferable(modulePath)))
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

    private fun findLibrary(modulePath: ModulePath, registeredLibraries: Collection<VcRawLibrary>, typecheckingErrorReporter: TypecheckingErrorReporter): VcRawLibrary? {
        var library: VcRawLibrary? = null
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
