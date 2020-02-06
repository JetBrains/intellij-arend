package org.arend.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.ext.error.SourceInfo
import org.arend.module.ModuleScope
import org.arend.module.scopeprovider.EmptyModuleScopeProvider
import org.arend.naming.scope.*
import org.arend.psi.*
import org.arend.resolving.ArendReference
import org.arend.term.abs.Abstract
import org.arend.typechecking.TypeCheckingService
import org.arend.typing.ModifiedClassFieldImplScope

interface ArendCompositeElement : PsiElement, SourceInfo {
    val scope: Scope
    override fun getReference(): ArendReference?
}

fun PsiElement.moduleTextRepresentationImpl(): String? = (containingFile as? ArendFile)?.name

fun PsiElement.positionTextRepresentationImpl(): String? {
    val document = PsiDocumentManager.getInstance(project).getDocument(containingFile ?: return null) ?: return null
    val offset = textOffset
    val line = document.getLineNumber(offset)
    val column = offset - document.getLineStartOffset(line)
    return (line + 1).toString() + ":" + (column + 1).toString()
}

interface ArendSourceNode: ArendCompositeElement, Abstract.SourceNode {
    override fun getTopmostEquivalentSourceNode(): ArendSourceNode
    override fun getParentSourceNode(): ArendSourceNode?
}

private fun getArendScope(element: ArendCompositeElement): Scope {
    val sourceNode = element.ancestor<ArendSourceNode>()?.topmostEquivalentSourceNode ?: return (element.containingFile as? ArendFile)?.scope ?: EmptyScope.INSTANCE
    val parentScope = sourceNode.parentSourceNode?.let {
        if (it is ArendDefClass && sourceNode is ArendLongName)
            // The last parameters is set to true to prevent infinite recursion during resolving of references in \\extends
            LexicalScope.insideOf(it, it.parentGroup?.groupScope ?: ScopeFactory.parentScopeForGroup(it, EmptyModuleScopeProvider.INSTANCE, true), true)
        else it.scope
    } ?: (sourceNode.containingFile as? ArendFile)?.scope ?: EmptyScope.INSTANCE

    val scope = ScopeFactory.forSourceNode(parentScope, sourceNode, LazyScope { sourceNode.libraryConfig?.let {
        val libraryManager = it.project.service<TypeCheckingService>().libraryManager
        ModuleScope(it, libraryManager.getExtensionModuleScopeProvider(true).registeredModules + libraryManager.getExtensionModuleScopeProvider(false).registeredModules)
    } ?: EmptyScope.INSTANCE })

    if (scope is ClassFieldImplScope && scope.withSuperClasses()) {
        val classRef = scope.classReference
        if (classRef is ArendDefClass) {
            return ModifiedClassFieldImplScope(classRef, sourceNode.parentSourceNode?.parentSourceNode as? ClassReferenceHolder)
        }
    }
    return if (element is ArendDefIdentifier && sourceNode is Abstract.Pattern) ConstructorFilteredScope(scope.globalSubscope) else scope
}

fun getTopmostEquivalentSourceNode(sourceNode: ArendSourceNode): ArendSourceNode {
    var current = sourceNode
    while (true) {
        val parent = current.parent
        if (current is Abstract.Expression != parent is Abstract.Expression) {
            return current
        }
        current = when {
            parent is ArendLiteral -> parent
            parent is ArendAtom -> parent
            parent is ArendTuple && parent.tupleExprList.size == 1 && parent.tupleExprList[0].exprList.size == 1 -> parent
            parent is ArendNewExpr && parent.appPrefix == null && parent.lbrace == null && parent.argumentList.isEmpty() -> parent
            parent is ArendAtomFieldsAcc && parent.fieldAccList.isEmpty() -> parent
            parent is ArendArgumentAppExpr && parent.argumentList.isEmpty() -> parent
            parent is ArendLongName && parent.refIdentifierList.size == 1 -> parent
            parent is ArendLevelExpr && parent.sucKw == null && parent.maxKw == null -> parent
            parent is ArendAtomLevelExpr && parent.lparen != null -> parent
            parent is ArendOnlyLevelExpr && parent.sucKw == null && parent.maxKw == null -> parent
            parent is ArendAtomOnlyLevelExpr && parent.lparen != null -> parent
            //parent is ArendIPName -> parent
            else -> return current
        }
    }
}

fun getParentSourceNode(sourceNode: ArendSourceNode) =
    sourceNode.topmostEquivalentSourceNode.parent.ancestor<ArendSourceNode>()

abstract class ArendCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), ArendCompositeElement  {
    override val scope
        get() = getArendScope(this)

    override fun getReference(): ArendReference? = null

    override fun moduleTextRepresentation(): String? = runReadAction { moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { positionTextRepresentationImpl() }
}

abstract class ArendSourceNodeImpl(node: ASTNode) : ArendCompositeElementImpl(node), ArendSourceNode {
    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}

abstract class ArendStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT>, ArendSourceNode {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val scope
        get() = getArendScope(this)

    override fun getReference(): ArendReference? = null

    override fun toString(): String = "${javaClass.simpleName}($elementType)"

    override fun moduleTextRepresentation(): String? = runReadAction { moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { positionTextRepresentationImpl() }

    override fun getTopmostEquivalentSourceNode() = getTopmostEquivalentSourceNode(this)

    override fun getParentSourceNode() = getParentSourceNode(this)
}
