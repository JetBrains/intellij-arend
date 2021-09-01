package org.arend.module

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.arend.module.config.ArendModuleConfigService
import org.arend.prelude.Prelude
import org.arend.settings.ArendProjectSettings
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FileUtils
import org.arend.util.arendModules
import org.arend.util.findExternalLibrary
import org.arend.util.refreshLibrariesDirectory
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


const val AREND_LIB = "arend-lib"

private fun downloadArendLib(project: Project, indicator: ProgressIndicator, path: Path): Boolean =
    try {
        val conn = URL("https://github.com/JetBrains/$AREND_LIB/releases/download/v${Prelude.VERSION.longString}/$AREND_LIB.zip").openConnection()
        BufferedInputStream(conn.getInputStream()).use { input ->
            val size = conn.contentLengthLong
            if (size < 0) {
                indicator.isIndeterminate = true
            }

            val file = path.toFile()
            val output = FileOutputStream(file)
            val buffer = ByteArray(8 * 1024)
            var totalRead: Long = 0
            while (true) {
                if (indicator.isCanceled) {
                    file.delete()
                    return@use false
                }

                val s = input.read(buffer, 0, buffer.size)
                if (s < 0) {
                    break
                }
                output.write(buffer, 0, s)

                if (size >= 0) {
                    totalRead += s
                    indicator.fraction = totalRead.toDouble() / size
                }
            }
        }
        true
    } catch (e: IOException) {
        NotificationErrorReporter.errorNotifications.createNotification("An exception happened during downloading of $AREND_LIB", e.toString(), NotificationType.ERROR).notify(project)
        false
    }

fun showDownloadNotification(project: Project, isNewVersion: Boolean) {
    val libRoot = project.service<ArendProjectSettings>().librariesRoot.let { if (it.isNotEmpty()) Paths.get(it) else FileUtils.defaultLibrariesRoot() }
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Arend Library Update")
        .createNotification(if (isNewVersion) "'$AREND_LIB' has a wrong version" else "'$AREND_LIB' is missing", "", NotificationType.ERROR)

    notification.addAction(object : NotificationAction("Download $AREND_LIB") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            if (Files.exists(libRoot) || libRoot.toFile().mkdirs()) {
                val zipFile = libRoot.resolve(AREND_LIB + FileUtils.ZIP_EXTENSION)
                if (!Files.exists(zipFile) || Files.isRegularFile(zipFile)) {
                    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading $AREND_LIB", true) {
                        override fun run(indicator: ProgressIndicator) {
                            if (downloadArendLib(project, indicator, zipFile)) {
                                refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                                project.findExternalLibrary(libRoot, AREND_LIB)?.root?.let {
                                    VfsUtil.markDirtyAndRefresh(false, true, false, it)
                                    for (module in project.arendModules) {
                                        ArendModuleConfigService.getInstance(module)?.synchronizeDependencies(false)
                                    }
                                    project.service<TypeCheckingService>().reload(onlyInternal = false, refresh = false)
                                }
                            }
                        }
                    })
                } else {
                    NotificationErrorReporter.errorNotifications.createNotification("", "Cannot open $zipFile", NotificationType.ERROR).notify(project)
                }
            } else {
                NotificationErrorReporter.errorNotifications.createNotification("", "Cannot create directory $libRoot", NotificationType.ERROR).notify(project)
            }
        }
    })

    notification.addAction(object : NotificationAction("Dismiss") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
        }
    })

    Notifications.Bus.notify(notification, project)
}
