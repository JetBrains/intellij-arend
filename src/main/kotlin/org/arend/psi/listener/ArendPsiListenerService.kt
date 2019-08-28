package org.arend.psi.listener

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager


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

    fun processEvent(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean) {
        for (listener in listeners) {
            listener.processParent(child, oldChild, newChild, parent, additionOrRemoval)
        }
    }
}