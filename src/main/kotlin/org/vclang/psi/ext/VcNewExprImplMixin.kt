package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAppExpr
import org.vclang.psi.VcArgumentAppExpr
import org.vclang.psi.VcNewExpr

abstract class VcNewExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcNewExpr, Abstract.ClassReferenceHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        if (newKw == null && lbrace == null && argumentList.isEmpty()) {
            return appExpr.accept(visitor, params)
        }
        return visitor.visitClassExt(this, newKw != null, appExpr, if (lbrace == null) null else coClauseList, argumentList, params)
    }

    override fun getClassReference(): ClassReferable? = getClassReference(appExpr)

    companion object {
        fun getClassReference(appExpr: VcAppExpr?): ClassReferable? {
            val argAppExpr = appExpr as? VcArgumentAppExpr ?: return null
            var ref = argAppExpr.longName?.referent
            if (ref == null) {
                val atomFieldsAcc = argAppExpr.atomFieldsAcc ?: return null
                if (!atomFieldsAcc.fieldAccList.isEmpty()) {
                    return null
                }
                ref = atomFieldsAcc.atom.literal?.longName?.referent
            }

            while (ref is RedirectingReferable) {
                ref = ref.originalReferable
            }
            if (ref is UnresolvedReference) {
                ref = ref.resolve(appExpr.scope.globalSubscope)
            }
            while (ref is RedirectingReferable) {
                ref = ref.originalReferable
            }
            return ref as? ClassReferable
        }
    }
}
