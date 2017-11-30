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
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitClassExt(this, newKw != null, appExpr, if (lbrace == null) null else coClauseList, argumentList, params)

    override fun getClassReference(): ClassReferable? = getClassReference(appExpr)

    companion object {
        fun getClassReference(appExpr: VcAppExpr): ClassReferable? {
            var ref = (appExpr as? VcArgumentAppExpr)?.longName?.referent ?: return null
            if (ref is RedirectingReferable) {
                ref = ref.originalReferable
            }
            if (ref is UnresolvedReference) {
                ref = ref.resolve(appExpr.scope.globalSubscope)
            }
            return ref as? ClassReferable
        }
    }
}
