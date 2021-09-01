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
import java.util.*
import kotlin.collections.HashMap

// We can also use ProjectTaskListener, but it is invoked only on internal build actions
class ArendExtensionChangeListener : ExternalSystemTaskNotificationListenerAdapter() {
    private val modificationStamps = HashMap<Module, Long>()
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

    fun notifyIfNeeded(project: Project) {
        if (notifications[project]?.isExpired != false) {
            notifications[project] = createNotification(project)
        }
    }

    private fun createNotification(project: Project) =
        NotificationGroupManager.getInstance().getNotificationGroup("Arend Reload").createNotification("Arend extension changed", "", NotificationType.INFORMATION).apply {
            val action = ReloadLibrariesAction()
            addAction(object : NotificationAction("Reload Arend libraries") {
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
        val newStamps = ArrayList<Pair<Module, Long>>()
        for (module in project.arendModules) {
            newStamps.add(Pair(module, ArendModuleConfigService.getInstance(module)?.extensionMainClassFile?.timeStamp ?: continue))
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

    fun initializeModule(config: ArendModuleConfigService) {
        modificationStamps[config.module] = config.extensionMainClassFile?.timeStamp ?: return
    }
}
