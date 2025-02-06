package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefMetaStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor


class ArendDefMeta : ArendDefinition<ArendDefMetaStub>, Abstract.MetaDefinition, StubBasedPsiElement<ArendDefMetaStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefMetaStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val expr: ArendExpr?
        get() = childOfType()

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun getIcon(flags: Int) = ArendIcons.META_DEFINITION

    override fun getTerm(): Abstract.Expression? = expr

    override fun getParameters(): List<ArendNameTeleUntyped> = getChildrenOfType()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R =
        visitor.visitMeta(this)
}