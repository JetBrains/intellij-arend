package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.UnresolvedReference
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.Abstract


class ArendLongName(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.LongReference, ArendReferenceContainer  {
    val refIdentifierList: List<ArendRefIdentifier>
        get() = getChildrenOfType()

    override val longName: List<String>
        get() = refIdentifierList.map { ref -> ref.referenceName }

    override val unresolvedReference: UnresolvedReference
        get() = referent

    override val resolve
        get() = referenceNameElement?.reference?.resolve()

    override fun getData() = this

    override fun getReferent(): UnresolvedReference {
        val refList = refIdentifierList
        return if (refList.size == 1) NamedUnresolvedReference(refList[0], refList[0].referenceName) else LongUnresolvedReference.make(this, refList, refList.map { it.referenceName })
    }

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