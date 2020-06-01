package org.arend.refactoring

import org.arend.ext.module.LongName
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.reference.ArendRef
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable

class PsiLocatedRenamer(val element: ArendCompositeElement): DefinitionRenamer {
    private val deferredNsCmdActions = ArrayList<NsCmdRefactoringAction>()

    override fun renameDefinition(ref: ArendRef?): LongName? {
        if (ref is PsiLocatedReferable) {
            val file = element.containingFile as? ArendFile ?: return null
            val pair = calculateReferenceName(LocationData(ref), file, element, false, deferredNsCmdActions) ?: return null
            val action = pair.first
            if (action != null) deferredNsCmdActions.add(action)
            return LongName(pair.second)
        }
        return null
    }

    fun writeAllImportCommands() {
        for (l in deferredNsCmdActions) l.execute()
        deferredNsCmdActions.clear()
    }
}