package org.arend.module

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.arend.server.ArendServerService
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.findLibrary
import org.arend.util.refreshLibrariesDirectory

@Service(Service.Level.PROJECT)
class ReloadLibrariesService(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun reload(onlyInternal: Boolean, refresh: Boolean = true) {
        ComputationRunner.getCancellationIndicator().cancel()
        coroutineScope.launch {
            withBackgroundProgress(project, "Reloading Arend libraries") {
                if (refresh) {
                    refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                }

                val server = project.service<ArendServerService>().server
                val libraries = server.libraries.filter { !onlyInternal || server.getLibrary(it)?.isExternalLibrary == false }
                server.unloadLibraries(onlyInternal)
                for (library in libraries) {
                    server.updateLibrary(project.findLibrary(library) ?: continue, NotificationErrorReporter(project))
                }

                DaemonCodeAnalyzer.getInstance(project).restart()
                withContext(Dispatchers.EDT) {
                    project.service<ArendMessagesService>().update()
                }
            }
        }
    }
}