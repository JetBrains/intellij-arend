package org.arend.typechecking

import com.intellij.notification.*
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.arendModules
import java.util.*

// We can also use ProjectTaskListener, but it is invoked only on internal build actions
class ArendExtensionChangeListener : ExternalSystemTaskNotificationListener {
    override fun onSuccess(id: ExternalSystemTaskId) {
        val service = service<ArendExtensionChangeService>()
        val newNotifications = ArrayList<Pair<Project, Notification>>()
        for (project in ProjectManager.getInstance().openProjects) {
            val oldNotification = service.notifications[project]
            if (oldNotification?.isExpired == false) {
                newNotifications.add(Pair(project, oldNotification))
            } else {
                if (updateModificationStamps(project)) {
                    newNotifications.add(Pair(project, service.createNotification(project)))
                }
            }
        }

        service.notifications.clear()
        service.notifications.putAll(newNotifications)
    }

    private fun updateModificationStamps(project: Project): Boolean {
        val newStamps = ArrayList<Pair<Module, Long>>()
        for (module in project.arendModules) {
            newStamps.add(Pair(module, ArendModuleConfigService.getInstance(module)?.extensionMainClassFile?.timeStamp ?: continue))
        }

        val service = service<ArendExtensionChangeService>()
        var found = false
        for (pair in newStamps) {
            val oldTime = service.modificationStamps[pair.first]
            if (oldTime == null || oldTime < pair.second) {
                found = true
                break
            }
        }

        service.modificationStamps.clear()
        service.modificationStamps.putAll(newStamps)

        return found
    }
}
