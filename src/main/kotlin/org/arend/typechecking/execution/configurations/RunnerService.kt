package org.arend.typechecking.execution.configurations

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arend.error.DummyErrorReporter
import org.arend.module.ModuleLocation
import org.arend.naming.resolving.DelegateResolverListener
import org.arend.naming.resolving.ResolverListener
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.computation.CancellationIndicator
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

@Service(Service.Level.PROJECT)
class RunnerService(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun runChecker(library: String?, isTest: Boolean, module: ModuleLocation?, definition: String?, resolverListener: ResolverListener = ResolverListener.EMPTY, indicator: CancellationIndicator = UnstoppableCancellationIndicator.INSTANCE) {
        coroutineScope.launch {
            val message = "Resolving " + if (module == null) {
                if (library == null) "project" else "library $library"
            } else {
                "module $module"
            }

            withBackgroundProgress(project, message) {
                val server = project.service<ArendServerService>().server
                if (module == null) {
                    ArendServerRequesterImpl(project).requestUpdate(server, library, isTest)
                }
                val modules = if (module == null) server.modules.filter { (library == null || it.libraryName == library) && (it.locationKind == ModuleLocation.LocationKind.SOURCE || isTest && it.locationKind == ModuleLocation.LocationKind.TEST) } else emptyList()
                reportSequentialProgress(modules.size) { reporter ->
                    readAction {
                        if (module == null) {
                            server.getCheckerFor(modules).resolveModules(DummyErrorReporter.INSTANCE, indicator, object : DelegateResolverListener(resolverListener) {
                                override fun moduleResolved(module: ModuleLocation?) {
                                    super.moduleResolved(module)
                                    reporter.itemStep()
                                }
                            })
                        } else {
                            server.getCheckerFor(listOf(module)).resolveModules(DummyErrorReporter.INSTANCE, indicator, resolverListener)
                            /* TODO[server2]
                            if (definition == "") {
                                val group = library?.getModuleGroup(modulePath, command.isTest)
                                if (library == null || group == null) {
                                    NotificationErrorReporter(environment.project).report(ModuleNotFoundError(modulePath))
                                    return null
                                }
                                ApplicationManager.getApplication().run {
                                    executeOnPooledThread {
                                        runReadAction {
                                            library.resetGroup(group)
                                        }
                                    }
                                }
                            } else {
                                val scope = if (command.isTest) library?.testsModuleScopeProvider?.forModule(modulePath) else library?.moduleScopeProvider?.forModule(modulePath)
                                if (library == null || scope == null) {
                                    NotificationErrorReporter(environment.project).report(ModuleNotFoundError(modulePath))
                                    return null
                                }
                                val ref = Scope.resolveName(scope, command.definitionFullName.split('.')) as? LocatedReferable
                                if (ref == null) {
                                    NotificationErrorReporter(environment.project).report(DefinitionNotFoundError(command.definitionFullName, modulePath))
                                    return null
                                }
                                library.resetDefinition(ref)
                            }
                            */
                        }
                    }
                }
            }

            withContext(Dispatchers.EDT) {
                project.service<ArendMessagesService>().update()
            }
        }
    }
}