package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefInstanceStub
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ExpectedTypeVisitor
import org.arend.typing.ReferableExtractVisitor
import javax.swing.Icon

abstract class InstanceAdapter : DefinitionAdapter<ArendDefInstanceStub>, ArendDefInstance {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.expr ?: it.atomFieldsAccList.firstOrNull() }

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.atomFieldsAccList?.getOrNull(1)

    override fun getTerm(): ArendExpr? = instanceBody?.expr

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = instanceBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = instanceBody?.functionClauses?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if (def is ArendDefFunction && def.useKw != null) def else null
    } ?: emptyList()

    override fun withTerm() = instanceBody?.fatArrow != null

    override fun isCowith(): Boolean {
        val body = instanceBody
        return body == null || body.elim == null && body.fatArrow == null
    }

    override fun isCoerce() = false

    override fun isLevel() = false

    override fun isLemma() = false

    override fun isInstance() = true

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_INSTANCE

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    private val allParameters
        get() = if (enclosingClass == null) parameters else listOf(ExpectedTypeVisitor.ParameterImpl(false, listOf(null), null)) + parameters

    override fun getParameterType(params: List<Boolean>) = ExpectedTypeVisitor.getParameterType(allParameters, resultType, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(allParameters, resultType)

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

    override fun getClassFieldImpls(): List<ArendCoClause> = instanceBody?.coClauseList ?: emptyList()

    override val psiElementType: PsiElement?
        get() = resultType
}
