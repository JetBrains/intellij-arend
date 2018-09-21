package org.arend.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiDocumentManager
import org.arend.psi.ArendFile
import java.io.PrintWriter
import java.io.StringWriter

class ArendPrettyPrinterFormatAction : AnAction(), DumbAware {

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabled = psiFile is ArendFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val project = AnAction.getEventProject(e) ?: return
        if (psiFile !is ArendFile) return

        val groupId = "Arend pretty printing"
        try {
            ApplicationManager.getApplication().saveAll()

            val formattedText = prettyPrint(psiFile) ?: return

            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
            CommandProcessor.getInstance().executeCommand(
                    project,
                    {
                        ApplicationManager.getApplication().runWriteAction {
                            document.setText(formattedText)
                        }
                    },
                    NOTIFICATION_TITLE,
                    "",
                    document
            )

            val message = "${psiFile.name} formatted with PrettyPrinter"
            val notification = Notification(
                    groupId,
                    NOTIFICATION_TITLE,
                    message,
                    NotificationType.INFORMATION
            )
            Notifications.Bus.notify(notification, project)
        } catch (exception: Exception) {
            val message = "${psiFile.name} formatting with PrettyPrinter failed"
            val writer = StringWriter()
            exception.printStackTrace(PrintWriter(writer))
            val notification = Notification(
                    groupId,
                    message,
                    writer.toString(),
                    NotificationType.ERROR
            )
            Notifications.Bus.notify(notification, project)
            LOG.error(exception)
        }
    }

    private companion object {
        private const val NOTIFICATION_TITLE = "Reformat code with PrettyPrinter"
        private val LOG = Logger.getInstance(ArendPrettyPrinterFormatAction::class.java)

        private fun prettyPrint(module: ArendFile): String? {
            return null
            /* TODO[pretty]
            val builder = StringBuilder()
            val visitor = ToTextVisitor(builder, 0)
            visitor.visitModule(module)
            return builder.toString()
            */
        }
    }
}
