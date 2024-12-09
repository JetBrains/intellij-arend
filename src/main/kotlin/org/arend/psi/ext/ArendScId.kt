package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfTypeStrict
import org.arend.psi.firstRelevantChild
import org.arend.term.NameHiding

class ArendScId(node: ASTNode) : ArendCompositeElementImpl(node), NameHiding {
    val refIdentifier: ArendRefIdentifier
        get() = childOfTypeStrict()

    override fun getScopeContext() = ArendStatCmd.getScopeContext(firstRelevantChild)

    override fun getHiddenReference() = refIdentifier.referent
}