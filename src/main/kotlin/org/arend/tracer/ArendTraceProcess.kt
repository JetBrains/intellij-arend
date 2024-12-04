package org.arend.tracer

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.LayoutViewOptions
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.DebuggerSupport
import com.intellij.xdebugger.impl.actions.*
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerActionHandler
import com.intellij.xdebugger.ui.XDebugTabLayouter
import org.arend.ArendFileTypeInstance
import org.arend.psi.ext.ArendExpr
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.util.ArendBundle
import org.jetbrains.uast.util.classSetOf
import org.jetbrains.uast.util.isInstanceOf
import javax.swing.Icon

class ArendTraceProcess(session: XDebugSession, private val tracingData: ArendTracingData) :
    XDebugProcess(session) {
    private val trace = tracingData.trace
    private var traceEntryIndex: Int = tracingData.firstEntryIndex

    private val contextViewPanel = JBUI.Panels.simplePanel()
    private val emptyContextView =
        JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.tracer.nothing.to.show"))
    private lateinit var contextView: ArendTraceContextView

    override fun registerAdditionalActions(
        leftToolbar: DefaultActionGroup,
        topToolbar: DefaultActionGroup,
        settings: DefaultActionGroup
    ) {
        super.registerAdditionalActions(leftToolbar, topToolbar, settings)
        leftToolbar.childActionsOrStubs
            .filter { it.isInstanceOf(removedLeftToolbarActions) }
            .forEach(leftToolbar::remove)
        topToolbar.childActionsOrStubs
            .filter { it.isInstanceOf(removedTopToolbarActions) }
            .forEach(topToolbar::remove)
        val runToCursorAction = ActionManager.getInstance().getAction(RUN_TO_CURSOR_ACTION_ID)
        val nextEntryAction = TracerAction(
            ArendBundle.message("arend.trace.next.entry.action.name"),
            ArendBundle.message("arend.trace.next.entry.action.description"),
            AllIcons.Vcs.Arrow_right,
            NextEntryActionHandler()
        )
        val prevEntryAction = TracerAction(
            ArendBundle.message("arend.trace.prev.entry.action.name"),
            ArendBundle.message("arend.trace.prev.entry.action.description"),
            AllIcons.Vcs.Arrow_left,
            PrevEntryActionHandler()
        )
        topToolbar.add(Separator.create(), Constraints.FIRST)
        runToCursorAction
            ?.run { topToolbar.add(this, Constraints.FIRST) }
            ?: LOGGER.error("Cannot add 'Run To Cursor' action to the toolbar")
        topToolbar.add(nextEntryAction, Constraints.FIRST)
        topToolbar.add(prevEntryAction, Constraints.FIRST)
    }

    override fun createTabLayouter(): XDebugTabLayouter = object : XDebugTabLayouter() {
        override fun registerAdditionalContent(ui: RunnerLayoutUi) {
            contextView = ArendTraceContextView(session.project)
            contextViewPanel.add(contextView.component!!)
            val contextContent = ui.createContent(CONTEXT_CONTENT, contextViewPanel, "Context", null, null)
            ui.addContent(contextContent, 0, PlaceInGrid.center, false)
        }
    }

    override fun sessionInitialized() {
        focusContextViewOnStartup()
        if (trace.entries.isEmpty()) {
            session.reportMessage(ArendBundle.message("arend.tracer.nothing.to.trace"), MessageType.WARNING)
            session.stop()
            return
        }
        if (tracingData.hasErrors) {
            session.reportMessage(ArendBundle.message("arend.tracer.declaration.has.errors"), MessageType.WARNING)
        }
        val firstEntry = trace.entries.getOrNull(traceEntryIndex)
        if (firstEntry == null) {
            session.reportMessage(
                ArendBundle.message("arend.tracer.cannot.find.starting.expression"),
                MessageType.WARNING
            )
            traceEntryIndex = 0
            session.positionReached(ArendSuspendContext(trace.entries[0], contextView))
            return
        }
        session.positionReached(ArendSuspendContext(firstEntry, contextView))
    }

    override fun resume(context: XSuspendContext?) {
        doNothing(context)
    }

    override fun startStepOver(context: XSuspendContext?) {
        doNothing(context)
    }

    override fun startStepInto(context: XSuspendContext?) {
        doNothing(context)
    }

    override fun startStepOut(context: XSuspendContext?) {
        doNothing(context)
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        ActionUtil.underModalProgress(session.project, ArendBundle.message("arend.tracer.collecting.tracing.data")) {
            try {
                val entryIndex = findEntryIndex(position)
                val entry = trace.entries.getOrNull(entryIndex)
                if (entry == null) {
                    session.reportMessage(ArendBundle.message("arend.tracer.cannot.trace.here"), MessageType.WARNING)
                    doNothing(context)
                    return@underModalProgress
                }
                traceEntryIndex = entryIndex
                session.positionReached(ArendSuspendContext(entry, contextView))
            } catch (e: ProcessCanceledException) {
                doNothing(context)
            }
        }
    }

    override fun stop() {
        contextViewPanel.removeAll()
        contextViewPanel.add(emptyContextView)
        contextView.release()
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider = EditorsProvider

    private fun focusContextViewOnStartup() {
        session.ui.defaults.initContentAttraction(
            CONTEXT_CONTENT,
            LayoutViewOptions.STARTUP,
            LayoutAttractionPolicy.FocusOnce(false)
        )
    }

    private fun doNothing(context: XSuspendContext?) {
        if (context != null) {
            session.positionReached(context)
        } else {
            session.reportError(ArendBundle.message("arend.tracer.command.failed"))
            session.stop()
        }
    }

    private fun findEntryIndex(position: XSourcePosition): Int {
        val file = PsiManager.getInstance(session.project).findFile(position.file) as? ArendFile ?: return -1
        val expression =
            PsiTreeUtil.findElementOfClassAtOffset(file, position.offset, ArendExpr::class.java, false) ?: return -1
        return trace.indexOfEntry(expression)
    }

    private inner class NextEntryActionHandler : XDebuggerSuspendedActionHandler() {
        override fun perform(session: XDebugSession, dataContext: DataContext) {
            session.sessionResumed()
            val entry = trace.entries.getOrNull(++traceEntryIndex)
            if (entry == null) {
                session.stop()
                return
            }
            session.positionReached(ArendSuspendContext(entry, contextView))
        }
    }

    private inner class PrevEntryActionHandler : XDebuggerSuspendedActionHandler() {
        override fun perform(session: XDebugSession, dataContext: DataContext) {
            session.sessionResumed()
            val entry = trace.entries.getOrNull(--traceEntryIndex)
            if (entry == null) {
                doNothing(session.suspendContext)
                return
            }
            session.positionReached(ArendSuspendContext(entry, contextView))
        }

        override fun isEnabled(project: Project, event: AnActionEvent): Boolean =
            super.isEnabled(project, event) && traceEntryIndex > 0
    }

    private class TracerAction(
        text: String,
        description: String,
        icon: Icon,
        private val handler: XDebuggerActionHandler
    ) : XDebuggerActionBase() {
        init {
            templatePresentation.text = text
            templatePresentation.description = description
            templatePresentation.icon = icon
        }

        override fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler = handler
    }

    private object EditorsProvider : XDebuggerEditorsProviderBase() {
        override fun getFileType(): FileType = ArendFileTypeInstance
        override fun createExpressionCodeFragment(
            project: Project,
            text: String,
            context: PsiElement?,
            isPhysical: Boolean
        ): PsiFile = ArendPsiFactory(project).injected(text)
    }

    companion object {
        private val LOGGER = Logger.getInstance(ArendTraceProcess::class.java)

        private const val RUN_TO_CURSOR_ACTION_ID = "RunToCursor"
        private const val CONTEXT_CONTENT = "ArendTraceContextContent"

        private val removedLeftToolbarActions = classSetOf(
            ResumeAction::class.java,
            PauseAction::class.java,
            ViewBreakpointsAction::class.java,
            MuteBreakpointAction::class.java
        )

        private val removedTopToolbarActions = classSetOf(
            StepOverAction::class.java,
            StepIntoAction::class.java,
            ForceStepIntoAction::class.java,
            StepOutAction::class.java,
            RunToCursorAction::class.java
        )
    }
}