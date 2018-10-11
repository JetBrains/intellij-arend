package org.arend.typechecking.error

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import org.arend.error.Error.Level
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.error.doc.DocStringBuilder
import org.arend.term.prettyprint.PrettyPrinterConfig


class NotificationErrorReporter(private val project: Project, private val ppConfig: PrettyPrinterConfig): ErrorReporter {
    companion object {
        val ERROR_NOTIFICATIONS = NotificationGroup("Arend Error Messages", NotificationDisplayType.STICKY_BALLOON, true)
        val WARNING_NOTIFICATIONS = NotificationGroup("Arend Warning Messages", NotificationDisplayType.BALLOON, true)
        val INFO_NOTIFICATIONS = NotificationGroup("Arend Info Messages", NotificationDisplayType.NONE, true)
    }

    override fun report(error: GeneralError) {
        val group = when (error.level) {
            Level.ERROR -> ERROR_NOTIFICATIONS
            Level.WARNING, Level.GOAL -> WARNING_NOTIFICATIONS
            Level.INFO -> INFO_NOTIFICATIONS
        }
        val type = when (error.level) {
            Level.ERROR -> NotificationType.ERROR
            Level.WARNING, Level.GOAL -> NotificationType.WARNING
            Level.INFO -> NotificationType.INFORMATION
        }
        val title = DocStringBuilder.build(error.getHeaderDoc(ppConfig))
        val content = DocStringBuilder.build(error.getBodyDoc(ppConfig))
        group.createNotification(title, content, type, null).notify(project)
    }
}