package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.elementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefFunctionStub
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import javax.swing.Icon

class ArendDefFunction : ArendFunctionDefinition<ArendDefFunctionStub>, Abstract.FunctionDefinition, StubBasedPsiElement<ArendDefFunctionStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val functionKw: ArendCompositeElement
        get() = firstRelevantChild as ArendCompositeElement

    override val parametersExt: List<Abstract.Parameter>
        get() = parameters

    override fun getStatements() = (body?.coClauseList ?: emptyList()) + super.getStatements()

    override fun getFunctionKind() =
        when (functionKw.firstRelevantChild.elementType) {
            LEMMA_KW -> FunctionKind.LEMMA
            SFUNC_KW -> FunctionKind.SFUNC
            TYPE_KW -> FunctionKind.TYPE
            AXIOM_KW -> FunctionKind.AXIOM
            LEVEL_KW -> FunctionKind.LEVEL
            COERCE_KW -> FunctionKind.COERCE
            else -> FunctionKind.FUNC
        }

    override fun getIcon(flags: Int): Icon = ArendIcons.FUNCTION_DEFINITION

    override fun getKind() = GlobalReferable.Kind.FUNCTION

    override val psiElementType: PsiElement?
        get() = resultType
}
