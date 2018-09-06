package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.psi.ArdClassImplement
import com.jetbrains.arend.ide.psi.ArdCoClause
import com.jetbrains.arend.ide.psi.ArdNameTele
import com.jetbrains.arend.ide.psi.ext.PsiStubbedReferableImpl
import com.jetbrains.arend.ide.psi.stubs.ArdClassImplementStub
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor

abstract class ClassFieldImplAdapter : PsiStubbedReferableImpl<ArdClassImplementStub>, ArdClassImplement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getName() = longName.refIdentifierList.lastOrNull()?.referenceName

    override fun getImplementedField() = longName.referent

    fun getResolvedImplementedField(): Referable? {
        val longName = longName
        return ExpressionResolveNameVisitor.resolve(longName.referent, longName.scope)
        // return longName.reference?.resolve()
    }

    override fun getParameters(): List<ArdNameTele> = nameTeleList

    override fun getImplementation() = expr

    override fun getClassFieldImpls(): List<ArdCoClause> = coClauseList

    override fun getArgumentsExplicitness() = emptyList<Boolean>()

    override fun getClassReference(): ClassReferable? {
        val resolved = getResolvedImplementedField()
        return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
    }

    override fun getIcon(flags: Int) = ArdIcons.IMPLEMENTATION
}