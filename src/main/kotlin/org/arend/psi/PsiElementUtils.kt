package org.arend.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore.KEY_MODULE
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.arend.findExternalLibrary
import org.arend.mapFirstNotNull
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.scope.LexicalScope
import org.arend.prelude.Prelude
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendPsiListenerService
import org.arend.typechecking.TypeCheckingService

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { if (it is PsiFile) null else it.parent }

inline fun <reified T : PsiElement> PsiElement.ancestor(): T? {
    var element: PsiElement? = this
    while (element != null && element !is T && element !is PsiFile) {
        element = element.parent
    }
    return element as? T
}

inline fun <reified T : PsiElement> PsiElement.rightSibling(): T? {
    var element: PsiElement? = nextSibling
    while (element != null && element !is T) {
        element = element.nextSibling
    }
    return element as? T
}

inline fun <reified T : PsiElement> PsiElement.leftSibling(): T? {
    var element: PsiElement? = prevSibling
    while (element != null && element !is T) {
        element = element.prevSibling
    }
    return element as? T
}

val PsiElement.module: Module?
    get() {
        val file = containingFile
        val virtualFile = file.virtualFile ?: file.originalFile.virtualFile ?: return getUserData(KEY_MODULE)
        return ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile)
    }

val PsiElement.libraryConfig: LibraryConfig?
    get() {
        val file = containingFile
        val virtualFile = file.virtualFile ?: file.originalFile.virtualFile ?: return null
        val project = project
        val fileIndex = ProjectFileIndex.SERVICE.getInstance(project)

        val module = fileIndex.getModuleForFile(virtualFile)
        if (module != null) {
            return ArendModuleConfigService.getInstance(module)
        }

        if (!fileIndex.isInLibrarySource(virtualFile)) {
            return null
        }
        for (orderEntry in fileIndex.getOrderEntriesForFile(virtualFile)) {
            if (orderEntry is LibraryOrderEntry) {
                val name = orderEntry.library?.let { TypeCheckingService.getInstance(project).getLibraryName(it) } ?: continue
                val conf = project.findExternalLibrary(name)
                if (conf != null) {
                    return conf
                }
            }
        }
        return null
    }

val PsiElement.moduleScopeProvider: ModuleScopeProvider
    get() {
        val containingFile = containingFile
        val config = containingFile.libraryConfig
        val typecheckingService = TypeCheckingService.getInstance(containingFile.project)
        return ModuleScopeProvider { modulePath ->
            val file = if (modulePath == Prelude.MODULE_PATH) {
                typecheckingService.prelude
            } else {
                if (config == null) {
                    typecheckingService.libraryManager.registeredLibraries.mapFirstNotNull { it.getModuleGroup(modulePath) }
                } else {
                    config.forAvailableConfigs { it.findArendFile(modulePath) }
                }
            }
            file?.let { LexicalScope.opened(it) }
        }
    }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

fun ArendGroup.findGroupByFullName(fullName: List<String>): ArendGroup? =
        if (fullName.isEmpty()) this else (subgroups.find { it.referable.textRepresentation() == fullName[0] }
                ?: dynamicSubgroups.find { it.referable.textRepresentation() == fullName[0] })?.findGroupByFullName(fullName.drop(1))

fun PsiElement.findNextSibling(): PsiElement? = findNextSibling(null)

fun PsiElement.findNextSibling(punctuationType: IElementType?): PsiElement? {
    var sibling: PsiElement? = nextSibling
    while (sibling is PsiComment || sibling is PsiWhiteSpace ||
            (punctuationType != null && sibling != null && sibling.node.elementType == punctuationType)) sibling = sibling.nextSibling
    return sibling
}

fun PsiElement?.findNextSibling(pred: (PsiElement) -> Boolean): PsiElement? {
    var next = this
    while (next != null) {
        if (pred(next)) {
            return next
        }
        next = next.nextSibling
    }
    return next
}

fun PsiElement.findPrevSibling(): PsiElement? = findPrevSibling(null)

fun PsiElement.findPrevSibling(punctuationType: IElementType?): PsiElement? {
    var sibling: PsiElement? = prevSibling
    while (sibling is PsiComment || sibling is PsiWhiteSpace ||
            (punctuationType != null && sibling != null && sibling.node.elementType == punctuationType)) sibling = sibling.prevSibling
    return sibling
}

fun PsiElement?.findPrevSibling(pred: (PsiElement) -> Boolean): PsiElement? {
    var prev = this
    while (prev != null) {
        if (pred(prev)) {
            return prev
        }
        prev = prev.prevSibling
    }
    return prev
}

/** Returns the last irrelevant element (i.e., whitespace or comment) to the right of the given element
 *  or the element itself if there are no irrelevant elements
  */
val PsiElement.extendRight: PsiElement
    get() {
        var result = this
        var next = nextSibling
        while (next is PsiWhiteSpace || next is PsiComment) {
            result = next
            next = next.nextSibling
        }
        return result
    }

/** Returns the last irrelevant element (i.e., whitespace or comment) to the left of the given element
 *  or the element itself if there are no irrelevant elements
 */
val PsiElement.extendLeft: PsiElement
    get() {
        var result = this
        var prev = prevSibling
        while (prev is PsiWhiteSpace || prev is PsiComment) {
            result = prev
            prev = prev.prevSibling
        }
        return result
    }

val PsiElement.nextElement: PsiElement?
    get() {
        var current: PsiElement? = this
        while (current != null && current !is PsiFile) {
            current.nextSibling?.let {
                return it
            }
            current = current.parent
        }
        return null
    }

val PsiElement.prevElement: PsiElement?
    get() {
        var current: PsiElement? = this
        while (current != null && current !is PsiFile) {
            current.prevSibling?.let {
                return it
            }
            current = current.parent
        }
        return null
    }

val PsiElement.oneLineText: String
    get() {
        val text = text
        return if (!text.contains('\n')) {
            text
        } else buildString {
            var start = 0
            var i = 0
            while (i < text.length) {
                if (text[i++] != '\n') continue
                var j = i - 1
                while (j >= start) {
                    if (!text[j].isWhitespace()) break
                    j--
                }
                if (j + 1 > start) {
                    if (start > 0) {
                        append(' ')
                    }
                    append(text.substring(start, j + 1))
                }
                i++
                while (i < text.length) {
                    if (!text[i].isWhitespace()) break
                    i++
                }
                start = i
            }
            if (start < text.length) {
                append(' ')
                append(text.substring(start, text.length))
            }
        }
    }

enum class PositionKind {
    BEFORE_ANCHOR, AFTER_ANCHOR, INSIDE_EMPTY_ANCHOR
}

class RelativePosition(val kind: PositionKind, val anchor: PsiElement) : Comparable<RelativePosition> {
    override fun compareTo(other: RelativePosition): Int {
        if (kind == PositionKind.INSIDE_EMPTY_ANCHOR && other.kind == PositionKind.INSIDE_EMPTY_ANCHOR)
            return 0
        if (kind == PositionKind.INSIDE_EMPTY_ANCHOR)
            return -1
        if (other.kind == PositionKind.INSIDE_EMPTY_ANCHOR)
            return 1
        val anchorOfs = anchor.textOffset
        val otherOfs = other.anchor.textOffset
        if (anchorOfs < otherOfs) return -1
        if (anchorOfs > otherOfs) return 1
        if (kind == other.kind) return 0
        if (kind == PositionKind.BEFORE_ANCHOR) return -1
        return 1
    }
}

fun PsiElement.deleteAndGetPosition(): RelativePosition? {
    val pS = this.findPrevSibling()
    val nS = this.findNextSibling()
    val result: RelativePosition? = when {
        pS != null -> RelativePosition(PositionKind.AFTER_ANCHOR, pS)
        nS != null -> RelativePosition(PositionKind.BEFORE_ANCHOR, nS)
        else -> this.parent?.let { RelativePosition(PositionKind.INSIDE_EMPTY_ANCHOR, it) }
    }
    this.deleteWithNotification()
    return result
}

private fun notify(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean) {
    val project = child?.project ?: oldChild?.project ?: return
    ArendPsiListenerService.getInstance(project).processEvent(child, oldChild, newChild, parent, additionOrRemoval)
}

private fun notifyRange(firstChild: PsiElement, lastChild: PsiElement, parent: PsiElement) {
    val project = parent.project
    val tcService = ArendPsiListenerService.getInstance(project)

    var child: PsiElement? = firstChild
    while (child != lastChild && child != null) {
        tcService.processEvent(child, null, null, parent, true)
        child = child.nextSibling
    }
}

fun PsiElement.addBeforeWithNotification(element: PsiElement, anchor: PsiElement?): PsiElement {
    notify(element, null, null, this, true)
    return this.addBefore(element, anchor)
}

fun PsiElement.addAfterWithNotification(element: PsiElement, anchor: PsiElement?): PsiElement {
    notify(element, null, null, this, true)
    return this.addAfter(element, anchor)
}

fun PsiElement.addWithNotification(element: PsiElement): PsiElement {
    notify(element, null, null, this, true)
    return this.add(element)
}

fun PsiElement.replaceWithNotification(newElement: PsiElement) {
    notify(null, this, newElement, parent, false)
    this.replace(newElement)
}

fun PsiElement.deleteWithNotification() {
    notify(this, null, null, parent, true)
    this.delete()
}

fun PsiElement.deleteChildRangeWithNotification(firstChild: PsiElement, lastChild: PsiElement) {
    notifyRange(firstChild, lastChild, this)
    this.deleteChildRange(firstChild, lastChild)
}

fun PsiElement.addRangeAfterWithNotification(firstElement: PsiElement, lastElement: PsiElement, anchor: PsiElement): PsiElement {
    notifyRange(firstElement, lastElement, this)
    return this.addRangeAfter(firstElement, lastElement, anchor)
}