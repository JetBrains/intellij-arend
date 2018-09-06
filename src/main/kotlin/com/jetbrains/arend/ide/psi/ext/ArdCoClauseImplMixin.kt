package com.jetbrains.arend.ide.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.arend.ide.psi.ArdCoClause
import com.jetbrains.arend.ide.psi.ArdExpr
import com.jetbrains.arend.ide.psi.ArdNameTele
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable


abstract class ArdCoClauseImplMixin(node: ASTNode) : ArdSourceNodeImpl(node), ArdCoClause {
    override fun getData() = this

    override fun getImplementedField() = longName?.referent

    fun getResolvedImplementedField() = longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    override fun getParameters(): List<ArdNameTele> = nameTeleList

    override fun getImplementation(): ArdExpr? = expr

    override fun getClassFieldImpls(): List<ArdCoClause> = coClauseList

    override fun getClassReference(): ClassReferable? {
        val resolved = getResolvedImplementedField()
        return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
    }

    override fun getArgumentsExplicitness() = emptyList<Boolean>()
}