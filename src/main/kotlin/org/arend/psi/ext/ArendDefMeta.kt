package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.MetaReferable
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.TypeClassReferenceExtractVisitor
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefMetaStub
import org.arend.resolving.IntellijMetaReferable
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import java.util.function.Supplier


class ArendDefMeta : ArendDefinition<ArendDefMetaStub>, Abstract.MetaDefinition, StubBasedPsiElement<ArendDefMetaStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefMetaStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val expr: ArendExpr?
        get() = childOfType()

    var metaRef: MetaReferable?
        get() = tcReferableCache as MetaReferable?
        set(value) {
            tcReferableCache = value
        }

    override fun getDescription() = documentation?.toString() ?: ""

    private fun prepareTCRef(data: SmartPsiElementPointer<PsiLocatedReferable>?, parent: LocatedReferable?) =
        IntellijMetaReferable(data, accessModifier, precedence, refName, aliasPrecedence, aliasName, description, parent)

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?) =
        prepareTCRef(data, parent).apply { underlyingReferable = Supplier { runReadAction { data.element } } }

    fun makeTCReferable(parent: LocatedReferable?) =
        prepareTCRef(null, parent).apply { underlyingReferable = Supplier { this } }

    override fun getBodyReference(visitor: TypeClassReferenceExtractVisitor): Referable? =
        ReferableExtractVisitor(requiredAdditionalInfo = false, isExpr = true).findReferable(expr)

    override fun getKind() = GlobalReferable.Kind.META

    override fun getIcon(flags: Int) = ArendIcons.META_DEFINITION

    override fun getTerm(): Abstract.Expression? = expr

    override fun getParameters(): List<ArendNameTeleUntyped> = getChildrenOfType()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R =
        visitor.visitMeta(this)
}