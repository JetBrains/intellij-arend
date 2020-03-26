package org.arend.typechecking

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.arend.module.ReloadLibrariesAction
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.arendModules
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.*
import kotlin.collections.HashMap

// We can also use ProjectTaskListener, but it is invoked only on internal build actions
class ArendExtensionChangeListener : ExternalSystemTaskNotificationListenerAdapter() {
    private companion object {
        val NOTIFICATIONS = NotificationGroup("Arend Reload", NotificationDisplayType.STICKY_BALLOON, false)
    }

    private val modificationStamps = HashMap<Module, FileTime>()
    private val notifications = HashMap<Project, Notification>()

    override fun onSuccess(id: ExternalSystemTaskId) {
        val newNotifications = ArrayList<Pair<Project, Notification>>()
        for (project in ProjectManager.getInstance().openProjects) {
            val oldNotification = notifications[project]
            if (oldNotification?.isExpired == false) {
                newNotifications.add(Pair(project, oldNotification))
            } else {
                if (updateModificationStamps(project)) {
                    newNotifications.add(Pair(project, createNotification(project)))
                }
            }
        }

        notifications.clear()
        notifications.putAll(newNotifications)
    }

    private fun createNotification(project: Project) =
        NOTIFICATIONS.createNotification("Arend extension changed", "", NotificationType.INFORMATION, null).apply {
            val action = ReloadLibrariesAction()
            addAction(object : NotificationAction(action.templateText) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    action.actionPerformed(e)
                    notification.expire()
                }
            })
            addAction(object : NotificationAction("Dismiss") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    notification.expire()
                }
            })
            Notifications.Bus.notify(this, project)
        }

    private fun updateModificationStamps(project: Project): Boolean {
        val newStamps = ArrayList<Pair<Module, FileTime>>()
        for (module in project.arendModules) {
            val path = ArendModuleConfigService.getInstance(module)?.extensionClassPath ?: continue
            try {
                newStamps.add(Pair(module, Files.getLastModifiedTime(path)))
            } catch (e: IOException) {
                modificationStamps[module]?.let {
                    newStamps.add(Pair(module, it))
                }
            }
        }

        var found = false
        for (pair in newStamps) {
            val oldTime = modificationStamps[pair.first]
            if (oldTime == null || oldTime < pair.second) {
                found = true
                break
            }
        }

        modificationStamps.clear()
        modificationStamps.putAll(newStamps)

        return found
    }

    fun initializeModule(module: Module) {
        val path = ArendModuleConfigService.getInstance(module)?.extensionClassPath ?: return
        try {
            modificationStamps[module] = Files.getLastModifiedTime(path)
        } catch (e: IOException) {}
    }
}
