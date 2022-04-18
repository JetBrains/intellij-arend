package org.arend.injection.actions

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.util.castSafelyTo
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.Expression
import org.arend.injection.*
import org.arend.ui.showManipulatePrettyPrinterHint
import org.arend.util.ArendBundle
import kotlin.random.Random

class RevealingInformationCaretListener(private val injectedArendEditor: InjectedArendEditor) : CaretListener {
    override fun caretPositionChanged(event: CaretEvent) {
        if (event.caret?.hasSelection() == true) return
        injectedArendEditor.performPrettyPrinterManipulation(event.editor, Choice.SHOW_UI)
    }
}

enum class Choice {
    REVEAL,
    HIDE,
    SHOW_UI
}

fun InjectedArendEditor.performPrettyPrinterManipulation(editor: Editor, choice: Choice) {
    val (_, scope) = treeElement?.sampleError?.error?.let(InjectedArendEditor.Companion::resolveCauseReference)
        ?: return
    val ppConfig = getCurrentConfig(scope)
    val offset = editor.caretModel.offset
    val revealableFragment= findRevealableCoreAtOffset(offset, currentDoc, treeElement?.sampleError?.error, ppConfig) ?: return
    val id = "Arend Verbose level increase " + Random.nextInt()
    val revealingAction = getModificationAction(revealableFragment, editor, id, false, choice)
    val hidingAction = getModificationAction(revealableFragment, editor, id, true, choice)

    when (choice) {
        Choice.SHOW_UI -> showManipulatePrettyPrinterHint(editor, revealableFragment, revealingAction, hidingAction)
        Choice.REVEAL -> if (revealableFragment.revealLifetime > 0) revealingAction.invoke()
        Choice.HIDE -> if (revealableFragment.hideLifetime > 0) hidingAction.invoke()
    }
}

private fun InjectedArendEditor.getModificationAction(revealingFragment: RevealableFragment, editor: Editor, actionId: String, isInverted: Boolean, choice: Choice) : () -> Unit {
    val modificationUndoableAction = getUndoAction(revealingFragment, editor, actionId, isInverted) ?: return {}
    val indexOfRange = computeGlobalIndexOfRange(editor.caretModel.offset)
    return {
        CommandProcessor.getInstance().executeCommand(project, {
            modificationUndoableAction.redo()
            updateErrorText(actionId)  {
                if (choice != Choice.SHOW_UI) {
                    val newOffset = getOffsetInEditor(revealingFragment.relativeOffset, indexOfRange) ?: editor.caretModel.offset
                    editor.caretModel.moveToOffset(newOffset)
                }
            }
            UndoManager.getInstance(project).undoableActionPerformed(modificationUndoableAction)
        }, ArendBundle.message("arend.add.information.in.arend.messages"), actionId)
    }
}

private fun InjectedArendEditor.getUndoAction(
    revealingFragment: RevealableFragment,
    editor: Editor,
    id: String,
    inverted: Boolean
): UndoableConfigModificationAction<out Any>? {
    return when (val concreteResult = revealingFragment.result) {
        is ConcreteLambdaParameter -> UndoableConfigModificationAction(
            this,
            editor.document,
            verboseLevelParameterMap,
            concreteResult.expr.data.castSafelyTo<DependentLink>() ?: return null,
            inverted,
            id
        )

        is ConcreteRefExpr -> UndoableConfigModificationAction(
            this,
            editor.document,
            verboseLevelMap,
            concreteResult.expr.data.castSafelyTo<Expression>() ?: return null,
            inverted,
            id
        )
    }
}

private fun InjectedArendEditor.computeGlobalIndexOfRange(startOffset: Int) : Int {
    for ((idx, ranges) in getInjectionFile()?.injectionRanges?.withIndex() ?: return -1) {
        if (ranges.any { it.contains(startOffset) }) {
            return idx
        }
    }
    return -1
}


