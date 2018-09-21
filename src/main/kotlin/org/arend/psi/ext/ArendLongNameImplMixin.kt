package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.UnresolvedReference
import org.arend.term.abs.Abstract
import org.arend.psi.ArendLongName


abstract class ArendLongNameImplMixin(node: ASTNode) : ArendSourceNodeImpl(node), ArendLongName {
    override fun getData() = this

    override fun getReferent(): UnresolvedReference =
        LongUnresolvedReference.make(this, refIdentifierList.map { it.referenceName })

    override fun getHeadReference(): Abstract.Reference = refIdentifierList[0]

    override fun getTailReferences(): List<Abstract.Reference> {
        val refs = refIdentifierList
        return refs.subList(1, refs.size)
    }
}