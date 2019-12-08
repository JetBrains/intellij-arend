package org.arend.psi.listener

import org.arend.psi.ArendFile
import org.arend.psi.ext.TCDefinition


interface ArendDefinitionChangeListener {
    fun updateDefinition(def: TCDefinition, file: ArendFile, isExternalUpdate: Boolean)
}