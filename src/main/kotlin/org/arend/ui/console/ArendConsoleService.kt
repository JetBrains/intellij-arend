package org.arend.ui.console

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.naming.scope.Scope

class ArendConsoleService(private val project: Project) {
    private var myView: ArendConsoleView? = null

    private fun withView(runnable: ArendConsoleView.() -> Unit) {
        myView?.let {
            return runnable(it)
        }
        invokeLater {
            synchronized(this) {
                val view = myView ?: ArendConsoleView(project)
                myView = view
                view.toolWindow.show()
                runnable(view)
            }
        }
    }

    fun print(doc: Doc, scope: Scope) {
        withView {
            editor.addDoc(doc, scope)
        }
    }

    fun updateText() {
        // We cannot update text since we do not store printed docs
    }

    fun clearText() {
        myView?.editor?.clearText()
    }
}