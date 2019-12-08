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
import org.arend.psi.stubs.ArendDefInstanceStub
import org.arend.term.FunctionKind
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ParameterImpl
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class InstanceAdapter : DefinitionAdapter<ArendDefInstanceStub>, ArendDefInstance, ArendFunctionalDefinition {
    constructor(node: ASTNode) : super(node)

    override val body: ArendFunctionalBody? get() = instanceBody

    constructor(stub: ArendDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)

    override fun getTerm(): ArendExpr? = instanceBody?.expr

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = instanceBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = instanceBody?.functionClauses?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if ((def as? ArendDefFunction)?.functionKw?.useKw != null) def else null
    } ?: emptyList()

    override fun withTerm() = instanceBody?.fatArrow != null

    override fun isCowith(): Boolean {
        val body = instanceBody
        return body == null || body.elim == null && body.fatArrow == null
    }

    override fun getFunctionKind() = if (instanceOrCons.consKw != null) FunctionKind.CONS else FunctionKind.INSTANCE

    override fun getKind() = if (instanceOrCons.consKw != null) GlobalReferable.Kind.DEFINED_CONSTRUCTOR else GlobalReferable.Kind.TYPECHECKABLE

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_INSTANCE

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

    override fun getCoClauseElements(): List<ArendCoClause> = instanceBody?.coClauseList ?: emptyList()

    override fun getImplementedField(): Abstract.Reference? = null

    override val psiElementType: PsiElement?
        get() = resultType
}
