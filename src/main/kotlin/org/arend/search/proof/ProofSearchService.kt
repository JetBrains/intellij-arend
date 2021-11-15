package org.arend.search.proof

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.BigPopupUI
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.WindowStateService
import com.intellij.util.ui.JBInsets
import java.awt.Dimension

@Service(Service.Level.PROJECT)
class ProofSearchService {

    private var myProofSearchUI: ProofSearchUI? = null

    private var myBalloon: JBPopup? = null
    private var myBalloonFullSize: Dimension? = null

    fun show(e: AnActionEvent) {
        IdeEventQueue.getInstance().popupManager.closeAllPopups(false)

        val project: Project = e.project ?: return

        val proofSearchUI = createView(e)
        myProofSearchUI = proofSearchUI
        val balloon =
            with(JBPopupFactory.getInstance().createComponentPopupBuilder(proofSearchUI, proofSearchUI.searchField)) {
                setProject(project)
                setModalContext(false)
                setCancelOnClickOutside(true)
                setRequestFocus(true)
                setCancelKeyEnabled(false)
                setCancelCallback {
//                    if (isShown() && myProofSearchUI.getUserInputText() != searchText) {
//                        saveSearchText()
//                    }
                    true
                }
                addUserData("SIMPLE_WINDOW") // NON-NLS
                setResizable(true)
                setMovable(true)
                    .setDimensionServiceKey(project, "proof.search.location.key", true)
                setLocateWithinScreenBounds(false)
                createPopup()
            }.also {
                Disposer.register(it, proofSearchUI)
                Disposer.register(project, it)
            }
        myBalloon = balloon

        val size: Dimension = proofSearchUI.minimumSize
        JBInsets.addTo(size, balloon.content.insets)
        balloon.setMinimumSize(size)

        Disposer.register(balloon) {
//            saveSize()
            myProofSearchUI = null
            myBalloon = null
            myBalloonFullSize = null
        }

        if (proofSearchUI.viewType == BigPopupUI.ViewType.SHORT) {
            myBalloonFullSize =
                WindowStateService.getInstance(project).getSize("proof.search.location.key")
            val prefSize: Dimension = proofSearchUI.preferredSize
            balloon.size = prefSize
        }
        calcPositionAndShow(project, balloon, proofSearchUI)
    }

    private fun calcPositionAndShow(project: Project, balloon: JBPopup, proofSearchUI: ProofSearchUI) {
        val savedLocation =
            WindowStateService.getInstance(project).getLocation("proof.search.location.key")
        balloon.showCenteredInCurrentWindow(project)

        //for first show and short mode popup should be shifted to the top screen half
        if (savedLocation == null && proofSearchUI.viewType == BigPopupUI.ViewType.SHORT) {
            val location = balloon.locationOnScreen
            location.y /= 2
            balloon.setLocation(location)
        }
    }


    private fun isShown(): Boolean {
        return myProofSearchUI != null && myBalloon.let { it != null && !it.isDisposed }
    }


    private fun createView(event: AnActionEvent): ProofSearchUI {
        val view = ProofSearchUI(event.project)
        view.setSearchFinishedHandler {
            if (isShown()) {
                myBalloon?.cancel()
            }
        }
        view.addViewTypeListener { viewType: BigPopupUI.ViewType ->
            if (!isShown()) {
                return@addViewTypeListener
            }
            ApplicationManager.getApplication().invokeLater {
                // this is EDT, all notnull-assertions are justified by this fact
                val balloon = myBalloon ?: return@invokeLater
                val minSize = view.minimumSize
                JBInsets.addTo(minSize, balloon.content.insets)
                balloon.setMinimumSize(minSize)
                if (viewType == BigPopupUI.ViewType.SHORT) {
                    myBalloonFullSize = balloon.size
                    JBInsets.removeFrom(myBalloonFullSize!!, balloon.content.insets)
                    balloon.pack(false, true)
                } else {
                    if (myBalloonFullSize == null) {
                        myBalloonFullSize = view.preferredSize
                        JBInsets.addTo(myBalloonFullSize!!, balloon.content.insets)
                    }
                    myBalloonFullSize!!.height = Integer.max(myBalloonFullSize!!.height, minSize.height)
                    myBalloonFullSize!!.width = Integer.max(myBalloonFullSize!!.width, minSize.width)
                    balloon.size = myBalloonFullSize!!
                }
            }
        }
        return view
    }
}