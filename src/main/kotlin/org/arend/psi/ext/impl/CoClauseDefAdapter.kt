package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalBody
import org.arend.psi.stubs.ArendCoClauseDefStub
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.resolving.util.ReferableExtractVisitor
import javax.swing.Icon

abstract class CoClauseDefAdapter : DefinitionAdapter<ArendCoClauseDefStub>, ArendCoClauseDef {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendCoClauseDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val parentCoClause: ArendCoClause?
        get() = parent as? ArendCoClause

    override fun getNameIdentifier() = parentCoClause?.defIdentifier ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getName() = stub?.name ?: parentCoClause?.defIdentifier?.id?.text ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()?.referenceName

    private val isDefault: Boolean
        get() = parentCoClause?.parent is ArendClassStat

    override fun getPrec(): ArendPrec? {
        val coClause = parentCoClause ?: return null
        coClause.prec?.let { return it }
        val classRef = (coClause.parent?.parent as? ClassReferenceHolder)?.classReference ?: return null
        return (Scope.resolveName(ClassFieldImplScope(classRef, false), coClause.longName.refIdentifierList.map { it.refName }) as? ReferableAdapter<*>)?.getPrec()
    }

    override fun getAlias(): ArendAlias? = null

    override val defIdentifier: ArendDefIdentifier?
        get() = parentCoClause?.defIdentifier

    override val where: ArendWhere?
        get() = null

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override val body: ArendFunctionalBody?
        get() = coClauseBody

    override fun getClassReference(): ClassReferable? = resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getCoClauseElements(): List<ArendCoClause> = coClauseBody?.coClauseList ?: emptyList()

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.exprList.firstOrNull() ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.let { it.exprList.getOrNull(1) ?: it.atomFieldsAccList.getOrNull(1) }

    override fun getTerm(): Abstract.Expression? = coClauseBody?.expr

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = coClauseBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = coClauseBody?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = emptyList()

    override fun withTerm() = coClauseBody?.fatArrow != null

    override fun isCowith() = coClauseBody?.cowithKw != null

    override fun getFunctionKind() = if (isDefault) FunctionKind.CLASS_COCLAUSE else FunctionKind.FUNC_COCLAUSE

    override fun getImplementedField(): Abstract.Reference? = parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getKind() = GlobalReferable.Kind.COCLAUSE_FUNCTION

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.COCLAUSE_DEFINITION

    override val tcReferable: TCDefReferable?
        get() = super.tcReferable as TCDefReferable?

    override fun getPLevelParams(): ArendPLevelParams? = null

    override fun getHLevelParams(): ArendHLevelParams? = null
}