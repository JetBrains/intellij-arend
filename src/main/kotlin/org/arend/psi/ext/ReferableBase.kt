package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.elementType
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.group.AccessModifier

// TODO[server2]: Remove everything that mentions TCReferable, and maybe the class itself?
abstract class ReferableBase<StubT> : PsiStubbedReferableImpl<StubT>, PsiLocatedReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    open val isVisible: Boolean
        get() = true

    open val prec: ArendPrec?
        get() = childOfType()

    open val alias: ArendAlias?
        get() = childOfType()

    override fun getAliasName() = alias?.aliasIdentifier?.id?.text

    override fun getAliasPrecedence() = calcPrecedence(alias?.prec)

    override fun getPrecedence() = stub?.precedence ?: calcPrecedence(prec)

    val locatedReferableParent: PsiLocatedReferable?
        get() = parent?.ancestor<PsiLocatedReferable>()

    override fun getAccessModifier() =
        parent?.childOfType<ArendAccessMod>()?.accessModifier ?: ancestor<ArendStatAccessMod>()?.accessModifier ?: AccessModifier.PUBLIC

    override val defIdentifier: ArendDefIdentifier?
        get() = childOfType()

    fun getDescription(): Doc =
        documentation?.doc ?: DocFactory.nullDoc()

    private object ArendReferableKey : Key<TCDefReferable>("AREND_REFERABLE_KEY")

    var tcReferable: TCDefReferable?
        get() = getUserData(ArendReferableKey)
        set(referable) {
            putUserData(ArendReferableKey, referable)
        }

    companion object {
        fun calcPrecedence(prec: ArendPrec?): Precedence {
            if (prec == null) return Precedence.DEFAULT
            val type = prec.firstRelevantChild.elementType
            val assoc = when (type) {
                RIGHT_ASSOC_KW, INFIX_RIGHT_KW -> Precedence.Associativity.RIGHT_ASSOC
                LEFT_ASSOC_KW, INFIX_LEFT_KW -> Precedence.Associativity.LEFT_ASSOC
                NON_ASSOC_KW, INFIX_NON_KW -> Precedence.Associativity.NON_ASSOC
                else -> return Precedence.DEFAULT
            }
            val priority = prec.number
            return Precedence(assoc, if (priority == null) Precedence.MAX_PRIORITY else priority.text?.toByteOrNull()
                ?: (Precedence.MAX_PRIORITY + 1).toByte(), type == INFIX_RIGHT_KW || type == INFIX_LEFT_KW || type == INFIX_NON_KW)
        }
    }
}
