package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.LazyScope
import org.arend.psi.*
import org.arend.psi.stubs.ArendNamedStub
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor

abstract class ArendFunctionDefinition<StubT> : ArendDefinition<StubT>, TCDefinition, Abstract.FunctionDefinition
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val tcReferable: TCDefReferable?
        get() = super.tcReferable as TCDefReferable?

    val body: ArendFunctionBody?
        get() = childOfType()

    val returnExpr: ArendReturnExpr?
        get() = childOfType()

    override fun getParameters(): List<ArendNameTele> = getChildrenOfType()

    override fun getResultType() = returnExpr?.type

    override fun getResultTypeLevel() = returnExpr?.typeLevel

    override fun withTerm() = body?.fatArrow != null

    override fun isCowith() = body?.cowithKw != null

    override fun getClassReference(): ClassReferable? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor()
        return if (isCowith) visitor.findClassReference(visitor.findReferableInType(type), LazyScope { type.scope }) else visitor.findClassReferable(type)
    }

    override fun getCoClauseElements() = body?.coClauseList ?: emptyList()

    override fun getTerm() = body?.expr

    override fun getEliminatedExpressions() = body?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = body?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statList?.mapNotNull {
        val def = it.group
        if ((def as? ArendDefFunction)?.functionKind?.isUse == true) def else null
    } ?: emptyList()

    override fun getImplementedField(): Abstract.Reference? = null

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    // The element before which parameters can be inserted
    open fun findParametersElement(): PsiElement? =
        getChild<PsiElement> { it.elementType == ArendElementTypes.COLON || it is ArendFunctionBody || it is ArendWhere }?.extendLeft
}