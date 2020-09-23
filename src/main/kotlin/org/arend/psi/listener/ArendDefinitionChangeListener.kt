package org.arend.psi.listener

import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiConcreteReferable


interface ArendDefinitionChangeListener {
    fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean)
}