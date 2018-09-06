package com.jetbrains.arend.ide.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.jetbrains.arend.ide.psi.ArdDefinition
import com.jetbrains.arend.ide.psi.ArdFile
import com.jetbrains.arend.ide.psi.ArdPrec
import com.jetbrains.arend.ide.psi.ancestors
import com.jetbrains.arend.ide.psi.ext.PsiLocatedReferable
import com.jetbrains.arend.ide.psi.ext.PsiStubbedReferableImpl
import com.jetbrains.arend.ide.psi.stubs.ArdNamedStub
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.term.Precedence

abstract class ReferableAdapter<StubT> : PsiStubbedReferableImpl<StubT>, PsiLocatedReferable
        where StubT : ArdNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getTypecheckable(): PsiLocatedReferable = ancestors.filterIsInstance<ArdDefinition>().firstOrNull()
            ?: this

    override fun getLocation() = (containingFile as? ArdFile)?.modulePath

    override fun getLocatedReferableParent() = parent.ancestors.filterIsInstance<LocatedReferable>().firstOrNull()

    override fun getUnderlyingReference(): LocatedReferable? = null

    override fun getUnresolvedUnderlyingReference(): Reference? = null

    companion object {
        fun calcPrecedence(prec: ArdPrec?): Precedence {
            if (prec == null) return Precedence.DEFAULT
            val assoc = when {
                prec.rightAssocKw != null || prec.infixRightKw != null -> Precedence.Associativity.RIGHT_ASSOC
                prec.leftAssocKw != null || prec.infixLeftKw != null -> Precedence.Associativity.LEFT_ASSOC
                prec.nonAssocKw != null || prec.infixNonKw != null -> Precedence.Associativity.NON_ASSOC
                else -> return Precedence.DEFAULT
            }
            return Precedence(assoc, prec.number.text.toByteOrNull()
                    ?: Byte.MAX_VALUE, prec.infixRightKw != null || prec.infixLeftKw != null || prec.infixNonKw != null)
        }
    }
}
