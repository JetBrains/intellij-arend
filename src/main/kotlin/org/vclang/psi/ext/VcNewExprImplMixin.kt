package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAppExpr
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcCoClause
import org.vclang.psi.VcNewExpr

abstract class VcNewExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcNewExpr, Abstract.ClassReferenceHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val isNew = newKw != null
        if (!isNew && lbrace == null && argumentList.isEmpty()) {
            val expr = appExpr ?: return visitor.visitInferHole(this, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
            return expr.accept(visitor, params)
        }
        return visitor.visitClassExt(this, isNew, if (isNew) argumentAppExpr else appExpr, if (lbrace == null) null else coClauseList, argumentList, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
    }

    override fun getClassReference(): ClassReferable? = getClassReference(appExpr) ?: getClassReference(argumentAppExpr)

    override fun getClassFieldImpls(): List<VcCoClause> = coClauseList

    override fun getNumberOfArguments() = argumentAppExpr?.argumentList?.size ?: 0

    companion object {
        fun getClassReference(appExpr: VcAppExpr?): ClassReferable? {
            val argAppExpr = appExpr as? VcArgumentAppExpr ?: return null
            var ref = argAppExpr.longNameExpr?.longName?.referent
            if (ref == null) {
                val atomFieldsAcc = argAppExpr.atomFieldsAcc ?: return null
                if (!atomFieldsAcc.fieldAccList.isEmpty()) {
                    return null
                }
                ref = atomFieldsAcc.atom.literal?.longName?.referent
            }
            return ExpressionResolveNameVisitor.resolve(ref, appExpr.scope.globalSubscope) as? ClassReferable
        }
    }
}
