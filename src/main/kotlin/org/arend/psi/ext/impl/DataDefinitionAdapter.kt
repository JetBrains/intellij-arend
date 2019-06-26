package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefDataStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.typing.ExpectedTypeVisitor
import javax.swing.Icon

abstract class DataDefinitionAdapter : DefinitionAdapter<ArendDefDataStub>, ArendDefData, Abstract.DataDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getConstructors(): List<ArendConstructor> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList.flatMap { it.constructorList } + body.constructorList
    }

    override fun getInternalReferables(): List<ArendInternalReferable> = constructors

    override fun getParameters(): List<ArendTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<ArendRefIdentifier>? = dataBody?.elim?.refIdentifierList

    override fun isTruncated(): Boolean = truncatedKw != null

    override fun getUniverse(): ArendExpr? = universeExpr

    override fun getClauses(): List<Abstract.ConstructorClause> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList + body.constructorList
    }

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if (def is ArendDefFunction && def.useKw != null) def else null
    } ?: emptyList()

    internal val allParameters
        get() = if (enclosingClass == null) parameters else listOf(ExpectedTypeVisitor.ParameterImpl(false, listOf(null), null)) + parameters

    override fun getParameterType(params: List<Boolean>) =
        ExpectedTypeVisitor.getParameterType(allParameters, ExpectedTypeVisitor.Universe, params, textRepresentation())

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(allParameters, ExpectedTypeVisitor.Universe)

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.DATA_DEFINITION

    override val psiElementType: PsiElement?
        get() = universe
}
