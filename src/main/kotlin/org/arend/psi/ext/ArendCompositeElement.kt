package org.arend.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.IArendFile
import org.arend.ext.error.SourceInfo
import org.arend.module.ModuleLocation
import org.arend.module.ModuleScope
import org.arend.module.scopeprovider.EmptyModuleScopeProvider
import org.arend.naming.reference.Referable
import org.arend.naming.scope.*
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.doc.ArendDocReference
import org.arend.resolving.ArendReference
import org.arend.resolving.util.ModifiedClassFieldImplScope
import org.arend.term.abs.Abstract
import java.util.function.Predicate

interface ArendCompositeElement : PsiElement, SourceInfo {
    val scope: Scope
    override fun getReference(): ArendReference?
}

fun PsiElement.moduleTextRepresentationImpl(): String? = if (isValid) containingFile?.name else null

fun PsiElement.positionTextRepresentationImpl(): String? {
    if (!isValid) {
        return null
    }
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

fun getArendScope(element: ArendCompositeElement): Scope {
    val sourceNode = element.ancestor<ArendSourceNode>()?.topmostEquivalentSourceNode ?: return (element.containingFile as? ArendFile)?.scope ?: EmptyScope.INSTANCE
    ((sourceNode as? ArendLongName)?.parent as? ArendDocReference)?.let { return it.scope }

    var isExtends = false
    val parentScope = sourceNode.parentSourceNode?.let {
        val classDef = if (sourceNode is ArendLongName) (it as? ArendSuperClass)?.parent as? ArendDefClass else null
        if (classDef != null) {
            isExtends = true
            // The last parameters is set to ONLY_EXTERNAL to prevent infinite recursion during resolving of references in \\extends
            LexicalScope.insideOf(classDef, classDef.parentGroup?.getGroupScope(LexicalScope.Extent.ONLY_EXTERNAL) ?: ScopeFactory.parentScopeForGroup(classDef, EmptyModuleScopeProvider.INSTANCE, true), LexicalScope.Extent.ONLY_EXTERNAL)
        } else it.scope
    } ?: (sourceNode.containingFile as? IArendFile)?.scope ?: EmptyScope.INSTANCE

    val scope = ScopeFactory.forSourceNode(parentScope, sourceNode, LazyScope {
        val containingFile = sourceNode.containingFile?.originalFile as? ArendFile
        containingFile?.libraryConfig?.let { ModuleScope(it, it.getFileLocationKind(containingFile) == ModuleLocation.LocationKind.TEST) } ?: EmptyScope.INSTANCE
    }) { classRef -> if (classRef is ArendDefClass) ModifiedClassFieldImplScope(classRef, sourceNode.parentSourceNode?.parentSourceNode as? ClassReferenceHolder) else null }
    return when {
        element is ArendDefIdentifier && sourceNode is Abstract.Pattern -> ConstructorFilteredScope(scope.globalSubscope)
        isExtends -> object : Scope { // exclude Array
            override fun find(pred: Predicate<Referable?>): Referable? =
                scope.find { ref -> !(Prelude.DEP_ARRAY != null && (ref == Prelude.DEP_ARRAY.ref || ref is ArendDefClass && ref.tcReferable == Prelude.DEP_ARRAY.ref)) && pred.test(ref) }
            override fun resolveName(name: String, kind: Referable.RefKind?): Referable? {
                val ref = scope.resolveName(name, kind)
                return if (Prelude.DEP_ARRAY != null && (ref == Prelude.DEP_ARRAY.ref || ref is ArendDefClass && ref.tcReferable == Prelude.DEP_ARRAY.ref)) null else ref
            }
            override fun resolveNamespace(name: String) = scope.resolveNamespace(name)
            override fun getGlobalSubscope() = scope.globalSubscope
            override fun getGlobalSubscopeWithoutOpens(withImports: Boolean) = scope.getGlobalSubscopeWithoutOpens(withImports)
            override fun getImportedSubscope() = scope.importedSubscope
        }
        else -> scope
    }
}

fun getTopmostEquivalentSourceNode(sourceNode: ArendSourceNode): ArendSourceNode {
    var current = sourceNode
    while (true) {
        val parent = current.parent
        if (parent == null || current is Abstract.Expression != parent is Abstract.Expression) {
            return current
        }
        current = when {
            parent is ArendLiteral -> parent
            parent is ArendAtom -> parent
            parent is ArendTuple && parent.tupleExprList.let { it.size == 1 && it[0].type == null } -> parent
            parent is ArendNewExpr && parent.appPrefix == null && parent.lbrace == null && parent.argumentList.isEmpty() -> parent
            parent is ArendAtomFieldsAcc && parent.numberList.isEmpty() -> parent
            parent is ArendArgumentAppExpr && parent.argumentList.isEmpty() -> parent
            parent is ArendLongName && parent.refIdentifierList.size == 1 -> parent
            parent is ArendAtomLevelExpr && parent.lparen != null -> parent
            parent is ArendOnlyLevelExpr && current is ArendOnlyLevelExpr -> parent
            //parent is ArendIPName -> parent
            else -> return current
        }
    }
}

fun getParentSourceNode(sourceNode: ArendSourceNode) =
    sourceNode.topmostEquivalentSourceNode.parent?.ancestor<ArendSourceNode>()

open class ArendCompositeElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), ArendCompositeElement {
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
