package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.arend.ide.ArdIcons
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.stubs.ArdConstructorStub
import com.jetbrains.arend.ide.typing.ExpectedTypeVisitor
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import javax.swing.Icon

abstract class ConstructorAdapter : ReferableAdapter<ArdConstructorStub>, ArdConstructor {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArdConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getKind() = GlobalReferable.Kind.CONSTRUCTOR

    override fun getData() = this

    override fun getPatterns(): List<Abstract.Pattern> = emptyList()

    override fun getConstructors(): List<ArdConstructor> = listOf(this)

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getReferable(): LocatedReferable = this

    override fun getParameters(): List<ArdTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<ArdRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArdClause> = clauseList

    override fun isVisible(): Boolean = true

    override fun getParameterType(params: List<Boolean>): Any? {
        val parameters = (ancestors.filterIsInstance<ArdDefData>().firstOrNull()?.typeTeleList?.map { ExpectedTypeVisitor.ParameterImpl(false, it.referableList, it.type) }
                ?: emptyList()) + parameters
        return ExpectedTypeVisitor.getParameterType(parameters, ExpectedTypeVisitor.TooManyArgumentsError(textRepresentation(), parameters.sumBy { it.referableList.size }), params, textRepresentation())
    }

    override fun getTypeOf() = ExpectedTypeVisitor.getTypeOf(parameters, ancestors.filterIsInstance<ArdDefData>().firstOrNull()?.let { ExpectedTypeVisitor.ReferenceImpl(it) })

    override fun getIcon(flags: Int): Icon = ArdIcons.CONSTRUCTOR

    override val psiElementType: ArdDefIdentifier?
        get() = ancestors.filterIsInstance<ArdDefData>().firstOrNull()?.defIdentifier
}
