package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.naming.reference.UnresolvedReference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ArendLongName
import org.arend.term.abs.Abstract


abstract class ArendLongNameImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendLongName {
    override val longName: List<String>
        get() = refIdentifierList.map { ref -> ref.referenceName }

    override val unresolvedReference: UnresolvedReference?
        get() = referent

    override val resolvedInScope: Referable?
        get() = referenceNameElement?.resolvedInScope

    override val resolve
        get() = referenceNameElement?.reference?.resolve()

    override fun getData() = this

    override fun getReferent(): UnresolvedReference =
            LongUnresolvedReference.make(this, refIdentifierList.map { it.referenceName })

    override fun getHeadReference(): Abstract.Reference = refIdentifierList[0]

    override fun getTailReferences(): List<Abstract.Reference> {
        val refs = refIdentifierList
        return refs.subList(1, refs.size)
    }

    override val referenceName: String
        get() = referenceNameElement?.referenceName ?: ""

    override val referenceNameElement
        get() = refIdentifierList.lastOrNull()
}