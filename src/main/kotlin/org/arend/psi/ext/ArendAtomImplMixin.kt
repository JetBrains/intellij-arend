package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.ArendAtom
import org.arend.term.abs.AbstractExpressionVisitor
import java.math.BigInteger


abstract class ArendAtomImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendAtom {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        literal?.let { return it.accept(visitor, params) }
        tuple?.let { return it.accept(visitor, params) }
        (number ?: negativeNumber)?.let { return visitor.visitNumericLiteral(this, BigInteger(it.text), params) }
        thisKw?.let { return visitor.visitThis(this, params) }
        error("Incorrect expression: atom")
    }
}