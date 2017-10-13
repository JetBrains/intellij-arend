package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcConstructorStub
import javax.swing.Icon

abstract class ConstructorAdapter : ReferableAdapter<VcConstructorStub>, VcConstructor {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun computeConcrete(errorReporter: ErrorReporter): Concrete.Constructor? {
        val data = ancestors.filterIsInstance<VcDefData>().firstOrNull()?.computeConcrete(errorReporter) as? Concrete.DataDefinition ?: return null
        return data.constructorClauses
            .flatMap { it.constructors }
            .firstOrNull { it.data === this }
    }

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getReferable(): GlobalReferable = this

    override fun getParameters(): List<VcTele> = teleList

    override fun getEliminatedExpressions(): List<VcRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<VcClause> = clauseList

    override fun getIcon(flags: Int): Icon = VcIcons.CONSTRUCTOR
}
