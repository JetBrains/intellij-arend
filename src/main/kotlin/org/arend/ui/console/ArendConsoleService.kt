package org.arend.ui.console

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import org.arend.ext.prettyprinting.doc.Doc

class ArendConsoleService(private val project: Project) {
    private var myView: ArendConsoleView? = null

    private fun withView(runnable: ArendConsoleView.() -> Unit) {
        myView?.let {
            return runnable(it)
        }
        runInEdt {
            synchronized(this) {
                val view = myView ?: ArendConsoleView(project)
                myView = view
                runnable(view)
            }
        }
    }

    fun print(doc: Doc) {
        withView {
            runInEdt {
                toolWindow.show()
            }
            editor.addDoc(doc)
        }
    }

    fun updateText() {
        // We cannot update text since we do not store printed docs
    }

    fun clearText() {
        myView?.editor?.clearText()
    }
}