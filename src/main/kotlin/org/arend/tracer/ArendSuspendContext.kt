package org.arend.tracer

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import org.arend.ArendIcons
import org.arend.core.expr.*
import org.arend.util.ArendBundle

class ArendSuspendContext(traceEntry: ArendTraceEntry, contextView: ArendTraceContextView) : XSuspendContext() {
    private val traceStack = TraceStack(traceEntry, contextView)

    override fun getActiveExecutionStack(): XExecutionStack = traceStack

    private class TraceStack(traceEntry: ArendTraceEntry, contextView: ArendTraceContextView) :
        XExecutionStack("") {

        private val frames = traceEntry.stack.map { StackFrame(it, contextView) }

        override fun getTopFrame(): XStackFrame? = frames.getOrNull(0)

        override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
            val subFrames =
                if (firstFrameIndex < frames.size) frames.subList(firstFrameIndex, frames.size) else emptyList()
            container?.addStackFrames(ArrayList(subFrames), true)
        }
    }

    private class StackFrame(private val traceEntry: ArendTraceEntry, private val contextView: ArendTraceContextView) :
        XStackFrame() {
        private val position = SourcePosition.create(traceEntry)

        private val icon = when (val expression = traceEntry.coreExpression) {
            is ConCallExpression -> ArendIcons.CONSTRUCTOR
            is DataCallExpression -> ArendIcons.DATA_DEFINITION
            is FunCallExpression -> ArendIcons.FUNCTION_DEFINITION
            is ClassCallExpression ->
                if (expression.definition.isRecord) ArendIcons.RECORD_DEFINITION else ArendIcons.CLASS_DEFINITION
            is FieldCallExpression -> ArendIcons.CLASS_FIELD
            is LamExpression -> ArendIcons.LAMBDA_EXPRESSION
            else -> ArendIcons.EXPRESSION
        }

        override fun getSourcePosition(): XSourcePosition? = position

        override fun customizePresentation(component: ColoredTextContainer) {
            val psiText = traceEntry.psiElement?.text?.let {
                StringUtil.shortenTextWithEllipsis(it, 40, 0)
            } ?: ArendBundle.message("arend.tracer.unknown.expression")
            component.append(psiText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            if (position != null) {
                val positionText = " (${position.file.name}:${position.line + 1})"
                component.append(positionText, SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
            }
            component.setIcon(icon)
        }

        override fun computeChildren(node: XCompositeNode) {
            node.addChildren(XValueChildrenList.EMPTY, true)
            contextView.update(traceEntry)
        }
    }

    private class SourcePosition(basePosition: XSourcePosition, private val range: TextRange) :
        XSourcePosition by basePosition,
        ExecutionPointHighlighter.HighlighterProvider {

        override fun getHighlightRange(): TextRange = range

        companion object {
            fun create(traceEntry: ArendTraceEntry): SourcePosition? {
                val psiElement = traceEntry.psiElement ?: return null
                val basePosition = XDebuggerUtil.getInstance().createPositionByElement(psiElement) ?: return null
                return SourcePosition(basePosition, psiElement.textRange)
            }
        }
    }
}
