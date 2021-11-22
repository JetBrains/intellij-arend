package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.popup.PopupState
import org.arend.settings.ArendProjectSettings
import java.awt.event.MouseEvent



class GearActionGroup(val searchUI: ProofSearchUI, val project: Project) : AnAction(AllIcons.General.GearPlain), DumbAware {
    val myPopupState = PopupState.forPopupMenu()


    override fun actionPerformed(e: AnActionEvent) {
        if (myPopupState.isRecentlyHidden) return // do not show new popup
        val popup = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, ActualGearActionGroup(searchUI, project))
        val inputEvent = e.inputEvent
        var x = 0
        var y = 0
        if (inputEvent is MouseEvent) {
            x = inputEvent.x
            y = inputEvent.y
        }
        myPopupState.prepareToShow(popup.component)
        popup.component.show(inputEvent.component, x, y)
    }

    private class ActualGearActionGroup(searchUI: ProofSearchUI, project: Project) : DefaultActionGroup(), DumbAware {
        init {
            templatePresentation.icon = AllIcons.General.GearPlain
            templatePresentation.text = IdeBundle.message("show.options.menu")
            addAction(ExcludeTests(searchUI, project))
            addAction(ExcludeNonProjectFiles(searchUI, project))
        }
    }
}

private class ExcludeNonProjectFiles(val searchUI: ProofSearchUI, val project: Project) : ToggleAction("Include non-project locations") {
    override fun isSelected(e: AnActionEvent): Boolean = project.service<ArendProjectSettings>().data.includeNonProjectLocations

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        project.service<ArendProjectSettings>().data.includeNonProjectLocations = state
        searchUI.runProofSearch(null)
    }
}

private class ExcludeTests(val searchUI: ProofSearchUI, private val project: Project) : ToggleAction("Include test locations") {
    override fun isSelected(e: AnActionEvent): Boolean = project.service<ArendProjectSettings>().data.includeTestLocations

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        project.service<ArendProjectSettings>().data.includeTestLocations = state
        searchUI.runProofSearch(null)
    }
}
