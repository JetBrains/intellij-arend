package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.VcPrec
import org.vclang.psi.ancestors
import org.vclang.psi.ext.PsiConcreteReferable
import org.vclang.psi.ext.PsiStubbedReferableImpl
import org.vclang.psi.stubs.VcNamedStub

abstract class ReferableAdapter<StubT> : PsiStubbedReferableImpl<StubT>, PsiConcreteReferable
where StubT : VcNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getTypecheckable(): GlobalReferable = ancestors.filterIsInstance<VcDefinition>().firstOrNull() ?: this

    override fun isTypecheckable() = false

    override fun getLocation() = (containingFile as? VcFile)?.modulePath

    override fun getLocatedReferableParent() = parent.ancestors.filterIsInstance<LocatedReferable>().firstOrNull()

    companion object {
        fun calcPrecedence(prec: VcPrec?): Precedence {
            if (prec == null) return Precedence.DEFAULT
            val assoc = when {
                prec.rightAssocKw != null || prec.infixRightKw != null -> Precedence.Associativity.RIGHT_ASSOC
                prec.leftAssocKw != null || prec.infixLeftKw != null -> Precedence.Associativity.LEFT_ASSOC
                prec.nonAssocKw != null || prec.infixNonKw != null -> Precedence.Associativity.NON_ASSOC
                else -> return Precedence.DEFAULT
            }
            return Precedence(assoc, prec.number.text.toByteOrNull() ?: Byte.MAX_VALUE, prec.infixRightKw != null || prec.infixLeftKw != null || prec.infixNonKw != null)
        }
    }
}
