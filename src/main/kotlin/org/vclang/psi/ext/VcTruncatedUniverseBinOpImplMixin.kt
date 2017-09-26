package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcTruncatedUniverseBinOp


abstract class VcTruncatedUniverseBinOpImplMixin(node: ASTNode) : VcExprImplMixin(node), VcTruncatedUniverseBinOp {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P): R {
        val uniText = truncatedUniverse.text
        val index = uniText.indexOf('-')
        val hLevelNum = when {
            uniText.startsWith("\\oo-")      -> Abstract.INFINITY_LEVEL
            index >= 0 && uniText[0] == '\\' -> uniText.substring(1, index).toIntOrNull()
            else                             -> null
        }
        val pLevelNum = if (hLevelNum != null) uniText.substring(index + "-Type".length).toIntOrNull() else null
        return visitor.visitUniverse(this, pLevelNum, hLevelNum, atomLevelExpr, null, params)
    }
}