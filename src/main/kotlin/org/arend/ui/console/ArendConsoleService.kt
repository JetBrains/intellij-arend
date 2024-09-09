package org.arend.ui.console

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.naming.scope.Scope
import java.util.concurrent.ConcurrentLinkedQueue

@Service(Service.Level.PROJECT)
class ArendConsoleService(private val project: Project) {
    private var myView: ArendConsoleView? = null
    private var queue: ConcurrentLinkedQueue<ArendConsoleView.() -> Unit>? = null // needed only during initialization

    private fun withView(runnable: ArendConsoleView.() -> Unit) {
        myView?.let {
            return runnable(it)
        }

        synchronized(this) {
            if (queue == null) {
                queue = ConcurrentLinkedQueue()
            }
            queue?.add(runnable)
        }

        invokeLater {
            synchronized(this) {
                if (myView == null) {
                    val view = ArendConsoleView(project)
                    myView = view
                    view.toolWindow.show()
                    while (true) {
                        val r = queue?.poll()
                        if (r != null) {
                            r(view)
                        } else break
                    }
                    queue = null
                }
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