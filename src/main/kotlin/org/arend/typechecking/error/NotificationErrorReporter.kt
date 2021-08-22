package org.arend.typechecking.error

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.error.GeneralError.Level
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.DocStringBuilder
import org.arend.naming.scope.EmptyScope
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer


class NotificationErrorReporter(private val project: Project, private val ppConfig: PrettyPrinterConfig = PrettyPrinterConfig.DEFAULT): ErrorReporter {
    companion object {
        val errorNotifications: NotificationGroup
            get() = NotificationGroupManager.getInstance().getNotificationGroup("Arend Error Messages")
        val warningNotifications: NotificationGroup
            get() = NotificationGroupManager.getInstance().getNotificationGroup("Arend Warning Messages")
        val infoNotifications: NotificationGroup
            get() = NotificationGroupManager.getInstance().getNotificationGroup("Arend Info Messages")

        fun notify(level: Level?, title: String?, content: String, project: Project) {
            val group = when (level) {
                Level.ERROR -> errorNotifications
                Level.WARNING, Level.WARNING_UNUSED, Level.GOAL -> warningNotifications
                Level.INFO, null -> infoNotifications
            }
            val type = when (level) {
                Level.ERROR -> NotificationType.ERROR
                Level.WARNING, Level.WARNING_UNUSED, Level.GOAL -> NotificationType.WARNING
                Level.INFO, null -> NotificationType.INFORMATION
            }
            group.createNotification(title ?: "", content, type).notify(project)
        }
    }

    override fun report(error: GeneralError) {
        val newPPConfig = PrettyPrinterConfigWithRenamer(ppConfig, EmptyScope.INSTANCE)
        val title = DocStringBuilder.build(error.getHeaderDoc(newPPConfig))
        val content = DocStringBuilder.build(error.getBodyDoc(newPPConfig))
        notify(error.level, title, content, project)
    }

    fun info(msg: String) {
        infoNotifications.createNotification(msg, NotificationType.INFORMATION).notify(project)
    }

    fun warn(msg: String) {
        warningNotifications.createNotification(msg, NotificationType.WARNING).notify(project)
    }
}