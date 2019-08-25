package org.arend.toolWindow.errors.tree

import org.arend.error.GeneralError

interface ArendErrorTreeListener {
    fun errorAdded(error: GeneralError) {}
    fun errorRemoved(error: GeneralError) {}
}