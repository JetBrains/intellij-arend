package org.arend.psi

import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore.KEY_MODULE
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.typechecking.error.ErrorService

val PsiElement.theOnlyChild: PsiElement?
    get() = firstChild?.takeIf { it.nextSibling == null }

val PsiElement.linearDescendants: Sequence<PsiElement>
    get() = generateSequence(this) { it.theOnlyChild }

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { if (it is PsiFile) null else it.parent }

val PsiElement.childrenWithLeaves: Sequence<PsiElement>
    get() = generateSequence(firstChild) { it.nextSibling }

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

fun PsiElement.navigate(requestFocus: Boolean = true) {
    val descriptor = EditSourceUtil.getDescriptor(this)
    if (descriptor?.canNavigate() == true) {
        descriptor.navigate(requestFocus)
    }
}

val PsiElement.module: Module?
    get() {
        val file = containingFile ?: return null
        val virtualFile = file.originalFile.virtualFile ?: return getUserData(KEY_MODULE)
        return ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile)
    }

val PsiFile.libraryConfig: LibraryConfig?
    get() {
        val enforcedConfig = (this as? ArendFile)?.enforcedLibraryConfig
        if (enforcedConfig != null) return enforcedConfig
        val virtualFile = originalFile.virtualFile ?: return null
        val module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile)
        return if (module != null) ArendModuleConfigService.getInstance(module) else null
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
    val file = (parent ?: child ?: oldChild)?.containingFile as? ArendFile ?: return
    file.project.service<ArendPsiChangeService>().processEvent(file, child, oldChild, newChild, parent, additionOrRemoval)
}

private fun notifyRange(firstChild: PsiElement, lastChild: PsiElement, parent: PsiElement) {
    val file = parent.containingFile as? ArendFile ?: return
    val service = file.project.service<ArendPsiChangeService>()

    var child: PsiElement? = firstChild
    while (child != lastChild && child != null) {
        service.processEvent(file, child, null, null, parent, true)
        child = child.nextSibling
    }
    service.processEvent(file, lastChild, null, null, parent, true)
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

fun PsiElement.replaceWithNotification(newElement: PsiElement): PsiElement {
    notify(null, this, newElement, parent, false)
    return this.replace(newElement)
}

fun PsiElement.deleteWithNotification() {
    notify(this, null, null, parent, true)
    this.delete()
}

fun PsiElement.deleteChildRangeWithNotification(firstChild: PsiElement, lastChild: PsiElement) {
    this.deleteChildRange(firstChild, lastChild)
    notify(this, null, null, parent, true)
}

fun PsiElement.addRangeAfterWithNotification(firstElement: PsiElement, lastElement: PsiElement, anchor: PsiElement): PsiElement {
    val result = this.addRangeAfter(firstElement, lastElement, anchor)
    notify(this, null, null, parent, true)
    return result
}

fun PsiElement.addRangeBeforeWithNotification(firstElement: PsiElement, lastElement: PsiElement, anchor: PsiElement): PsiElement {
    val result = this.addRangeBefore(firstElement, lastElement, anchor)
    notify(this, null, null, parent, true)
    return result
}

fun getArendNameText(element: PsiElement?): String? = when (element) {
    is LeafPsiElement -> when (element.elementType) {
        ID -> element.text
        POSTFIX -> element.text.removePrefix("`")
        INFIX -> element.text.removeSurrounding("`")
        else -> null
    }
    is ArendRefIdentifier -> getArendNameText(element.id)
    is ArendIPName -> when {
        (element.infix != null) -> getArendNameText(element.infix)
        (element.postfix != null) -> getArendNameText(element.postfix)
        else -> null
    }
    is ArendDefIdentifier -> getArendNameText(element.id)
    is ArendFieldDefIdentifier -> getArendNameText(element.defIdentifier)
    else -> element?.text //fallback
}

fun getTeleType(tele: PsiElement?): ArendExpr? = when (tele) {
    is ArendNameTele -> tele.expr
    is ArendLamTele -> tele.expr
    is ArendTypedExpr -> tele.expr
    is ArendTypeTele -> tele.typedExpr?.expr
    is ArendFieldTele -> tele.expr
    else -> null
}

fun Editor.getSelectionWithoutErrors(): TextRange? =
    EditorUtil.getSelectionInAnyMode(this).takeIf { range ->
        if (range.isEmpty) {
            return@takeIf true
        }
        val nnProject = project ?: return@takeIf false
        val file = nnProject.let { PsiDocumentManager.getInstance(it).getPsiFile(document) } ?: return@takeIf false
        val elementsWithErrors = nnProject.service<ErrorService>().errors[file]?.mapNotNull { it.cause } ?: return@takeIf true
        elementsWithErrors.all { !range.intersects(it.textRange) }
    }
