package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdAtom
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import java.math.BigInteger


abstract class ArdAtomImplMixin(node: ASTNode) : ArdExprImplMixin(node), ArdAtom {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        literal?.let { return it.accept(visitor, params) }
        tuple?.let { return it.accept(visitor, params) }
        (number
                ?: negativeNumber)?.let { return visitor.visitNumericLiteral(this, BigInteger(it.text), if (visitor.visitErrors()) com.jetbrains.arend.ide.psi.ext.getErrorData(this) else null, params) }
        error("Incorrect expression: atom")
    }
}