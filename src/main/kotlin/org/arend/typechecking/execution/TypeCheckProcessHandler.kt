package org.arend.typechecking.execution

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.SmartPointerManager
import org.arend.error.GeneralError
import org.arend.library.Library
import org.arend.library.SourceLibrary
import org.arend.library.error.LibraryError
import org.arend.library.error.ModuleInSeveralLibrariesError
import org.arend.module.ArendRawLibrary
import org.arend.module.ModulePath
import org.arend.module.error.ModuleNotFoundError
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendFile
import org.arend.psi.ArendStatement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.findGroupByFullName
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete
import org.arend.term.group.Group
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.BinaryFileSaver
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.TestBasedTypechecking
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ParserError
import org.arend.typechecking.error.TypecheckingErrorReporter
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.jetbrains.ide.PooledThreadExecutor
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

        val typecheckingErrorReporter = TypecheckingErrorReporter(typeCheckerService, PrettyPrinterConfig.DEFAULT, eventsProcessor)
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
        val libraries = if (command.library == "" && modulePath == null) registeredLibraries.filterIsInstance<ArendRawLibrary>() else {
            val library = if (command.library != "") typeCheckerService.libraryManager.getRegisteredLibrary(command.library) else findLibrary(modulePath!!, registeredLibraries, typecheckingErrorReporter)
            if (library == null) {
                if (command.library != "") {
                    typecheckingErrorReporter.report(LibraryError.notFound(command.library))
                }
                eventsProcessor.onSuitesFinished()
                destroyProcessImpl()
                return
            }
            if (library !is ArendRawLibrary) {
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

        val persistableLibraries = typeCheckerService.libraryManager.registeredLibraries.filterIsInstance<SourceLibrary>().filter { it.supportsPersisting() }
        for (module in typeCheckerService.updatedModules) {
            for (lib in persistableLibraries) {
                if (lib.containsModule(module)) {
                    lib.deleteModule(module)
                }
            }
        }
        typeCheckerService.updatedModules.clear()

        PooledThreadExecutor.INSTANCE.execute {
            val referableConverter = typeCheckerService.newReferableConverter(true)
            val concreteProvider = PsiConcreteProvider(typeCheckerService.project, referableConverter, typecheckingErrorReporter, typecheckingErrorReporter.eventsProcessor)
            val collector = CollectingOrderingListener()
            val instanceProviderSet = PsiInstanceProviderSet(concreteProvider, referableConverter)
            val ordering = Ordering(instanceProviderSet, concreteProvider, collector, typeCheckerService.dependencyListener, referableConverter, typeCheckerService.typecheckerState, PsiElementComparator)

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
                            runReadAction { DefinitionResolveNameVisitor(concreteProvider, typecheckingErrorReporter).resolveGroup(module, referableConverter, ScopeFactory.forGroup(module, typeCheckerService.libraryManager.getAvailableModuleScopeProvider(library))) }
                        }
                        module
                    }

                    if (command.definitionFullName == "") {
                        for (module in modules) {
                            runReadAction {
                                reportParserErrors(module, module, typecheckingErrorReporter)
                            }
                            orderGroup(module, ordering)
                            module.lastModifiedDefinition = null
                        }
                    } else {
                        val ref = runReadAction {
                            modules.firstOrNull()?.findGroupByFullName(command.definitionFullName.split('.'))?.referable
                        }
                        if (ref == null) {
                            if (modules.isNotEmpty()) {
                                runReadAction { typecheckingErrorReporter.report(DefinitionNotFoundError(command.definitionFullName, modulePath)) }
                            }
                        } else {
                            val tcReferable = runReadAction { referableConverter.toDataLocatedReferable(ref) }
                            val typechecked =
                                if (tcReferable != null) {
                                    if (PsiLocatedReferable.isValid(tcReferable)) {
                                        typeCheckerService.typecheckerState.getTypechecked(tcReferable)
                                    } else {
                                        typeCheckerService.dependencyListener.update(tcReferable)
                                        null
                                    }
                                } else {
                                    null
                                }
                            if (typechecked == null || !typechecked.status().isOK) {
                                val definition = concreteProvider.getConcrete(ref)
                                if (definition is Concrete.Definition) {
                                    typeCheckerService.dependencyListener.update(definition.data)
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
                    val typechecking = TestBasedTypechecking(typecheckingErrorReporter.eventsProcessor, instanceProviderSet, typeCheckerService, concreteProvider, referableConverter, typecheckingErrorReporter, typeCheckerService.dependencyListener)
                    try {
                        typechecking.typecheckCollected(collector) { indicator.isCanceled }
                        ServiceManager.getService(typeCheckerService.project, BinaryFileSaver::class.java).saveAll()
                    } finally {
                        typecheckingErrorReporter.flush()
                        for (file in typechecking.filesToRestart) {
                            DaemonCodeAnalyzer.getInstance(typeCheckerService.project).restart(file)
                        }
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
        if (tcReferable != null) {
            typeCheckerService.dependencyListener.update(tcReferable)
            (ordering.concreteProvider.getConcrete(referable) as? Concrete.Definition)?.let { ordering.orderDefinition(it) }
        }

        for (subgroup in runReadAction { group.subgroups }) {
            orderGroup(subgroup, ordering)
        }
        for (subgroup in runReadAction { group.dynamicSubgroups }) {
            orderGroup(subgroup, ordering)
        }
    }

    private fun reportParserErrors(group: PsiElement, module: ArendFile, typecheckingErrorReporter: TypecheckingErrorReporter) {
        for (child in group.children) {
            when (child) {
                is PsiErrorElement -> {
                    val modulePath = module.modulePath
                    if (modulePath != null) {
                        typecheckingErrorReporter.report(ParserError(SmartPointerManager.createPointer(child), group as? PsiLocatedReferable
                            ?: ModuleReferable(modulePath), child.errorDescription))
                        if (group is PsiLocatedReferable) {
                            typecheckingErrorReporter.eventsProcessor.onTestFailure(group)
                        } else {
                            typecheckingErrorReporter.eventsProcessor.onSuiteFailure(modulePath)
                        }
                    }
                }
                is ArendStatement -> child.definition?.let { reportParserErrors(it, module, typecheckingErrorReporter) }
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
    GeneralError(Level.ERROR, if (modulePath == null) "Definition '$definitionName' cannot be located without a module name" else "Definition $definitionName not found in module $modulePath")
