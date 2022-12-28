package org.arend.resolving

import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ext.PsiLocatedReferable

class IntellijMetaReferable(private val underlyingRef: SmartPsiElementPointer<PsiLocatedReferable>?, precedence: Precedence, name: String, aliasPrecedence: Precedence, aliasName: String?, description: String, parent: LocatedReferable?) : MetaReferable(precedence, name, aliasPrecedence, aliasName, description, null, null, parent), IntellijTCReferable {
    override fun isEquivalent(ref: LocatedReferable) =
        precedence == ref.precedence && refName == ref.refName && aliasPrecedence == ref.aliasPrecedence && aliasName == ref.aliasName && description == ref.description

    override fun getUnderlyingReferable(): LocatedReferable = underlyingRef?.element ?: this

    override val isConsistent: Boolean
        get() {
            val underlyingRef = underlyingRef?.element
            return underlyingRef != null && isEquivalent(underlyingRef)
        }
}