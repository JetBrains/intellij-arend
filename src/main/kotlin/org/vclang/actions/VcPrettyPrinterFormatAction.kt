package org.vclang.actions

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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.source.SourceId
import com.jetbrains.jetpad.vclang.term.legacy.ToTextVisitor
import org.vclang.parser.AbstractTreeBuildVisitor
import org.vclang.psi.VcFile
import java.io.PrintWriter
import java.io.StringWriter

class VcPrettyPrinterFormatAction : AnAction(), DumbAware {

    override fun update(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabled = psiFile is VcFile
    }

    override fun actionPerformed(e: AnActionEvent) {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        val project = AnAction.getEventProject(e) ?: return
        if (psiFile !is VcFile) return

        val groupId = StringUtil.notNullize(e.presentation.text, NOTIFICATION_TITLE)
        try {
            ApplicationManager.getApplication().saveAll()

            rebuild(psiFile)
            val formattedText = prettyPrint(psiFile)

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
        private val NOTIFICATION_TITLE = "Reformat code with PrettyPrinter"
        private val LOG = Logger.getInstance(VcPrettyPrinterFormatAction::class.java)

        private fun rebuild(module: VcFile) {
            val visitor = AbstractTreeBuildVisitor(
                    SourceId { ModulePath("dummy") },
                    DummyErrorReporter()
            )
            visitor.visitModule(module)
        }

        private fun prettyPrint(module: VcFile): String {
            val builder = StringBuilder()
            val visitor = ToTextVisitor(builder, 0)
            visitor.visitModule(module)
            return builder.toString()
        }
    }
}
