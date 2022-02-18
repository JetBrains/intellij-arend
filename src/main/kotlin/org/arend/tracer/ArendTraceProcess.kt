package org.arend.tracer

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.LayoutViewOptions
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProviderBase
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.actions.*
import com.intellij.xdebugger.ui.XDebugTabLayouter
import org.arend.ArendFileType
import org.arend.psi.ArendPsiFactory
import org.arend.util.ArendBundle
import org.jetbrains.uast.util.classSetOf
import org.jetbrains.uast.util.isInstanceOf

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
        val entry = trace.entries.getOrNull(++traceEntryIndex)
        if (entry == null) {
            session.stop()
            return
        }
        session.positionReached(ArendSuspendContext(entry, contextView))
    }

    override fun startStepInto(context: XSuspendContext?) {
        doNothing(context)
    }

    override fun startStepOut(context: XSuspendContext?) {
        doNothing(context)
    }

    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
        doNothing(context)
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

    private object EditorsProvider : XDebuggerEditorsProviderBase() {
        override fun getFileType(): FileType = ArendFileType
        override fun createExpressionCodeFragment(
            project: Project,
            text: String,
            context: PsiElement?,
            isPhysical: Boolean
        ): PsiFile = ArendPsiFactory(project).injected(text)
    }

    companion object {
        private const val CONTEXT_CONTENT = "ArendTraceContextContent"

        private val removedLeftToolbarActions = classSetOf(
            ResumeAction::class.java,
            PauseAction::class.java,
            ViewBreakpointsAction::class.java,
            MuteBreakpointAction::class.java
        )

        private val removedTopToolbarActions = classSetOf(
            StepIntoAction::class.java,
            ForceStepIntoAction::class.java,
            StepOutAction::class.java,
            RunToCursorAction::class.java
        )
    }
}