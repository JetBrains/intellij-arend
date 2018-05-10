package org.vclang.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.*
import org.vclang.resolving.VcReference

interface VcCompositeElement : PsiElement, SourceInfo {
    val scope: Scope
    override fun getReference(): VcReference?
}

fun PsiElement.moduleTextRepresentationImpl(): String? = (containingFile as? VcFile)?.name

fun PsiElement.positionTextRepresentationImpl(): String? {
    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile ?: return null) ?: return null
    val offset = textOffset
    val line = document.getLineNumber(offset)
    val column = offset - document.getLineStartOffset(line)
    return (line + 1).toString() + ":" + (column + 1).toString()
}

interface VcSourceNode: VcCompositeElement, Abstract.SourceNode {
    override fun getTopmostEquivalentSourceNode(): VcSourceNode
    override fun getParentSourceNode(): VcSourceNode?
}

private fun getVcScope(element: VcCompositeElement): Scope {
    val sourceNode = element.ancestors.filterIsInstance<VcSourceNode>().firstOrNull()?.topmostEquivalentSourceNode ?: return (element.containingFile as? VcFile)?.scope ?: EmptyScope.INSTANCE
    return ScopeFactory.forSourceNode(sourceNode.parentSourceNode?.scope ?: (sourceNode.containingFile as? VcFile)?.scope ?: EmptyScope.INSTANCE, sourceNode)
}

fun getTopmostEquivalentSourceNode(sourceNode: VcSourceNode): VcSourceNode {
    var current = sourceNode
    while (true) {
        val parent = current.parent
        if (current is Abstract.Expression != parent is Abstract.Expression) {
            return current
        }
        current = when {
            parent is VcLiteral -> parent
            parent is VcAtom -> parent
            parent is VcTuple -> parent
            parent is VcNewExpr && parent.newKw == null && parent.lbrace == null && parent.argumentList.isEmpty() -> parent
            parent is VcAtomFieldsAcc && parent.fieldAccList.isEmpty() -> parent //&& parent.parent is Abstract.Expression -> parent
            parent is VcLongName && parent.refIdentifierList.size == 1 -> parent
            parent is VcLevelExpr && parent.sucKw == null && parent.maxKw == null -> parent
            parent is VcAtomLevelExpr && parent.lparen != null -> parent
            parent is VcOnlyLevelExpr && parent.sucKw == null && parent.maxKw == null -> parent
            parent is VcAtomOnlyLevelExpr && parent.lparen != null -> parent
            else -> return current
        }
    }
}

fun getParentSourceNode(sourceNode: VcSourceNode): VcSourceNode? {
    val parent = sourceNode.topmostEquivalentSourceNode.parent
    return if (parent is VcFile) null else parent.ancestors.filterIsInstance<VcSourceNode>().firstOrNull()
}

abstract class VcCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), VcCompositeElement  {
    override val scope
        get() = getVcScope(this)

    override fun getReference(): VcReference? = null

    override fun moduleTextRepresentation(): String? = moduleTextRepresentationImpl()

    override fun positionTextRepresentation(): String? = positionTextRepresentationImpl()
}

abstract class VcSourceNodeImpl(node: ASTNode) : VcCompositeElementImpl(node), VcSourceNode {
    override fun getTopmostEquivalentSourceNode() = org.vclang.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = org.vclang.psi.ext.getParentSourceNode(this)
}

abstract class VcStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, VcSourceNode {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope
        get() = getVcScope(this)

    override fun getReference(): VcReference? = null

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    override fun moduleTextRepresentation(): String? = moduleTextRepresentationImpl()

    override fun positionTextRepresentation(): String? = positionTextRepresentationImpl()

    override fun getTopmostEquivalentSourceNode() = org.vclang.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = org.vclang.psi.ext.getParentSourceNode(this)
}
