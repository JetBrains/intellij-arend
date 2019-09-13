package org.arend.psi.listener

import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile


interface ArendDefinitionChangeListener {
    fun updateDefinition(def: ArendDefinition, file: ArendFile, isExternalUpdate: Boolean)
}