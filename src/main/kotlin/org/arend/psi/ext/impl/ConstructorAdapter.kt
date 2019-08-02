package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendConstructorStub
import org.arend.term.abs.Abstract
import org.arend.typing.ParameterImpl
import org.arend.typing.ReferenceImpl
import javax.swing.Icon

abstract class ConstructorAdapter : ReferableAdapter<ArendConstructorStub>, ArendConstructor {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.CONSTRUCTOR

    override fun getData() = this

    override fun getPatterns(): List<Abstract.Pattern> = emptyList()

    override fun getConstructors(): List<ArendConstructor> = listOf(this)

    override fun getReferable() = this

    override fun getParameters(): List<ArendTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = clauseList

    override fun isVisible(): Boolean = true

    override fun getResultType(): ArendExpr? = null // expr // TODO[hits]

    private val allParameters
        get() = (ancestors.filterIsInstance<DataDefinitionAdapter>().firstOrNull()?.allParameters?.map { ParameterImpl(false, it.referableList, it.type) } ?: emptyList()) + parameters

    override fun getTypeOf() = org.arend.typing.getTypeOf(allParameters, ancestors.filterIsInstance<ArendDefData>().firstOrNull()?.let { ReferenceImpl(it) })

    override fun getIcon(flags: Int): Icon = ArendIcons.CONSTRUCTOR

    override val psiElementType: ArendDefIdentifier?
        get() = ancestors.filterIsInstance<ArendDefData>().firstOrNull()?.defIdentifier
}
