package org.arend.psi.listener

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile


class ArendPsiListenerService(private val project: Project) {
    private val listeners = HashSet<ArendPsiListener>()

    fun addListener(listener: ArendPsiListener) {
        listeners.add(listener)
        PsiManager.getInstance(project).addPsiTreeChangeListener(listener)
    }

    fun removeListener(listener: ArendPsiListener) {
        PsiManager.getInstance(project).removePsiTreeChangeListener(listener)
        listeners.remove(listener)
    }

    fun processEvent(file: ArendFile, child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean) {
        for (listener in listeners) {
            listener.processParent(file, child, oldChild, newChild, parent, additionOrRemoval)
        }
    }

    fun externalUpdate(definition: ArendDefinition, file: ArendFile) {
        for (listener in listeners) {
            listener.updateDefinition(definition, file, true)
        }
    }
}