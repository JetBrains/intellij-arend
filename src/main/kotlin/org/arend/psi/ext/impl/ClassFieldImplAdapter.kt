package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.stubs.ArendClassImplementStub
import org.arend.resolving.DataLocatedReferable

abstract class ClassFieldImplAdapter : ReferableAdapter<ArendClassImplementStub>, ArendClassImplement, CoClauseBase {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendClassImplementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getData() = this

    override fun getPrec(): ArendPrec? = null

    override fun getAlias(): ArendAlias? = null

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override fun getImplementedField() = longName

    override fun getCoClauseElements(): List<ArendLocalCoClause> = localCoClauseList

    override val resolvedImplementedField
        get() = longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? Referable

    override fun getNameIdentifier() = longName.refIdentifierList.lastOrNull()

    override fun getName() = stub?.name ?: longName.refIdentifierList.lastOrNull()?.referenceName

    override fun getParameters(): List<ArendLamTele> = lamParamList.filterIsInstance<ArendLamTele>()

    override fun getLamParameters(): List<ArendLamParam> = lamParamList

    override fun getImplementation() = expr

    override fun hasImplementation() = fatArrow != null

    override fun getCoClauseData() = lbrace

    override fun getClassReference() = CoClauseBase.getClassReference(this)

    override fun getClassReferenceData(onlyClassRef: Boolean) = CoClauseBase.getClassReferenceData(this)

    override fun getIcon(flags: Int) = ArendIcons.IMPLEMENTATION

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?) =
        DataLocatedReferable(data, this, parent)

    override fun isDefault() = false
}