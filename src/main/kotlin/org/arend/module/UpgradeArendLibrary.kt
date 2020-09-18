package org.arend.module

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.arend.prelude.Prelude
import org.arend.settings.ArendSettings
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FileUtils
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

private val NOTIFICATION = NotificationGroup("Arend Library Update", NotificationDisplayType.STICKY_BALLOON, true)

private fun downloadArendLib(project: Project, file: Path): Boolean {
    try {
        BufferedInputStream(URL("https://github.com/JetBrains/arend-lib/releases/download/v${Prelude.VERSION.longString}/arend-lib.zip").openStream()).use { input ->
            val output = FileOutputStream(file.toFile())
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val s = input.read(buffer, 0, buffer.size)
                if (s < 0) {
                    break
                }
                output.write(buffer, 0, s)
            }
        }
        return true
    } catch (e: IOException) {
        NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification("An exception happened during downloading of $AREND_LIB", null, e.toString(), NotificationType.ERROR, null).notify(project)
        return false
    }
}

fun showDownloadNotification(project: Project, isNewVersion: Boolean) {
    val libRoot = service<ArendSettings>().librariesRoot.let { if (it.isNotEmpty()) Paths.get(it) else FileUtils.defaultLibrariesRoot() }
    val notification = NOTIFICATION.createNotification(if (isNewVersion) "$AREND_LIB has a wrong version" else "$AREND_LIB is missing", "", NotificationType.ERROR, null)

    notification.addAction(object : NotificationAction("Download $AREND_LIB") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            if (Files.exists(libRoot) || libRoot.toFile().mkdirs()) {
                val zipFile = libRoot.resolve(AREND_LIB + FileUtils.ZIP_EXTENSION)
                if (!Files.exists(zipFile) || Files.isRegularFile(zipFile)) {
                    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading $AREND_LIB", false) {
                        override fun run(indicator: ProgressIndicator) {
                            if (downloadArendLib(project, zipFile)) {
                                refreshLibrariesDirectory(service<ArendSettings>().librariesRoot)
                                project.findExternalLibrary(libRoot, AREND_LIB)?.root?.let {
                                    VfsUtil.markDirtyAndRefresh(false, true, false, it)
                                    project.service<TypeCheckingService>().reload(onlyInternal = true, refresh = false)
                                }
                            }
                        }
                    })
                } else {
                    NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification(null, null, "Cannot open $zipFile", NotificationType.ERROR, null).notify(project)
                }
            } else {
                NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification(null, null, "Cannot create directory $libRoot", NotificationType.ERROR, null).notify(project)
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
