package org.arend.search.proof

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.BigPopupUI
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.WindowStateService
import com.intellij.util.ui.JBInsets
import java.awt.Container
import java.awt.Dimension

@Service(Service.Level.PROJECT)
class ProofSearchService: Disposable {

    private var myProofSearchUI: ProofSearchUI? = null

    private var myBalloon: JBPopup? = null
    private var myBalloonFullSize: Dimension? = null
    private var lastSearchText: String? = null

    init {
        installScrollingActions()
    }

    fun show(e: AnActionEvent) {
        IdeEventQueue.getInstance().popupManager.closeAllPopups(false)

        val project: Project = e.project ?: return

        val signatureSearchUI = createView(e)
        myProofSearchUI = signatureSearchUI
        val balloon =
            with(JBPopupFactory.getInstance().createComponentPopupBuilder(signatureSearchUI, signatureSearchUI.editorSearchField)) {
                setProject(project)
                setModalContext(false)
                setCancelOnClickOutside(true)
                setRequestFocus(true)
                setCancelKeyEnabled(false)
                setCancelCallback {
                    if (isShown()) {
                        lastSearchText = myProofSearchUI?.editorSearchField?.text
                    }
                    true
                }
                addUserData("SIMPLE_WINDOW") // NON-NLS
                setResizable(true)
                setMovable(true)
                    .setDimensionServiceKey(project, PROOF_SEARCH_LOCATION_KEY, true)
                setLocateWithinScreenBounds(false)
                createPopup()
            }.also {
                Disposer.register(it, signatureSearchUI)
                Disposer.register(project, it)
            }
        myBalloon = balloon

        Disposer.register(balloon) {
            if (signatureSearchUI.viewType == BigPopupUI.ViewType.SHORT) {
                WindowStateService.getInstance(project).putSize(PROOF_SEARCH_LOCATION_KEY, myBalloonFullSize)
            }
            myProofSearchUI = null
            myBalloon = null
            myBalloonFullSize = null
        }

        if (signatureSearchUI.viewType == BigPopupUI.ViewType.SHORT) {
            myBalloonFullSize = WindowStateService.getInstance(project).getSize(PROOF_SEARCH_LOCATION_KEY)
            balloon.size = signatureSearchUI.preferredSize
        }
        calcPositionAndShow(project, balloon, signatureSearchUI)
        val text = signatureSearchUI.editorSearchField.text
        if (text.isNotEmpty()) {
            signatureSearchUI.editorSearchField.editor?.selectionModel?.setSelection(0, text.length)
            signatureSearchUI.registerSearchAttempt()
        }
    }

    private fun calcPositionAndShow(project: Project, balloon: JBPopup, proofSearchUI: ProofSearchUI) {
        val savedLocation = WindowStateService.getInstance(project).getLocation(PROOF_SEARCH_LOCATION_KEY)
        balloon.showCenteredInCurrentWindow(project)

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
        val view = ProofSearchUI(event.project!!, event.getData(CommonDataKeys.CARET))
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

        val lastText = lastSearchText
        if (lastText != null) {
            view.editorSearchField.text = lastText
        }
        return view
    }

    private fun installScrollingActions() {
        val actionManager = EditorActionManager.getInstance()
        if (actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) !is MoveDownEditorAction) {
            actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, MoveDownEditorAction())
        }
        if (actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP) !is MoveUpEditorAction) {
            actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, MoveUpEditorAction())
        }
    }

    override fun dispose() {
        val actionManager = EditorActionManager.getInstance()
        val downAction = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
        if (downAction is DelegatingProofSearchScrollingAction) {
            actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, downAction.originalAction)
        }
        val upAction = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
        if (upAction is DelegatingProofSearchScrollingAction) {
            actionManager.setActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_UP, upAction.originalAction)
        }
    }
}

private const val PROOF_SEARCH_LOCATION_KEY = "proof.search.location.key"


private abstract class DelegatingProofSearchScrollingAction(editorEvent: String): EditorActionHandler() {
    val originalAction: EditorActionHandler = EditorActionManager.getInstance().getActionHandler(editorEvent)

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
        return editor.getUserData(PROOF_SEARCH_EDITOR) != null || originalAction.isEnabled(editor, caret, dataContext)
    }

    protected abstract fun doOwnAction(ui: ProofSearchUI)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        if (editor.getUserData(PROOF_SEARCH_EDITOR) != null && LookupManager.getActiveLookup(editor) == null) {
            var ui : Container = editor.component
            while (ui.parent != null && ui !is ProofSearchUI) {
                ui = ui.parent
            }
            if (ui !is ProofSearchUI) {
                originalAction.execute(editor, caret, dataContext)
                return
            }
            doOwnAction(ui)
        } else {
            originalAction.execute(editor, caret, dataContext)
        }
    }
}

private class MoveDownEditorAction: DelegatingProofSearchScrollingAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN) {
    override fun doOwnAction(ui: ProofSearchUI) = ui.moveListDown()
}

private class MoveUpEditorAction: DelegatingProofSearchScrollingAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP) {
    override fun doOwnAction(ui: ProofSearchUI) = ui.moveListUp()
}

val PROOF_SEARCH_EDITOR : Key<Unit> = Key.create("PROOF_SEARCH_PATTERN_EDITOR")