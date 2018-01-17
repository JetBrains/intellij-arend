package org.vclang

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity

class VcStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        if (ProjectRootManager.getInstance(project).contentSourceRoots.isEmpty()) {
            Notifications.Bus.notify(Notification("","No source roots detected!", "", NotificationType.ERROR), project)

        }
    }
}