package org.arend.psi.listener

import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable


interface ArendDefinitionChangeListener {
    fun updateDefinition(def: PsiLocatedReferable, file: ArendFile, isExternalUpdate: Boolean)
}