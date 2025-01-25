package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.elementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefInstanceStub
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ParameterImpl
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.resolving.util.getTypeOf
import javax.swing.Icon

class ArendDefInstance : ArendFunctionDefinition<ArendDefInstanceStub>, Abstract.FunctionDefinition, ClassReferenceHolder, StubBasedPsiElement<ArendDefInstanceStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefInstanceStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val parametersExt: List<Abstract.Parameter>
        get() = parameters

    override fun getStatements() = (body?.coClauseList ?: emptyList()) + super.getStatements()

    override fun isCowith(): Boolean {
        val body = body
        return body == null || body.elim == null && body.fatArrow == null
    }

    override fun getFunctionKind(): FunctionKind = when (firstRelevantChild.elementType) {
        ArendElementTypes.INSTANCE_KW -> FunctionKind.INSTANCE
        ArendElementTypes.CONS_KW -> FunctionKind.CONS
        else -> error("Unknown function kind: ${firstRelevantChild.elementType}")
    }

    override fun getKind() = if (functionKind == FunctionKind.INSTANCE) GlobalReferable.Kind.INSTANCE else GlobalReferable.Kind.DEFINED_CONSTRUCTOR

    override fun getIcon(flags: Int): Icon = ArendIcons.CLASS_INSTANCE

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

    override val psiElementType: PsiElement?
        get() = resultType
}
