package org.arend.typechecking.runner

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.*
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.LongName
import org.arend.module.ModuleLocation
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.CoroutineCancellationIndicator
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.module.FullName
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.ArendCheckerFactory

@Service(Service.Level.PROJECT)
class RunnerService(private val project: Project, private val coroutineScope: CoroutineScope) {
    private fun runChecker(library: String?, isTest: Boolean, module: ModuleLocation?, definition: LongName?, checkerFactory: ArendCheckerFactory?, bgAction: (() -> Unit)?, edtAction: (() -> Unit)?) =
        coroutineScope.launch {
            val message = module?.toString() ?: (library ?: "project")
            val server = project.service<ArendServerService>().server
            withBackgroundProgress(project, "Checking $message") { reportSequentialProgress { reporter ->
                val checker = reporter.nextStep(5, "Resolving $message") { reportRawProgress { reporter ->
                    if (module == null) {
                        ArendServerRequesterImpl(project).requestUpdate(server, library, isTest)
                    }
                    val checker = server.getCheckerFor(if (module == null) server.modules.filter { (library == null || it.libraryName == library) && (it.locationKind == ModuleLocation.LocationKind.SOURCE || isTest && it.locationKind == ModuleLocation.LocationKind.TEST) } else listOf(module))
                    checker.resolveAll(CoroutineCancellationIndicator(this), IntellijProgressReporter(reporter) { it.toString() })
                    checker
                } }

                if (checkerFactory == null) withContext(Dispatchers.EDT) {
                    project.service<ArendMessagesService>().update()
                }

                val updated = reporter.nextStep(100, "Typechecking $message") {
                    reportRawProgress { reporter ->
                        val indicator = IntellijProgressReporter<List<Concrete.ResolvableDefinition>>(reporter) {
                            val ref = it.firstOrNull()?.data ?: return@IntellijProgressReporter null
                            val location = if (module == null) ref.location else null
                            (if (location == null) "" else "$location ") + ref.refLongName.toString()
                        }
                        val result = if (checkerFactory == null) {
                            checker.typecheck(if (definition == null || module == null) null else listOf(FullName(module, definition)), NotificationErrorReporter(project), CoroutineCancellationIndicator(this), indicator)
                        } else {
                            checker.typecheck(FullName(module, definition!!), checkerFactory, DummyErrorReporter.INSTANCE, CoroutineCancellationIndicator(this), indicator)
                        }
                        if (bgAction != null) bgAction()
                        result
                    } > 0
                }

                if (updated && checkerFactory == null) {
                    DaemonCodeAnalyzer.getInstance(project).restart()
                    withContext(Dispatchers.EDT) {
                        project.service<ArendMessagesService>().update()
                        if (edtAction != null) edtAction()
                    }
                } else if (edtAction != null) {
                    withContext(Dispatchers.EDT) {
                        edtAction()
                    }
                }
            } }
        }

    fun runChecker(library: String?, isTest: Boolean, module: ModuleLocation?, definition: LongName?) =
        runChecker(library, isTest, module, definition, null, null, null)

    fun runChecker(module: ModuleLocation, definition: LongName, checkerFactory: ArendCheckerFactory, bgAction: (() -> Unit)?, edtAction: (() -> Unit)?) =
        runChecker(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, definition, checkerFactory, bgAction, edtAction)

    fun runChecker(module: ModuleLocation) =
        runChecker(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, null)
}