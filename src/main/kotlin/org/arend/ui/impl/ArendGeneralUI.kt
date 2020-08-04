package org.arend.ui.impl

import com.intellij.openapi.project.Project
import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.ext.error.GeneralError
import org.arend.ext.ui.ArendSession
import org.arend.ext.ui.ArendUI
import org.arend.psi.ext.ArendCompositeElement
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.ui.impl.session.ArendToolWindowSession

open class ArendGeneralUI(protected val project: Project) : ArendUI {
    override fun newSession(): ArendSession = ArendToolWindowSession(project)

    override fun showMessage(title: String?, message: String) {
        NotificationErrorReporter.notify(GeneralError.Level.INFO, title, message, project)
    }

    override fun showErrorMessage(title: String?, message: String) {
        NotificationErrorReporter.notify(GeneralError.Level.ERROR, title, message, project)
    }

    override fun getConsole(marker: Any?) =
        ArendConsoleImpl(project, ((marker as? ConcreteSourceNode)?.data ?: marker) as? ArendCompositeElement)
}