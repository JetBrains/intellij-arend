package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.term.group.AccessModifier

class IntellijMetaReferable(private val underlyingRef: SmartPsiElementPointer<PsiLocatedReferable>?, accessModifier: AccessModifier, precedence: Precedence, name: String, aliasPrecedence: Precedence, aliasName: String?, parent: LocatedReferable?) : MetaReferable(underlyingRef, accessModifier, precedence, name, aliasPrecedence, aliasName, null, null, parent), IntellijTCReferable {
    override fun isEquivalent(ref: LocatedReferable) =
        precedence == ref.precedence && refName == ref.refName && aliasPrecedence == ref.aliasPrecedence && aliasName == ref.aliasName

    override fun getUnderlyingReferable(): LocatedReferable = runReadAction {
        underlyingRef?.element ?: this
    }

    override val isConsistent: Boolean
        get() {
            val underlyingRef = underlyingRef?.element
            return underlyingRef != null && isEquivalent(underlyingRef)
        }

    override var displayName: String? = refLongName.toString()
}