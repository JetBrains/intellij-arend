package org.arend.tracer

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredText
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import org.arend.ArendIcons
import javax.swing.Icon

class ArendSuspendContextTest : ArendTraceTestBase() {
    companion object {
        const val pmap =
            """\func pmap {A B : \Type} (f : A -> B) {a a' : A} (p : a = a') : f a = f a' => path (\lam i => f (p @ i))"""
    }

    lateinit var contextView: ArendTraceContextView

    override fun setUp() {
        super.setUp()
        contextView = ArendTraceContextView(project)
        disposeOnTearDown(Disposable { contextView.release() })
    }

    fun `test simple`() {
        val tracingData = doTrace(
            """
            $pmap
            \func test (a b : Nat) (p : a = b) => pmap suc (pmap suc {-caret-}p)
        """
        )
        val traceEntry = getFirstEntry(tracingData)!!
        val suspendContext = ArendSuspendContext(traceEntry, contextView)
        val frame = suspendContext.activeExecutionStack.topFrame!!
        assertPresentation(ArendIcons.EXPRESSION, "p (Main.ard:3)", frame)
    }

    fun `test long expression`() {
        val tracingData = doTrace(
            """
            $pmap
            \func test (a b : Nat) (very_long_parameter_name : a = b) => {-caret-}pmap suc (pmap suc very_long_parameter_name)
        """
        )
        val traceEntry = getFirstEntry(tracingData)!!
        val suspendContext = ArendSuspendContext(traceEntry, contextView)
        val frame = suspendContext.activeExecutionStack.topFrame!!
        assertPresentation(ArendIcons.EXPRESSION, "pmap suc (pmap suc very_long_parameter_${StringUtil.ELLIPSIS} (Main.ard:3)", frame)
    }

    fun `test cowith function body`() {
        val tracingData = doTrace(
            """
            \class SomeRelation (A : \Set)
              | \infix 4 ~ : A -> A -> \Prop
              | ~-reflexive {x : A} : x ~ x
            
            \func test{-caret-} : SomeRelation Nat \cowith
              | ~ => =
              | ~-reflexive => {?}
        """
        )
        val traceEntry = getFirstEntry(tracingData)!!
        val suspendContext = ArendSuspendContext(traceEntry, contextView)
        val frame = getFrames(suspendContext.activeExecutionStack).last()
        assertPresentation(ArendIcons.EXPRESSION, """\cowith${StringUtil.ELLIPSIS} (Main.ard:6)""", frame)
    }

    // TODO doesn't work, the body of the meta is not resolved for some reason.
    fun `_test meta expression`() {
        val tracingData = doTrace(
            """
            \meta meta-zero => 0
            \func test{-caret-} : Nat => meta-zero
        """
        )
        val traceEntry = tracingData.trace.entries.last()
        val suspendContext = ArendSuspendContext(traceEntry, contextView)
        val frame = suspendContext.activeExecutionStack.topFrame!!
        assertPresentation(ArendIcons.EXPRESSION, "0 (Main.ard:3:meta-zero)", frame)
    }

    private fun getFrames(stack: XExecutionStack) =
        StackFramesCollector().apply { stack.computeStackFrames(0, this) }.frames

    private fun assertPresentation(icon: Icon, text: String, frame: XStackFrame) {
        val presentation = getPresentation(frame)
        assertEquals(icon, presentation.frameIcon)
        assertEquals(text, presentation.toString())
    }

    private fun getPresentation(frame: XStackFrame) = StackFramePresentation().apply(frame::customizePresentation)

    class StackFramesCollector : XExecutionStack.XStackFrameContainer {
        val frames: MutableList<XStackFrame> = mutableListOf()

        override fun addStackFrames(stackFrames: MutableList<out XStackFrame>, last: Boolean) {
            frames += stackFrames
        }

        override fun errorOccurred(errorMessage: String) {}
    }

    class StackFramePresentation : SimpleColoredText() {
        var frameIcon: Icon? = null

        override fun setIcon(icon: Icon?) {
            frameIcon = icon
        }
    }
}