package org.arend.psi

import com.intellij.ide.util.EditSourceUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore.KEY_MODULE
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.module.scopeprovider.EmptyModuleScopeProvider
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.scope.LexicalScope
import org.arend.prelude.Prelude
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendDefinitionChangeListenerService
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.arend.util.mapFirstNotNull
import org.jetbrains.yaml.psi.YAMLFile
import java.util.ArrayList

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
        val virtualFile = file.virtualFile ?: file.originalFile.virtualFile ?: return getUserData(KEY_MODULE)
        return ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile)
    }

fun PsiElement.getLibraryConfig(onlyInternal: Boolean): LibraryConfig? {
    val containingFile = containingFile ?: return null
    val virtualFile = containingFile.virtualFile ?: containingFile.originalFile.virtualFile ?: return null
    val project = project
    val fileIndex = ProjectFileIndex.SERVICE.getInstance(project)

    val module = fileIndex.getModuleForFile(virtualFile)
    if (module != null) {
        return ArendModuleConfigService.getInstance(module)
    }

    if (onlyInternal || !fileIndex.isInLibrarySource(virtualFile)) {
        return null
    }

    for (orderEntry in fileIndex.getOrderEntriesForFile(virtualFile)) {
        if (orderEntry is LibraryOrderEntry) {
            for (file in orderEntry.getRootFiles(ArendConfigOrderRootType)) {
                val yaml = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: continue
                if (yaml.name != FileUtils.LIBRARY_CONFIG_FILE) {
                    continue
                }
                val name = yaml.virtualFile?.parent?.name ?: continue
                return ExternalLibraryConfig(name, yaml)
            }
        }
    }

    return null
}

val PsiElement.libraryConfig: LibraryConfig?
    get() = getLibraryConfig(false)

val PsiElement.moduleScopeProvider: ModuleScopeProvider
    get() {
        val containingFile = containingFile ?: return EmptyModuleScopeProvider.INSTANCE
        val config = containingFile.libraryConfig
        val typecheckingService = containingFile.project.service<TypeCheckingService>()
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

fun getDocumentation(statement: ArendStatement): List<PsiElement> {
    val result = ArrayList<PsiElement>()
    var prev0: PsiElement? = statement.prevSibling
    if (prev0 is PsiWhiteSpace) prev0 = prev0.prevSibling
    val eT = prev0?.node?.elementType
    if (eT == ArendElementTypes.LINE_DOC_TEXT) {
        val prev1 = prev0?.prevSibling
        val eT1 = prev1?.node?.elementType
        if (eT1 == ArendElementTypes.LINE_DOC_COMMENT_START) {
            result.add(prev1!!)
            result.add(prev0!!)
        }
    }

    if (eT == ArendElementTypes.BLOCK_COMMENT_END) {
        val prev1 = prev0?.prevSibling
        val eT1 = prev1?.node?.elementType
        val prev2 = prev1?.prevSibling
        val eT2 = prev2?.node?.elementType
        if (eT1 == ArendElementTypes.BLOCK_DOC_TEXT && eT2 == ArendElementTypes.BLOCK_DOC_COMMENT_START) {
            result.add(prev2!!)
            result.add(prev1)
            result.add(prev0!!)
        }
    }
    return result
}

private fun notify(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean) {
    val file = (parent ?: child ?: oldChild)?.containingFile as? ArendFile ?: return
    file.project.service<ArendDefinitionChangeListenerService>().processEvent(file, child, oldChild, newChild, parent, additionOrRemoval)
}

private fun notifyRange(firstChild: PsiElement, lastChild: PsiElement, parent: PsiElement) {
    val file = parent.containingFile as? ArendFile ?: return
    val service = file.project.service<ArendDefinitionChangeListenerService>()

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
    notifyRange(firstChild, lastChild, this)
    this.deleteChildRange(firstChild, lastChild)
}

fun PsiElement.addRangeAfterWithNotification(firstElement: PsiElement, lastElement: PsiElement, anchor: PsiElement): PsiElement {
    notifyRange(firstElement, lastElement, this)
    return this.addRangeAfter(firstElement, lastElement, anchor)
}