package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.UnresolvedReference
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcArgumentBinOp
import org.vclang.psi.VcNewExpr

abstract class VcNewExprImplMixin(node: ASTNode) : VcExprImplMixin(node), VcNewExpr, Abstract.ClassReferenceHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitClassExt(this, newKw != null, binOpArg, if (lbrace == null) null else implementStatementList, params)

    override fun getClassReference(): ClassReferable? =
        ((binOpArg as? VcArgumentBinOp)?.atomFieldsAcc?.atom?.literal?.longName?.referent as UnresolvedReference?)?.resolve(scope.globalSubscope) as? ClassReferable
}
