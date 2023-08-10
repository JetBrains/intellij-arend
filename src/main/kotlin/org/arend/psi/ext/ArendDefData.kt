package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefDataStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.resolving.util.ParameterImpl
import org.arend.resolving.util.Universe
import org.arend.resolving.util.getTypeOf
import javax.swing.Icon

class ArendDefData : ArendDefinition<ArendDefDataStub>, TCDefinition, Abstract.DataDefinition, StubBasedPsiElement<ArendDefDataStub> {
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

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statList?.mapNotNull {
        val def = it.firstRelevantChild
        if ((def as? ArendDefFunction)?.functionKind?.isUse == true) def else null
    } ?: emptyList()

    internal val allParameters
        get() = if (enclosingClass == null) parameters else listOf(ParameterImpl(false, listOf(null), null)) + parameters

    override val typeOf: Abstract.Expression?
        get() = getTypeOf(allParameters, Universe)

    override fun getKind() = GlobalReferable.Kind.DATA

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.DATA_DEFINITION

    override val psiElementType: PsiElement?
        get() = universe

    override val tcReferable: TCDefReferable?
        get() = super.tcReferable as TCDefReferable?
}
