package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefFunctionStub
import org.vclang.typing.ExpectedTypeVisitor
import org.vclang.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<VcDefFunctionStub>, VcDefFunction {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getResultType(): VcExpr? = expr

    override fun getTerm(): VcExpr? = functionBody?.expr

    override fun getEliminatedExpressions(): List<VcRefIdentifier> = functionBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<VcClause> = functionBody?.functionClauses?.clauseList ?: emptyList()

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun isCoerce() = coerceKw != null

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = VcIcons.FUNCTION_DEFINITION

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

    override fun getClassFieldImpls(): List<VcCoClause> = functionBody?.coClauses?.coClauseList ?: emptyList()

    override fun getArgumentsExplicitness() = (resultType as? VcNewExpr)?.argumentAppExpr?.argumentList?.map { it.isExplicit } ?: emptyList()

    override val psiElementType: PsiElement?
        get() = resultType
}
