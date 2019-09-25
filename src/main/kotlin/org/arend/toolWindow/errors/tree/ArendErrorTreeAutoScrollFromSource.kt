package org.arend.toolWindow.errors.tree

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.AutoScrollFromSourceHandler
import com.intellij.ui.UIBundle
import com.intellij.util.Processor
import org.arend.highlight.BasePass
import org.arend.psi.ArendFile
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.MessageType
import org.arend.toolWindow.errors.satisfies
import org.arend.typechecking.error.ErrorService
import java.util.*
import kotlin.collections.HashMap


class ArendErrorTreeAutoScrollFromSource(private val project: Project, private val tree: ArendErrorTree) : AutoScrollFromSourceHandler(project, tree, project) {
    init {
        install()
    }

    private var actionMap = HashMap<MessageType, MyAction>()

    fun setEnabled(types: EnumSet<MessageType>, enabled: Boolean) {
        val filterSet = project.service<ArendProjectSettings>().autoScrollFromSource
        for (type in types) {
            if (!enabled || (!(type == MessageType.RESOLVING || type == MessageType.PARSING) || filterSet.contains(MessageType.SHORT))) {
                actionMap[type]?.templatePresentation?.isEnabled = enabled
            }
        }
        if (enabled && filterSet.intersect(types).isNotEmpty()) {
            updateCurrentSelection()
        }
    }

    override fun install() {
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                selectInAlarm(event.editor)
            }
        }, project)
    }

    private fun selectInAlarm(editor: Editor?) {
        if (editor != null && tree.isShowing && isAutoScrollEnabled) {
            myAlarm.cancelAllRequests()
            myAlarm.addRequest({ selectElementFromEditor(editor) }, alarmDelay, modalityState)
        }
    }

    fun updateCurrentSelection() {
        val selectedEditor = (FileEditorManager.getInstance(myProject).selectedEditor as? TextEditor)?.editor
        if (selectedEditor != null) {
            runInEdt(ModalityState.NON_MODAL) {
                selectInAlarm(selectedEditor)
            }
        }
    }

    private fun selectElementFromEditor(editor: Editor) {
        if (editor.project != project) {
            return
        }

        val document = editor.document
        val offset = editor.caretModel.offset
        // Check that we are in a problem range
        if ((DocumentMarkupModel.forDocument(document, project, true) as? MarkupModelEx)?.processRangeHighlightersOverlappingWith(offset, offset, Processor.FALSE) == true) {
            return
        }

        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) as? ArendFile ?: return
        val arendErrors = project.service<ErrorService>().getErrors(file)
        if (arendErrors.isEmpty()) {
            return
        }

        val service = project.service<ArendProjectSettings>()
        for (arendError in arendErrors) {
            if (arendError.error.satisfies(service.autoScrollFromSource) && BasePass.getImprovedTextRange(arendError.error)?.contains(offset) == true) {
                tree.select(arendError.error)
                break
            }
        }
    }

    override fun selectElementFromEditor(editor: FileEditor) {
        (editor as? TextEditor)?.editor?.let { selectElementFromEditor(it) }
    }

    override fun setAutoScrollEnabled(enabled: Boolean) {}

    public override fun isAutoScrollEnabled(): Boolean {
        val service = project.service<ArendProjectSettings>()
        return MessageType.values().any { service.autoScrollFromSource.contains(it) && service.messagesFilterSet.contains(it) }
    }

    private inner class MyAction(private val type: MessageType) : ToggleAction("Autoscroll from ${type.toText()}s", null, null), DumbAware {
        override fun isSelected(e: AnActionEvent): Boolean {
            val settings = project.service<ArendProjectSettings>()
            return settings.autoScrollFromSource.contains(type) && settings.messagesFilterSet.contains(type) &&
                (!(type == MessageType.RESOLVING || type == MessageType.PARSING) || settings.autoScrollFromSource.contains(MessageType.SHORT) && settings.messagesFilterSet.contains(MessageType.SHORT))
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val settings = project.service<ArendProjectSettings>()
            if (state) {
                settings.autoScrollFromSource.add(type)
                updateCurrentSelection()
            } else {
                settings.autoScrollFromSource.remove(type)
            }

            for (type in EnumSet.of(MessageType.RESOLVING, MessageType.PARSING)) {
                if (!state || settings.messagesFilterSet.contains(type)) {
                    actionMap[type]?.templatePresentation?.isEnabled = state
                }
            }
        }
    }

    fun createActionGroup() = DefaultActionGroup(UIBundle.message("autoscroll.from.source.action.description"), true).apply {
        templatePresentation.icon = AllIcons.General.AutoscrollFromSource
        for (type in MessageType.values()) {
            val action = MyAction(type)
            add(action)
            actionMap[type] = action
        }

        val settings = project.service<ArendProjectSettings>()
        for (type in MessageType.values()) {
            val enabled = settings.messagesFilterSet.contains(type) &&
                (!(type == MessageType.RESOLVING || type == MessageType.PARSING) ||
                    settings.autoScrollFromSource.contains(MessageType.SHORT) && settings.messagesFilterSet.contains(MessageType.SHORT))
            if (!enabled) {
                actionMap[type]?.templatePresentation?.isEnabled = false
            }
        }
    }
}