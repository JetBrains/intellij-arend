package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.elementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.TypeClassReferenceExtractVisitor
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefFunctionStub
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ParameterImpl
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.resolving.util.getTypeOf
import javax.swing.Icon

class ArendDefFunction : ArendFunctionDefinition<ArendDefFunctionStub>, Abstract.FunctionDefinition, TCDefinition, ClassReferenceHolder, StubBasedPsiElement<ArendDefFunctionStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val functionKw: ArendCompositeElement
        get() = firstRelevantChild as ArendCompositeElement

    override val parametersExt: List<Abstract.Parameter>
        get() = parameters

    override fun getStatements() = (body?.coClauseList ?: emptyList()) + super.getStatements()

    override fun getFunctionKind(): FunctionKind {
        val child = functionKw.firstRelevantChild
        return when (child.elementType) {
            LEMMA_KW -> FunctionKind.LEMMA
            SFUNC_KW -> FunctionKind.SFUNC
            TYPE_KW -> FunctionKind.TYPE
            AXIOM_KW -> FunctionKind.AXIOM
            USE_KW -> when (child?.findNextSibling().elementType) {
                LEVEL_KW -> FunctionKind.LEVEL
                COERCE_KW -> FunctionKind.COERCE
                else -> FunctionKind.FUNC
            }
            else -> FunctionKind.FUNC
        }
    }

    override fun getIcon(flags: Int): Icon = ArendIcons.FUNCTION_DEFINITION

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getBodyReference(visitor: TypeClassReferenceExtractVisitor): Referable? {
        val expr = body?.expr ?: return null
        return ReferableExtractVisitor(requiredAdditionalInfo = false, isExpr = true).findReferable(expr)
    }

    private val allParameters
        get() = if (enclosingClass == null) parameters else listOf(ParameterImpl(false, listOf(null), null)) + parameters

    override val typeOf: Abstract.Expression?
        get() = getTypeOf(allParameters, resultType)

    override fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor(true)
        val classRef = (if (isCowith) visitor.findReferableInType(type) as? ClassReferable else visitor.findClassReferable(type)) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields, true)
    }

    override fun getKind() = GlobalReferable.Kind.FUNCTION

    override val psiElementType: PsiElement?
        get() = resultType
}
