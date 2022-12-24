package org.arend.psi.listener

import com.intellij.openapi.util.SimpleModificationTracker
import org.arend.psi.ArendFile
import org.arend.psi.ext.*


class ArendPsiChangeService {
    private val listeners = HashSet<ArendDefinitionChangeListener>()
    val modificationTracker = SimpleModificationTracker()
    val definitionModificationTracker = SimpleModificationTracker()

    fun incModificationCount(withDef: Boolean = true) {
        modificationTracker.incModificationCount()
        if (withDef) {
            definitionModificationTracker.incModificationCount()
        }
    }

    fun addListener(listener: ArendDefinitionChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: ArendDefinitionChangeListener) {
        listeners.remove(listener)
    }

    fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean) {
        if (!isExternalUpdate) {
            definitionModificationTracker.incModificationCount()
        }
        for (listener in listeners) {
            listener.updateDefinition(def, file, isExternalUpdate)
        }
    }
}