package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalBody
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.stubs.ArendDefFunctionStub
import org.arend.term.FunctionKind
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ParameterImpl
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<ArendDefFunctionStub>, ArendDefFunction, ArendFunctionalDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override val body: ArendFunctionalBody? get() = functionBody

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)

    override fun getTerm(): ArendExpr? = functionBody?.expr

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = functionBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = functionBody?.functionClauses?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if ((def as? ArendDefFunction)?.functionKw?.useKw != null) def else null
    } ?: emptyList()

    override fun withTerm() = functionBody?.fatArrow != null

    override fun isCowith() = functionBody?.cowithKw != null

    override fun getFunctionKind() = functionKw.let {
        when {
            it.lemmaKw != null -> FunctionKind.LEMMA
            it.sfuncKw != null -> FunctionKind.SFUNC
            it.levelKw != null -> FunctionKind.LEVEL
            it.coerceKw != null -> FunctionKind.COERCE
            it.consKw != null -> FunctionKind.CONS
            else -> FunctionKind.FUNC
        }
    }

    override fun getKind() = if (functionKw.consKw != null) GlobalReferable.Kind.CONSTRUCTOR else GlobalReferable.Kind.TYPECHECKABLE

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.FUNCTION_DEFINITION

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    private val allParameters
        get() = if (enclosingClass == null) parameters else listOf(ParameterImpl(false, listOf(null), null)) + parameters

    override fun getTypeOf() = org.arend.typing.getTypeOf(allParameters, resultType)

    override fun getClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (isCowith) ReferableExtractVisitor().findReferableInType(type) as? ClassReferable else ReferableExtractVisitor().findClassReferable(type)
    }

    override fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor(true)
        val classRef = (if (isCowith) visitor.findReferableInType(type) as? ClassReferable else visitor.findClassReferable(type)) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields, true)
    }

    override fun getClassFieldImpls(): List<ArendCoClause> = functionBody?.coClauseList ?: emptyList()

    override val psiElementType: PsiElement?
        get() = resultType
}
