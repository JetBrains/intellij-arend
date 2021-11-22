package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.settings.ArendProjectSettings

class GearActionGroup(searchUI: ProofSearchUI, project: Project) : DefaultActionGroup(), DumbAware {
    init {
        templatePresentation.icon = AllIcons.General.Gear
        templatePresentation.text = IdeBundle.message("show.options.menu")
        addAction(ExcludeTests(searchUI, project))
    }
}

private class ExcludeTests(val searchUI: ProofSearchUI, private val project: Project) : ToggleAction("Exclude test locations") {
    override fun isSelected(e: AnActionEvent): Boolean = project.service<ArendProjectSettings>().data.ignoreTestLocations

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        project.service<ArendProjectSettings>().data.ignoreTestLocations = state
        searchUI.runProofSearch(null)
    }
}
