package org.arend.actions

import com.intellij.ide.actions.GotoFileAction
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.openapi.actionSystem.AnActionEvent

class GotoArendFileAction : GotoFileAction(){
    override fun actionPerformed(e: AnActionEvent) {
        showInSearchEverywherePopup(SearchArendFilesContributor::class.java.simpleName, e, true, true)
    }
}