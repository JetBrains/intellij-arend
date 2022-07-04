package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.psi.ArendAlias
import org.arend.psi.ArendDefHLevels
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendPrec
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.stubs.ArendHLevelsStub

abstract class ArendDefHLevelsImplMixin : ReferableAdapter<ArendHLevelsStub>, ArendDefHLevels {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendHLevelsStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override fun getPrec(): ArendPrec? = null

    override fun getAlias(): ArendAlias? = null

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?): TCReferable {
        TODO("Not yet implemented")
    }

    override fun getKind() = GlobalReferable.Kind.LEVEL
}