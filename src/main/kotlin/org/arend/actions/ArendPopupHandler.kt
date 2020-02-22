package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager


abstract class ArendPopupHandler(val requestFocus: Boolean) : CodeInsightActionHandler {
    override fun startInWriteAction() = false

    inline fun displayHint(crossinline f: HintManager.() -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance().apply { setRequestFocusForNextHint(requestFocus) }.f()
        }
    }
}
