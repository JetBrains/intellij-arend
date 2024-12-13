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
import com.intellij.psi.util.descendants
import com.intellij.psi.util.elementType
import com.intellij.psi.util.startOffset
import com.intellij.util.SmartList
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.Referable
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
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
        val file = (if (this is ArendExpressionCodeFragment) this.context?.containingFile else containingFile) ?: return null
        val virtualFile = file.originalFile.virtualFile ?: return getUserData(KEY_MODULE)
        return ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
    }

val PsiFile.libraryConfig: LibraryConfig?
    get() {
        val enforcedConfig = (this as? ArendFile)?.enforcedLibraryConfig
        if (enforcedConfig != null) return enforcedConfig
        val virtualFile = originalFile.virtualFile ?: return null
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(virtualFile)
        return if (module != null) ArendModuleConfigService.getInstance(module) else null
    }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.descendantOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

fun <T : PsiElement> PsiElement.childOfType(clazz: Class<T>, index: Int): T? {
    var i = 0
    var child = firstChild
    while (child != null) {
        if (clazz.isInstance(child) && i++ == index) {
            return clazz.cast(child)
        }
        child = child.nextSibling
    }
    return null
}

inline fun <reified T : PsiElement> PsiElement.childOfType(index: Int): T? = childOfType(T::class.java, index)

inline fun <reified T : PsiElement> PsiElement.childOfType(): T? = PsiTreeUtil.getChildOfType(this, T::class.java)

inline fun <reified T : PsiElement> PsiElement.childOfTypeStrict(): T = PsiTreeUtil.getChildOfType(this, T::class.java)!!

inline fun <reified T : PsiElement> PsiElement.getChildrenOfType(): List<T> = PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)

fun PsiElement.childOfType(type: IElementType): PsiElement? = node.findChildByType(type)?.psi

fun PsiElement.childOfTypeStrict(type: IElementType): PsiElement = node.findChildByType(type)!!.psi

fun PsiElement?.hasChildOfType(type: IElementType): Boolean = this?.node?.findChildByType(type)?.psi != null

fun PsiElement.getChildrenOfType(type: IElementType): List<PsiElement> {
    var result: MutableList<PsiElement>? = null
    var child = firstChild
    while (child != null) {
        if (child.elementType == type) {
            if (result == null) result = SmartList()
            result.add(child)
        }
        child = child.nextSibling
    }
    return result ?: emptyList()
}

fun <T : PsiElement> PsiElement.getChild(clazz: Class<T>, pred : (T) -> Boolean): T? {
    var child = firstChild
    while (child != null) {
        if (clazz.isInstance(child)) {
            val c = clazz.cast(child)
            if (pred(c)) return c
        }
        child = child.nextSibling
    }
    return null
}

inline fun <reified T : PsiElement> PsiElement.getChild(noinline pred : (T) -> Boolean): T? = getChild(T::class.java, pred)

val PsiElement.firstRelevantChild: PsiElement?
    get() {
        var child = firstChild
        while (child is PsiWhiteSpace || child is PsiComment) {
            child = child.nextSibling
        }
        return child
    }

fun ArendGroup.findGroupByFullName(fullName: List<String>): ArendGroup? =
        if (fullName.isEmpty()) this else (statements.find { it.group?.referable?.refName == fullName[0] }?.group
                ?: dynamicSubgroups.find { it.referable.refName == fullName[0] })?.findGroupByFullName(fullName.drop(1))

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
    return null
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
    return null
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

enum class SpaceDirection {TrailingSpace, LeadingSpace}

fun PsiElement?.getWhitespace(direction: SpaceDirection, includingPunctuation: Boolean = false): String? {
    val parent = this?.parent ?: return null
    val significantChildren = when (includingPunctuation) {
        true -> parent.children.toList() // This list typically omits punctuation like "," or ":"
        false -> parent.childrenWithLeaves.filter { it !is PsiWhiteSpace && it !is PsiComment }.toList()
    }
    var pointer = when (direction) {
        SpaceDirection.TrailingSpace -> this.nextSibling
        SpaceDirection.LeadingSpace -> this.prevSibling
    }
    var buffer = ""
    while (pointer != null && !significantChildren.contains(pointer)) {
        buffer = when (direction) {
            SpaceDirection.TrailingSpace -> buffer + pointer.text
            SpaceDirection.LeadingSpace -> pointer.text + buffer
        }
        pointer = when (direction) {
            SpaceDirection.TrailingSpace -> pointer.nextSibling
            SpaceDirection.LeadingSpace -> pointer.prevSibling
        }
    }
    return buffer
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
        val descendants = descendants()
        val ofs = startOffset
        val modifications = HashMap<TextRange, String>()
        descendants.filter { it is PsiWhiteSpace }.forEach { modifications[it.textRange] = " " }
        descendants.filter { it is PsiComment && it.text.startsWith("-- ") }.forEach { modifications[it.textRange] = "{-${it.text.drop(3)}-}" }
        var t = text
        modifications.keys.sortedBy { -it.startOffset }.forEach {
            t = t.replaceRange(it.startOffset - ofs, it.endOffset - ofs, modifications[it]!!)
        }

        return t
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
    this.delete()
    return result
}

fun PsiElement.deleteWithWhitespaces() {
    if (prevElement is PsiWhiteSpace) {
        val next = nextElement
        if (next is PsiWhiteSpace) {
            parent.deleteChildRange(this, next)
            return
        }
    }
    delete()
}

fun getArendNameText(element: PsiElement?): String? = when (element) {
    is LeafPsiElement -> when (element.elementType) {
        ID -> element.text
        POSTFIX -> element.text.removePrefix("`")
        INFIX -> element.text.removeSurrounding("`")
        else -> null
    }
    is ArendRefIdentifier -> getArendNameText(element.id)
    is ArendIPName -> getArendNameText(element.firstRelevantChild)
    is ArendDefIdentifier -> getArendNameText(element.id)
    is ArendFieldDefIdentifier -> getArendNameText(element.defIdentifier)
    else -> element?.text //fallback
}

fun getTeleType(tele: PsiElement?): ArendExpr? = when (tele) {
    is ArendNameTele -> tele.type
    is ArendTypedExpr -> tele.type
    is ArendTypeTele -> tele.typedExpr?.type
    is ArendFieldTele -> tele.type
    else -> null
}

fun getIdName(id: Referable): String? = when (id) {
    is ArendDefIdentifier -> id.name
    is ArendFieldDefIdentifier -> id.name
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

 fun parentArgumentAppExpr(atomFieldsAcc: ArendAtomFieldsAcc?): ArendArgumentAppExpr? =
        atomFieldsAcc?.parent?.let { if (it is ArendAtomArgument) it.parent else it } as? ArendArgumentAppExpr

fun isInDynamicPart(childPsi: PsiElement): ArendDefClass? {
    var psi: PsiElement? = childPsi
    var result = false
    while (psi != null) {
        if (psi is ArendClassStat || psi is ArendClassImplement) result = true
        if (psi is ArendDefClass) return if (result) psi else null
        psi = psi.parent
    }
    return null
}
/** Strips PSI of all surrounding braces and parentheses and returns the essential part + a flag indicating whether this part is an "atomic" expression
 */
fun getStrippedPsi(expression: PsiElement): Pair<PsiElement, Boolean> {
    var expr = expression
    stripLoop@while (true) {
        when (val exprData1 = expr) {
            is ArendImplicitArgument -> if (exprData1.tupleExprList.size == 1) {
                expr = exprData1.tupleExprList[0]; continue
            }
            is ArendAtomArgument -> {
                expr = exprData1.atomFieldsAcc; continue
            }
            is ArendAtomFieldsAcc -> if (exprData1.fieldAccList.isEmpty()) {
                expr = exprData1.atom; continue
            }
            is ArendTuple -> if (exprData1.tupleExprList.size == 1) {
                expr = exprData1.tupleExprList[0]; continue
            }
            is ArendTupleExpr -> if (exprData1.type == null) {
                expr = exprData1.expr; continue
            }
            is ArendAtom -> {
                val child = exprData1.firstRelevantChild
                if (child is ArendTuple || child is ArendLiteral) {
                    expr = child; continue
                }
            }
            is ArendNewExpr -> if (exprData1.appExpr?.let { it.textRange == exprData1.textRange } == true) {
                expr = exprData1.appExpr!!; continue
            }
            is ArendArgumentAppExpr -> if (exprData1.argumentList.isEmpty() && exprData1.atomFieldsAcc != null) {
                expr = exprData1.atomFieldsAcc!!; continue
            }
            is ArendPattern -> if ((exprData1.isTuplePattern || !exprData1.isExplicit) && exprData1.sequence.size == 1) {
                expr = exprData1.sequence[0]; continue
            } else if (!exprData1.isExplicit) {
                if (exprData1.referenceElement != null) {
                    expr = exprData1.referenceElement!!; continue
                } else if (exprData1.singleReferable != null) {
                    expr = exprData1.singleReferable!!; continue
                } else if (exprData1.underscore != null) {
                    expr = exprData1.underscore!!; continue
                }
            }
        }
        break
    }
    val isAtomic = expr is ArendAtom || expr is ArendDefIdentifier || expr is ArendLongName || expr is ArendTuple || expr is ArendLiteral ||
            expr.text == "_" || expr is ArendAtomFieldsAcc ||
            (expr is ArendPattern && (expr.referenceElement != null || expr.singleReferable != null || expr.isUnnamed || expr.isTuplePattern))

    return Pair(expr, isAtomic)
}