package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendConstructorStub
import org.arend.term.Precedence
import org.arend.term.abs.Abstract
import org.arend.typing.ExpectedTypeVisitor
import javax.swing.Icon

abstract class ConstructorAdapter : ReferableAdapter<ArendConstructorStub>, ArendConstructor {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.CONSTRUCTOR

    override fun getData() = this

    override fun getPatterns(): List<Abstract.Pattern> = emptyList()

    override fun getConstructors(): List<ArendConstructor> = listOf(this)

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getReferable(): LocatedReferable = this

    override fun getParameters(): List<ArendTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = clauseList

    override fun isVisible(): Boolean = true

    override fun getResultType() = expr

    override fun getParameterType(params: List<Boolean>): Any? {
        val parameters = (ancestors.filterIsInstance<ArendDefData>().firstOrNull()?.typeTeleList?.map { ExpectedTypeVisitor.ParameterImpl(false, it.referableList, it.type) } ?: emptyList()) + parameters
        return ExpectedTypeVisitor.getParameterType(parameters, ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), parameters.sumBy { it.referableList.size }), params, textRepresentation())
    }

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, ancestors.filterIsInstance<ArendDefData>().firstOrNull()?.let { ExpectedTypeVisitor.ReferenceImpl(it) })

    override fun getIcon(flags: Int): Icon = ArendIcons.CONSTRUCTOR

    override val psiElementType: ArendDefIdentifier?
        get() = ancestors.filterIsInstance<ArendDefData>().firstOrNull()?.defIdentifier
}
