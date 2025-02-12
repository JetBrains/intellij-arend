package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefDataStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import javax.swing.Icon

class ArendDefData : ArendDefinition<ArendDefDataStub>, Abstract.DataDefinition, StubBasedPsiElement<ArendDefDataStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val dataBody: ArendDataBody?
        get() = childOfType()

    val truncatedKw: PsiElement?
        get() = findChildByType(ArendElementTypes.TRUNCATED_KW)

    override fun getConstructors(): List<ArendConstructor> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList.flatMap { it.constructors } + body.constructorList
    }

    override fun getInternalReferables(): List<ArendInternalReferable> = constructors

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()

    override val parametersExt: List<Abstract.Parameter>
        get() = parameters

    override fun getEliminatedExpressions(): List<ArendRefIdentifier>? = dataBody?.elim?.refIdentifierList

    override fun isTruncated(): Boolean = hasChildOfType(ArendElementTypes.TRUNCATED_KW)

    override fun getUniverse(): ArendExpr? = childOfType()

    override fun getClauses(): List<Abstract.ConstructorClause> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList + body.constructorList
    }

    override fun getKind() = GlobalReferable.Kind.DATA

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.DATA_DEFINITION

    override val psiElementType: PsiElement?
        get() = universe
}
