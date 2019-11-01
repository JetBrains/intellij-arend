package org.arend.toolWindow.errors.tree

import org.arend.typechecking.error.ArendError

interface ArendErrorTreeListener {
    fun errorAdded(arendError: ArendError) {}
    fun errorRemoved(arendError: ArendError) {}
}