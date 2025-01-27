package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.psi.stubs.ArendCoClauseDefStub
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.naming.reference.*
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.term.group.AccessModifier
import javax.swing.Icon

class ArendCoClauseDef : ArendFunctionDefinition<ArendCoClauseDefStub>, Abstract.FunctionDefinition, TCDefinition, StubBasedPsiElement<ArendCoClauseDefStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendCoClauseDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val parentCoClause: ArendCoClause?
        get() = parent as? ArendCoClause

    override fun getNameIdentifier() = parentCoClause?.defIdentifier ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getName() = stub?.name ?: parentCoClause?.defIdentifier?.id?.text ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()?.referenceName

    private val isDefault: Boolean
        get() = parentCoClause?.parent is ArendClassStat

    override fun getAccessModifier() = if (isDefault) AccessModifier.PROTECTED else AccessModifier.PUBLIC

    /* TODO[server2]
    override val prec: ArendPrec?
        get() {
            val coClause = parentCoClause ?: return null
            coClause.prec?.let { return it }
            val classRef = (coClause.parent?.parent as? ClassReferenceHolder)?.classReference ?: return null
            return (Scope.resolveName(ClassFieldImplScope(classRef, false), coClause.longName.refIdentifierList.map { it.refName }) as? ReferableBase<*>)?.prec
        }
    */

    override val defIdentifier: ArendDefIdentifier?
        get() = parentCoClause?.defIdentifier

    override val where: ArendWhere?
        get() = null

    override fun getClassReference(): ClassReferable? = resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getFunctionKind() = if (isDefault) FunctionKind.CLASS_COCLAUSE else FunctionKind.FUNC_COCLAUSE

    override fun getImplementedField(): Abstract.Reference? = parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getKind() = GlobalReferable.Kind.COCLAUSE_FUNCTION

    override fun getIcon(flags: Int): Icon = ArendIcons.COCLAUSE_DEFINITION

    override fun findParametersElement(): PsiElement? = firstChild
}