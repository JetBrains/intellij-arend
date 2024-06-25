package org.arend.resolving

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import org.arend.naming.reference.LevelDefinition
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCLevelReferable
import org.arend.psi.ext.PsiLocatedReferable

class IntellijTCLevelReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, name: String, parent: LevelDefinition?) : TCLevelReferable(data, name, parent), IntellijTCReferable {
    @Suppress("UNCHECKED_CAST")
    override fun getData(): SmartPsiElementPointer<PsiLocatedReferable> =
        super.getData() as SmartPsiElementPointer<PsiLocatedReferable>

    override fun isEquivalent(ref: LocatedReferable) =
        refName == ref.refName

    override val isConsistent: Boolean
        get() {
            val underlyingRef = runReadAction { data.element }
            return underlyingRef != null && isEquivalent(underlyingRef)
        }

    override var displayName: String? = refLongName.toString()
}