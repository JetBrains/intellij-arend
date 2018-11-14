package org.arend.formatting

import com.intellij.codeInsight.editorActions.BackspaceModeOverride
import com.intellij.codeInsight.editorActions.SmartBackspaceMode

class ArendBackspaceDisabler : BackspaceModeOverride() {
    override fun getBackspaceMode(modeFromSettings: SmartBackspaceMode): SmartBackspaceMode {
        return if (modeFromSettings == SmartBackspaceMode.AUTOINDENT) SmartBackspaceMode.INDENT else modeFromSettings
    }
}