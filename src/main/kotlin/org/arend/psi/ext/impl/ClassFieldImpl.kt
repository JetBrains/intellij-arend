package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TypedReferable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.ArendClassImplement
import org.arend.psi.ArendCoClause
import org.arend.psi.ArendNameTele
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.stubs.ArendClassImplementStub

abstract class ClassFieldImplAdapter : PsiStubbedReferableImpl<ArendClassImplementStub>, ArendClassImplement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getName() = longName.refIdentifierList.lastOrNull()?.referenceName

    override fun getImplementedField() = longName.referent

    fun getResolvedImplementedField(): Referable? {
        val longName = longName
        return ExpressionResolveNameVisitor.resolve(longName.referent, longName.scope)
        // return longName.reference?.resolve()
    }

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getImplementation() = expr

    override fun getClassFieldImpls(): List<ArendCoClause> = coClauseList

    override fun getArgumentsExplicitness() = emptyList<Boolean>()

    override fun getClassReference(): ClassReferable? {
        val resolved = getResolvedImplementedField()
        return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
    }

    override fun getIcon(flags: Int) = ArendIcons.IMPLEMENTATION
}