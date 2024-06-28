package org.arend.typechecking.execution

import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.SmartPointerManager
import org.arend.ext.error.GeneralError
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.library.Library
import org.arend.library.SourceLibrary
import org.arend.library.error.LibraryError
import org.arend.library.error.ModuleInSeveralLibrariesError
import org.arend.module.ArendRawLibrary
import org.arend.module.ModuleLocation
import org.arend.module.error.ModuleNotFoundError
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.reference.FullModuleReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendStat
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.ArendGroup
import org.arend.psi.findGroupByFullName
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete
import org.arend.typechecking.*
import org.arend.typechecking.error.ParserError
import org.arend.typechecking.error.TypecheckingErrorReporter
import org.arend.typechecking.execution.TypecheckRunConfigurationProducer.Companion.TEST_PREFIX
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.CollectingOrderingListener
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.afterTypechecking
import org.jetbrains.ide.PooledThreadExecutor
import java.io.OutputStream


class TypeCheckProcessHandler(
    private val typeCheckerService: TypeCheckingService,
    private val command: TypeCheckCommand
) : ProcessHandler() {
        //OSProcessHandler(GeneralCommandLine()) {
    var eventsProcessor: TypecheckingEventsProcessor? = null
    private val indicator: ProgressIndicator = ProgressIndicatorBase()

    override fun startNotify() {
        super.startNotify()

        val eventsProcessor = eventsProcessor ?: return
        ApplicationManager.getApplication().saveAll()

        val typecheckingErrorReporter = TypecheckingErrorReporter(typeCheckerService.project.service(), PrettyPrinterConfig.DEFAULT, eventsProcessor)
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

        for (module in typeCheckerService.updatedModules) {
            val library = typeCheckerService.libraryManager.getRegisteredLibrary(module.libraryName) as? SourceLibrary
            if (library?.supportsPersisting() == true) {
                library.deleteModule(module.modulePath)
            }
        }
        typeCheckerService.updatedModules.clear()

        PooledThreadExecutor.INSTANCE.execute {
            val concreteProvider = PsiConcreteProvider(typeCheckerService.project, typecheckingErrorReporter, typecheckingErrorReporter.eventsProcessor)
            val collector = CollectingOrderingListener()
            val instanceProviderSet = PsiInstanceProviderSet()
            val ordering = Ordering(instanceProviderSet, concreteProvider, collector, typeCheckerService.dependencyListener, ArendReferableConverter, PsiElementComparator)

            try {
                runReadAction {
                    for (library in libraries) {
                        if (indicator.isCanceled) {
                            break
                        }

                        val modulePaths = if (modulePath == null) library.loadedModules else listOf(modulePath)
                        val modules = modulePaths.flatMap {
                            var newModulePath = it
                            val isTest = (newModulePath.firstName == TEST_PREFIX && newModulePath.toList().getOrNull(1) != EXTENSION.drop(1)) ||
                                    (newModulePath.size() == 1 && newModulePath.firstName == library.config.testsDirFile?.name)
                            val isSource = newModulePath.firstName == library.config.sourcesDirFile?.name
                            if (isTest || isSource) {
                                newModulePath = ModulePath(newModulePath.toList().subList(1, newModulePath.size()))
                            }
                            val items = when (val fileItem = library.config.getArendDirectoryOrFile(newModulePath, isTest)) {
                                is ArendFile -> listOf(fileItem)
                                is PsiDirectory -> getAllFilesInDirectory(fileItem)
                                else -> {
                                    typecheckingErrorReporter.report(LibraryError.moduleNotFound(newModulePath, library.name))
                                    emptyList()
                                }
                            }
                             for (module in items) {
                                if (command.definitionFullName == "") {
                                    val sourcesModuleScopeProvider = typeCheckerService.libraryManager.getAvailableModuleScopeProvider(library)
                                    val moduleScopeProvider = if (module.moduleLocation?.locationKind == ModuleLocation.LocationKind.TEST) {
                                        val testsModuleScopeProvider = library.testsModuleScopeProvider
                                        ModuleScopeProvider { modulePath ->
                                            sourcesModuleScopeProvider.forModule(modulePath)
                                                ?: testsModuleScopeProvider.forModule(modulePath)
                                        }
                                    } else sourcesModuleScopeProvider
                                    DefinitionResolveNameVisitor(concreteProvider, ArendReferableConverter, typecheckingErrorReporter).resolveGroup(module, ScopeFactory.forGroup(module, moduleScopeProvider))
                                }
                            }
                            items
                        }

                        if (command.definitionFullName == "") {
                            for (module in modules) {
                                reportParserErrors(module, module, typecheckingErrorReporter)
                                resetGroup(module)
                            }
                            for (module in modules) {
                                orderGroup(module, ordering)
                            }
                        } else {
                            val ref = modules.firstOrNull()?.findGroupByFullName(command.definitionFullName.split('.'))?.referable
                            if (ref == null) {
                                if (modules.isNotEmpty()) {
                                    typecheckingErrorReporter.report(DefinitionNotFoundError(command.definitionFullName, modulePath))
                                }
                            } else {
                                val tcReferable = ArendReferableConverter.toDataLocatedReferable(ref)
                                val typechecked =
                                    if (tcReferable is TCDefReferable) {
                                        if (PsiLocatedReferable.isValid(tcReferable)) {
                                            tcReferable.typechecked
                                        } else {
                                            typeCheckerService.dependencyListener.update(tcReferable)
                                            null
                                        }
                                    } else null
                                if (typechecked == null || !typechecked.status().isOK) {
                                    val definition = concreteProvider.getConcrete(ref)
                                    if (definition is Concrete.Definition) {
                                        typeCheckerService.dependencyListener.update(definition.data)
                                        ordering.order(definition)
                                    } else if (definition != null) error(command.definitionFullName + " is not a definition")
                                } else {
                                    if (ref is PsiLocatedReferable) {
                                        typecheckingErrorReporter.eventsProcessor.onTestStarted(ref)
                                        typecheckingErrorReporter.eventsProcessor.onTestFinished(ref)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!indicator.isCanceled) {
                    val typechecking = TestBasedTypechecking(typecheckingErrorReporter.eventsProcessor, instanceProviderSet, typeCheckerService, concreteProvider, typecheckingErrorReporter, typeCheckerService.dependencyListener)
                    try {
                        typechecking.typecheckCollected(collector, ProgressCancellationIndicator(indicator))
                        typeCheckerService.project.service<BinaryFileSaver>().saveAll()
                    } finally {
                        typecheckingErrorReporter.flush()
                        typeCheckerService.project.afterTypechecking(typechecking.filesToRestart)
                    }
                }
            }
            catch (_: ProcessCanceledException) {}
            catch (e: Exception) {
                Logger.getInstance(TypeCheckingService::class.java).error(e)
            }
            finally {
                typecheckingErrorReporter.eventsProcessor.onSuitesFinished()
                ApplicationManager.getApplication().executeOnPooledThread {
                    destroyProcessImpl() //we prefer to call this method rather than "this@TypeCheckProcessHandler.destroyProcess()" for if processHandler state is not equal to PROCESS_RUNNING then destroyProcessImpl will not be invoked (this is true e.g. in the case when the user stops computation using Detach Process button)
                }
            }
        }
    }

    private fun getAllFilesInDirectory(directory: PsiDirectory): List<ArendFile> {
        val arendFiles = mutableListOf<ArendFile>()
        for (subDir in directory.subdirectories) {
            arendFiles.addAll(getAllFilesInDirectory(subDir))
        }
        arendFiles.addAll(directory.files.filterIsInstance<ArendFile>())
        return arendFiles
    }

    private fun resetGroup(group: ArendGroup) {
        if (indicator.isCanceled) {
            return
        }

        val tcReferable = group.tcReferable
        if (tcReferable is TCDefReferable) {
            val typechecked = tcReferable.typechecked
            if (typechecked != null && !typechecked.status().isOK) {
                typeCheckerService.dependencyListener.update(tcReferable)
            }
        }

        for (stat in group.statements) {
            resetGroup(stat.group ?: continue)
        }
        for (subgroup in group.dynamicSubgroups) {
            resetGroup(subgroup)
        }
    }

    private fun orderGroup(group: ArendGroup, ordering: Ordering) {
        if (indicator.isCanceled) {
            return
        }

        (ordering.concreteProvider.getConcrete(group) as? Concrete.Definition)?.let { ordering.order(it) }

        for (stat in group.statements) {
            orderGroup(stat.group ?: continue, ordering)
        }
        for (subgroup in group.dynamicSubgroups) {
            orderGroup(subgroup, ordering)
        }
    }

    private fun reportParserErrors(group: PsiElement, module: ArendFile, typecheckingErrorReporter: TypecheckingErrorReporter) {
        for (child in group.children) {
            when (child) {
                is PsiErrorElement -> {
                    val moduleLocation = module.moduleLocation
                    if (moduleLocation != null) {
                        typecheckingErrorReporter.report(ParserError(SmartPointerManager.createPointer(child),
                            group as? PsiLocatedReferable ?: FullModuleReferable(moduleLocation), child.errorDescription))
                        if (group is PsiLocatedReferable) {
                            typecheckingErrorReporter.eventsProcessor.onTestFailure(group)
                        } else {
                            typecheckingErrorReporter.eventsProcessor.onSuiteFailure(moduleLocation.modulePath)
                        }
                    }
                }
                is ArendStat -> child.group?.let { reportParserErrors(it, module, typecheckingErrorReporter) }
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

class DefinitionNotFoundError(definitionName: String, modulePath: ModulePath? = null) :
    GeneralError(Level.ERROR, if (modulePath == null) "Definition '$definitionName' cannot be located without a module name" else "Definition $definitionName not found in module $modulePath")
