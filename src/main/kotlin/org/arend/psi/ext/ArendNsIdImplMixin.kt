package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.term.Precedence
import org.arend.psi.ArendNsId
import org.arend.psi.ext.impl.ReferableAdapter


abstract class ArendNsIdImplMixin(node: ASTNode) : ArendCompositeElementImpl(node), ArendNsId {
    override fun getOldReference(): Referable = refIdentifier.referent

    override fun getName() = defIdentifier?.referenceName

    override fun getPrecedence(): Precedence? = prec.let { ReferableAdapter.calcPrecedence(it) }
}