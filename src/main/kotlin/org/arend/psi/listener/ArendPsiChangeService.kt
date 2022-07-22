package org.arend.psi.listener

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.*
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.castSafelyTo
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.resolving.ArendResolveCache


class ArendPsiChangeService(project: Project) : PsiTreeChangeAdapter() {
    private val resolveCache = project.service<ArendResolveCache>()
    private val listeners = HashSet<ArendDefinitionChangeListener>()
    val modificationTracker = SimpleModificationTracker()
    val definitionModificationTracker = SimpleModificationTracker()

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(this, project)
    }

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

    fun processEvent(file: ArendFile, child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean) {
        for (listener in listeners) {
            processParent(file, child, oldChild, newChild, parent, additionOrRemoval)
        }
    }

    fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean) {
        if (!isExternalUpdate) {
            definitionModificationTracker.incModificationCount()
        }
        for (listener in listeners) {
            listener.updateDefinition(def, file, isExternalUpdate)
        }
    }

    private fun incModificationCount(file: PsiFile?): Boolean =
        if (file is ArendFile && file.isWritable) {
            modificationTracker.incModificationCount()
            true
        } else false

    private fun checkGroup(group: ArendGroup, file: ArendFile) {
        if (!group.checkTCReferable()) {
            if (group is PsiConcreteReferable) updateDefinition(group, file, false)
            group.dropTCReferable()
        }

        for (statement in group.statements) {
            checkGroup(statement.group ?: continue, file)
        }
        for (subgroup in group.dynamicSubgroups) {
            checkGroup(subgroup, file)
        }
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
        if (event !is PsiTreeChangeEventImpl || !event.isGenericChange) {
            val file = event.parent as? ArendFile ?: return
            if (!file.isWritable) return
            checkGroup(file, file)
        }
        modificationTracker.incModificationCount()
        event.file.castSafelyTo<ArendFile>()?.cleanupTCRefMaps()
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
        incModificationCount(event.file)
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
        processParent(event, true)
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
        processParent(event, false)
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
        processParent(event, false)
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        val child = event.child
        if (child is ArendFile) { // whole file has been removed
            invalidateChildren(child, child)
        } else {
            processParent(event, true)
        }
    }

    private fun isDynamicDef(elem: PsiElement?) = elem is ArendClassStat && (elem.definition != null || elem.defModule != null)

    private fun processParent(event: PsiTreeChangeEvent, checkCommentStart: Boolean) {
        val file = event.file as? ArendFile ?: return
        file.cleanupTCRefMaps()
        processChildren(event.child, file)
        processChildren(event.oldChild, file)
        processParent(file, event.child, event.oldChild, event.newChild, event.parent ?: event.oldParent, checkCommentStart)
    }

    private fun processParent(file: ArendFile, child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, checkCommentStart: Boolean) {
        if (parent is ArendReferenceElement) {
            resolveCache.dropCache(parent)
        }

        if (child is PsiErrorElement ||
            child is PsiWhiteSpace ||
            child is ArendWhere ||
            isDynamicDef(child) ||
            child is LeafPsiElement && AREND_COMMENTS.contains(child.node.elementType)) {
            return
        }
        if (oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
            (oldChild is ArendWhere || oldChild is PsiErrorElement || isDynamicDef(oldChild)) && (newChild is ArendWhere || newChild is PsiErrorElement || isDynamicDef(newChild)) ||
            oldChild is LeafPsiElement && AREND_COMMENTS.contains(oldChild.node.elementType) && newChild is LeafPsiElement && AREND_COMMENTS.contains(newChild.node.elementType)) {
            return
        }

        if (!incModificationCount(file)) {
            return
        }

        if (checkCommentStart) {
            var node = (child as? ArendCompositeElement)?.node ?: child as? LeafPsiElement
            while (node != null && node !is LeafPsiElement) {
                val first = node.firstChildNode
                if (first == null || node.lastChildNode != first) {
                    break
                }
                node = first
            }
        }

        ((parent as? ArendDefIdentifier)?.parent as? ArendGroup)?.let { invalidateChildren(it, file) }

        var elem = parent
        while (elem != null) {
            if (elem is ArendWhere || elem is ArendFile || isDynamicDef(elem)) {
                return
            }
            if (elem is PsiConcreteReferable) {
                updateDefinition(elem, file, false)
                return
            }
            elem = elem.parent
        }
    }

    private fun invalidateChildren(group: ArendGroup, file: ArendFile) {
        if (group is PsiConcreteReferable) {
            updateDefinition(group, file, false)
        }
        for (statement in group.statements) {
            invalidateChildren(statement.group ?: continue, file)
        }
        for (subgroup in group.dynamicSubgroups) {
            invalidateChildren(subgroup, file)
        }
    }

    private fun processChildren(element: PsiElement?, file: ArendFile) {
        when (element) {
            is ArendGroup -> invalidateChildren(element, file)
            is ArendStat -> {
                element.statCmd?.let { invalidateChildren(file, file) }
                element.definition?.let { invalidateChildren(it, file) }
                element.defModule?.let { invalidateChildren(it, file) }
            }
        }
    }
}