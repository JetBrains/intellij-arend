package org.arend.module

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.arend.module.config.ArendModuleConfigService
import org.arend.prelude.Prelude
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.*
import java.io.*
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


const val AREND_LIB = "arend-lib"

@Throws(IOException::class)
internal fun getVersion(): String? {
    val versionsConn = URL("https://raw.githubusercontent.com/JetBrains/$AREND_LIB/master/versions").openConnection()
    BufferedReader(InputStreamReader(versionsConn.getInputStream())).use { reader ->
        while (true) {
            val str = reader.readLine() ?: break
            val index = str.indexOf("->")
            if (index < 0) continue
            val range = VersionRange.parseVersionRange(str.substring(0, index)) ?: continue
            if (!range.inRange(Prelude.VERSION)) continue
            val result = str.substring(index + 2, str.length).trim()
            if (result.isNotEmpty()) return result
        }
        return Prelude.VERSION.longString
    }
}

private fun downloadArendLib(project: Project, indicator: ProgressIndicator, path: Path, ver: String?): Boolean {
    try {
        val version = ver ?: getVersion()
        if (version == null) {
            NotificationErrorReporter.errorNotifications.createNotification("$AREND_LIB does not support language version ${Prelude.VERSION}", NotificationType.ERROR).notify(project)
            return false
        }

        val conn = URL("https://github.com/JetBrains/$AREND_LIB/releases/download/v${version}/$AREND_LIB.zip").openConnection()
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
        return true
    } catch (e: IOException) {
        NotificationErrorReporter.errorNotifications.createNotification("An exception happened during downloading of $AREND_LIB", e.toString(), NotificationType.ERROR).notify(project)
        return false
    }
}

fun checkForUpdates(project: Project, version: Version?) {
    ApplicationManager.getApplication().executeOnPooledThread {
        val newVersion = try { Version.fromString(getVersion()) } catch (e: IOException) { null }
        if (newVersion != null && (version != null && /* TODO: Replace with version == null || */ newVersion > version)) {
            showDownloadNotification(project, Reason.UPDATE, newVersion.toString())
        }
    }
}

enum class Reason { WRONG_VERSION, MISSING, UPDATE }

fun showDownloadNotification(project: Project, reason: Reason, version: String? = null) {
    val libRoot = project.service<ArendProjectSettings>().librariesRoot.let { if (it.isNotEmpty()) Paths.get(it) else FileUtils.defaultLibrariesRoot() }
    val message = when (reason) {
        Reason.WRONG_VERSION -> "'$AREND_LIB' has a wrong version"
        Reason.MISSING -> "'$AREND_LIB' is missing"
        Reason.UPDATE -> "A newer version of '$AREND_LIB' is available"
    }
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Arend Library Update")
        .createNotification(message, "", if (reason == Reason.UPDATE) NotificationType.INFORMATION else NotificationType.ERROR)

    notification.addAction(object : NotificationAction("Download $AREND_LIB") {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
            notification.expire()
            if (Files.exists(libRoot) || libRoot.toFile().mkdirs()) {
                val zipFile = libRoot.resolve(AREND_LIB + FileUtils.ZIP_EXTENSION)
                if (!Files.exists(zipFile) || Files.isRegularFile(zipFile)) {
                    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Downloading $AREND_LIB", true) {
                        override fun run(indicator: ProgressIndicator) {
                            if (downloadArendLib(project, indicator, zipFile, version)) {
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

    if (reason == Reason.UPDATE) {
        notification.addAction(object : NotificationAction("Do not show again") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                service<ArendSettings>().checkForUpdates = false
                notification.expire()
            }
        })
    }

    Notifications.Bus.notify(notification, project)
}
