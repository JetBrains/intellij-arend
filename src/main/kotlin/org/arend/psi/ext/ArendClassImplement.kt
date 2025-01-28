package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendClassImplementStub
import org.arend.term.abs.Abstract

class ArendClassImplement : ReferableBase<ArendClassImplementStub>, PsiLocatedReferable, Abstract.ClassFieldImpl, CoClauseBase, StubBasedPsiElement<ArendClassImplementStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getPrec(): Any? = prec

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override fun getImplementedField() = notNullChild(longName)

    override fun getCoClauseElements(): List<ArendLocalCoClause> = localCoClauseList

    override fun getNameIdentifier() = implementedField.refIdentifierList.lastOrNull()

    override fun getName() = stub?.name ?: implementedField.refIdentifierList.lastOrNull()?.referenceName

    override fun getParameters(): List<ArendNameTele> = getChildrenOfType()

    override fun getLamParameters(): List<ArendLamParam> = lamParamList

    override fun getImplementation() = expr

    override fun hasImplementation() = fatArrow != null

    override fun getCoClauseData() = lbrace

    override fun getIcon(flags: Int) = ArendIcons.IMPLEMENTATION

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun isDefault() = false
}