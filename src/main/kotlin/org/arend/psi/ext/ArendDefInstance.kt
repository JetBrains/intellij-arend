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
import javax.swing.Icon

class ArendDefInstance : ArendFunctionDefinition<ArendDefInstanceStub>, Abstract.FunctionDefinition, StubBasedPsiElement<ArendDefInstanceStub> {
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

    override val psiElementType: PsiElement?
        get() = resultType
}
