package org.arend.typechecking

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendPsiChangeService
import java.util.concurrent.LinkedBlockingQueue

private class TypecheckingTask(val modificationCount: Long, val action: () -> Unit)

@Service
class TypecheckingTaskQueue : ArendDefinitionChangeListener {
    private val queue = LinkedBlockingQueue<TypecheckingTask>()
    private val tracker = service<ArendPsiChangeService>().definitionModificationTracker
    private var isThreadRunning = false

    init {
        service<ArendPsiChangeService>().addListener(this)
    }

    fun addTask(modificationCount: Long = tracker.modificationCount, action: () -> Unit) {
        if (modificationCount < tracker.modificationCount) {
            return
        }

        queue.put(TypecheckingTask(modificationCount, action))
        if (isThreadRunning) {
            return
        }

        synchronized(this) {
            if (isThreadRunning) return
            isThreadRunning = true
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                while (true) {
                    val task = queue.take()
                    if (task.modificationCount >= tracker.modificationCount) {
                        task.action()
                    }
                }
            } finally {
                isThreadRunning = false
            }
        }
    }

    fun clearQueue() {
        queue.clear()
    }

    override fun updateDefinition(def: PsiLocatedReferable, file: ArendFile, isExternalUpdate: Boolean) {
        clearQueue()
    }
}