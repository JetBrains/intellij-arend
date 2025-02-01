package org.arend.typechecking.execution.configurations

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
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
import org.arend.ext.module.LongName
import org.arend.module.ModuleLocation
import org.arend.naming.resolving.DelegateResolverListener
import org.arend.naming.resolving.ResolverListener
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.computation.CancellationIndicator
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FullName

@Service(Service.Level.PROJECT)
class RunnerService(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun runChecker(library: String?, isTest: Boolean, module: ModuleLocation?, definition: String?, resolverListener: ResolverListener = ResolverListener.EMPTY, indicator: CancellationIndicator = UnstoppableCancellationIndicator.INSTANCE) {
        coroutineScope.launch {
            val message = if (module == null) {
                if (library == null) "project" else "library $library"
            } else {
                "module $module"
            }

            val server = project.service<ArendServerService>().server
            val checker = withBackgroundProgress(project, "Loading $message") {
                if (module == null) {
                    ArendServerRequesterImpl(project).requestUpdate(server, library, isTest)
                }
                val checker = server.getCheckerFor(if (module == null) server.modules.filter { (library == null || it.libraryName == library) && (it.locationKind == ModuleLocation.LocationKind.SOURCE || isTest && it.locationKind == ModuleLocation.LocationKind.TEST) } else listOf(module))
                checker.getDependencies(indicator)
                checker
            }

            val dependencies = checker.getDependencies(indicator)?.size ?: return@launch
            withBackgroundProgress(project, "Resolving $message") {
                reportSequentialProgress(dependencies) { reporter ->
                    checker.resolveAll(DummyErrorReporter.INSTANCE, indicator, object : DelegateResolverListener(resolverListener) {
                        override fun moduleResolved(module: ModuleLocation?) {
                            super.moduleResolved(module)
                            reporter.itemStep()
                        }
                    })
                }
            }

            withContext(Dispatchers.EDT) {
                project.service<ArendMessagesService>().update()
            }

            val definitions = if (definition == null || module == null) {
                checker.prepareTypechecking()
            } else {
                checker.prepareTypechecking(listOf(FullName(module, LongName.fromString(definition))), NotificationErrorReporter(project))
            }

            if (definitions > 0) {
                withBackgroundProgress(project, "Typechecking $message") {
                    reportSequentialProgress(definitions) { reporter ->
                        checker.typecheckPrepared(indicator) { reporter.itemStep() }
                    }
                }

                DaemonCodeAnalyzer.getInstance(project).restart()
                withContext(Dispatchers.EDT) {
                    project.service<ArendMessagesService>().update()
                }
            }
        }
    }
}