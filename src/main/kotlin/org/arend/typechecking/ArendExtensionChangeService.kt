package org.arend.typechecking

import com.intellij.notification.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.application
import org.arend.module.ReloadLibrariesAction
import org.arend.module.config.ArendModuleConfigService

@Service
class ArendExtensionChangeService {
    val modificationStamps = HashMap<Module, Long>()
    val notifications = HashMap<Project, Notification>()

    fun initializeModule(config: ArendModuleConfigService) {
        modificationStamps[config.module] = config.extensionMainClassFile?.timeStamp ?: return
    }

    fun createNotification(project: Project) =
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

    fun notifyIfNeeded(project: Project) {
        val service = application.service<ArendExtensionChangeService>()
        if (service.notifications[project]?.isExpired != false) {
            service.notifications[project] = createNotification(project)
        }
    }
}