package org.arend.psi.listener

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.util.application
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.typechecking.TypeCheckingService


@Service
class ArendPsiChangeService {
    private val listeners = HashSet<ArendDefinitionChangeListener>()
    val definitionModificationTracker = SimpleModificationTracker()

    init {
        application.messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
            override fun projectClosing(project: Project) {
                removeListener(project.service<TypeCheckingService>())
            }
        })
    }

    fun incModificationCount() {
        definitionModificationTracker.incModificationCount()
    }

    fun addListener(listener: ArendDefinitionChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ArendDefinitionChangeListener) {
        listeners.remove(listener)
    }

    fun updateDefinition(def: PsiLocatedReferable, file: ArendFile, isExternalUpdate: Boolean) {
        synchronized(listeners) {
            for (listener in listeners) {
                listener.updateDefinition(def, file, isExternalUpdate)
            }
        }
    }
}
