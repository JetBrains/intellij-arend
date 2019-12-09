package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalBody
import org.arend.psi.stubs.ArendCoClauseDefStub
import org.arend.term.FunctionKind
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class CoClauseDefAdapter : DefinitionAdapter<ArendCoClauseDefStub>, ArendCoClauseDef {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendCoClauseDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val parentCoClause: ArendCoClause?
        get() = parent as? ArendCoClause

    override fun getNameIdentifier() = parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun getName() = stub?.name ?: parentCoClause?.longName?.refIdentifierList?.lastOrNull()?.referenceName

    override fun getPrec(): ArendPrec? = parentCoClause?.prec

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override val where: ArendWhere?
        get() = null

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override val body: ArendFunctionalBody?
        get() = this

    override val coClauseList: List<ArendCoClause>
        get() = emptyList()

    override val lbrace: PsiElement?
        get() = functionClauses?.lbrace

    override val rbrace: PsiElement?
        get() = functionClauses?.rbrace

    override val cowithKw: PsiElement?
        get() = null

    override val fatArrow: PsiElement?
        get() = null

    override val expr: ArendExpr?
        get() = null

    override fun getClassReference(): ClassReferable? = resultType?.let { ReferableExtractVisitor().findClassReferable(it) }

    override fun getCoClauseElements(): List<Abstract.ClassFieldImpl> = emptyList()

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)

    override fun getTerm(): Abstract.Expression? = null

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = functionClauses?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = emptyList()

    override fun withTerm() = false

    override fun isCowith() = false

    override fun getFunctionKind() = FunctionKind.COCLAUSE_FUNC

    override fun getImplementedField(): Abstract.Reference? = parentCoClause?.longName?.refIdentifierList?.lastOrNull()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.COCLAUSE_DEFINITION
}