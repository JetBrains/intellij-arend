package com.jetbrains.arend.ide.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.*
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.arend.ide.module.ModuleScope
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.resolving.ArdReference
import com.jetbrains.arend.ide.typing.ModifiedClassFieldImplScope
import com.jetbrains.jetpad.vclang.error.SourceInfo
import com.jetbrains.jetpad.vclang.naming.reference.DataContainer
import com.jetbrains.jetpad.vclang.naming.scope.ClassFieldImplScope
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.abs.Abstract

interface ArdCompositeElement : PsiElement, SourceInfo {
    val scope: Scope
    override fun getReference(): ArdReference?
}

fun PsiElement.moduleTextRepresentationImpl(): String? = (containingFile as? ArdFile)?.name

fun PsiElement.positionTextRepresentationImpl(): String? {
    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile ?: return null) ?: return null
    val offset = textOffset
    val line = document.getLineNumber(offset)
    val column = offset - document.getLineStartOffset(line)
    return (line + 1).toString() + ":" + (column + 1).toString()
}

interface ArdSourceNode : ArdCompositeElement, Abstract.SourceNode {
    override fun getTopmostEquivalentSourceNode(): ArdSourceNode
    override fun getParentSourceNode(): ArdSourceNode?
}

private fun getArdScope(element: ArdCompositeElement): Scope {
    val sourceNode = element.ancestors.filterIsInstance<ArdSourceNode>().firstOrNull()?.topmostEquivalentSourceNode
            ?: return (element.containingFile as? ArdFile)?.scope ?: EmptyScope.INSTANCE
    val scope = ScopeFactory.forSourceNode(sourceNode.parentSourceNode?.scope
            ?: (sourceNode.containingFile as? ArdFile)?.scope
            ?: EmptyScope.INSTANCE, sourceNode, sourceNode.module?.let { ModuleScope(it) })
    if (scope is ClassFieldImplScope && scope.withSuperClasses()) {
        val classRef = scope.classReference
        if (classRef is ArdDefClass) {
            return ModifiedClassFieldImplScope(classRef, sourceNode.parentSourceNode?.parentSourceNode as? Abstract.ClassReferenceHolder)
        }
    }
    return scope
}

fun getTopmostEquivalentSourceNode(sourceNode: ArdSourceNode): ArdSourceNode {
    var current = sourceNode
    while (true) {
        val parent = current.parent
        if (current is Abstract.Expression != parent is Abstract.Expression) {
            return current
        }
        current = when {
            parent is ArdLiteral -> parent
            parent is ArdAtom -> parent
            parent is ArdTuple && parent.tupleExprList.size == 1 && parent.tupleExprList[0].exprList.size == 1 -> parent
            parent is ArdNewExpr && parent.newKw == null && parent.lbrace == null && parent.argumentList.isEmpty() -> parent
            parent is ArdAtomFieldsAcc && parent.fieldAccList.isEmpty() -> parent
            parent is ArdArgumentAppExpr && parent.argumentList.isEmpty() -> parent
            parent is ArdLongName && parent.refIdentifierList.size == 1 -> parent
            parent is ArdLevelExpr && parent.sucKw == null && parent.maxKw == null -> parent
            parent is ArdAtomLevelExpr && parent.lparen != null -> parent
            parent is ArdOnlyLevelExpr && parent.sucKw == null && parent.maxKw == null -> parent
            parent is ArdAtomOnlyLevelExpr && parent.lparen != null -> parent
            else -> return current
        }
    }
}

fun getParentSourceNode(sourceNode: ArdSourceNode): ArdSourceNode? {
    val parent = sourceNode.topmostEquivalentSourceNode.parent
    return parent as? ArdFile ?: parent.ancestors.filterIsInstance<ArdSourceNode>().firstOrNull()
}

private class SourceInfoErrorData(cause: PsiErrorElement) : Abstract.ErrorData(SmartPointerManager.createPointer(cause), cause.errorDescription), SourceInfo, DataContainer {
    override fun getData(): Any = cause

    override fun moduleTextRepresentation(): String? = runReadAction { (cause as SmartPsiElementPointer<*>).element?.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { (cause as SmartPsiElementPointer<*>).element?.positionTextRepresentationImpl() }
}

fun getErrorData(element: ArdCompositeElement): Abstract.ErrorData? =
        element.children.filterIsInstance<PsiErrorElement>().firstOrNull()?.let { SourceInfoErrorData(it) }

abstract class ArdCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), ArdCompositeElement {
    override val scope
        get() = getArdScope(this)

    override fun getReference(): ArdReference? = null

    override fun moduleTextRepresentation(): String? = runReadAction { moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { positionTextRepresentationImpl() }
}

abstract class ArdSourceNodeImpl(node: ASTNode) : ArdCompositeElementImpl(node), ArdSourceNode {
    override fun getTopmostEquivalentSourceNode() = com.jetbrains.arend.ide.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = com.jetbrains.arend.ide.psi.ext.getParentSourceNode(this)

    override fun getErrorData(): Abstract.ErrorData? = com.jetbrains.arend.ide.psi.ext.getErrorData(this)
}

abstract class ArdStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, ArdSourceNode {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope
        get() = getArdScope(this)

    override fun getReference(): ArdReference? = null

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    override fun moduleTextRepresentation(): String? = runReadAction { moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { positionTextRepresentationImpl() }

    override fun getTopmostEquivalentSourceNode() = com.jetbrains.arend.ide.psi.ext.getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = com.jetbrains.arend.ide.psi.ext.getParentSourceNode(this)

    override fun getErrorData(): Abstract.ErrorData? = com.jetbrains.arend.ide.psi.ext.getErrorData(this)
}
