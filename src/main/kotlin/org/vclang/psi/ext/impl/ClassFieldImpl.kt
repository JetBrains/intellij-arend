package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.vclang.VcIcons
import org.vclang.psi.VcClassImplement
import org.vclang.psi.VcCoClause
import org.vclang.psi.VcNameTele
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcClassImplementStub

abstract class ClassFieldImplAdapter : PsiStubbedReferableImpl<VcClassImplementStub>, VcClassImplement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getName() = longName.refIdentifierList.lastOrNull()?.referenceName

    override fun getImplementedField() = longName.referent

    fun getResolvedImplementedField(): Referable? {
        val longName = longName
        return ExpressionResolveNameVisitor.resolve(longName.referent, longName.scope)
        // return longName.reference?.resolve()
    }

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getImplementation() = expr

    override fun getClassFieldImpls(): List<VcCoClause> = coClauseList

    override fun getArgumentsExplicitness() = emptyList<Boolean>()

    override fun getClassReference(): ClassReferable? {
        val resolved = getResolvedImplementedField()
        return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
    }

    override fun getIcon(flags: Int) = VcIcons.IMPLEMENTATION
}