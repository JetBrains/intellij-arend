package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefFunctionStub
import org.arend.term.Precedence
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ExpectedTypeVisitor
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<ArendDefFunctionStub>, ArendDefFunction {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = expr

    override fun getTerm(): ArendExpr? = functionBody?.expr

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = functionBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = functionBody?.functionClauses?.clauseList ?: emptyList()

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun isCoerce() = coerceKw != null

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.FUNCTION_DEFINITION

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(parameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, resultType)

    override fun getClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return ReferableExtractVisitor().findClassReferable(type)
    }

    override fun getClassReferenceData(): ClassReferenceData? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor(true)
        val classRef = visitor.findClassReferable(type) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields)
    }

    override fun getClassFieldImpls(): List<ArendCoClause> = functionBody?.coClauses?.coClauseList ?: emptyList()

    override val psiElementType: PsiElement?
        get() = resultType
}
