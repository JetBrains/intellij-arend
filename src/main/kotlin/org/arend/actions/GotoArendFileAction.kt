package org.arend.actions

import com.intellij.ide.actions.GotoFileAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.arend.util.arendModules

class GotoArendFileAction : GotoFileAction(){
    override fun actionPerformed(e: AnActionEvent) {
        showInSearchEverywherePopup(SearchArendFilesContributor::class.java.simpleName, e, true, true)
    }

    override fun hasContributors(context: DataContext?): Boolean =
        if (context != null) {
            val project: Project? = CommonDataKeys.PROJECT.getData(context)
            project?.arendModules?.isNotEmpty() ?: false
        } else false
}