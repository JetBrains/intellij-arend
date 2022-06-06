package org.arend.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import com.intellij.ui.components.JBLabel
import org.arend.ArendIcons
import org.arend.injection.*
import org.arend.util.ArendBundle
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.*
import javax.swing.border.Border

fun showManipulatePrettyPrinterHint(editor: Editor, fragment: RevealableFragment, revealingCallback: () -> Unit, hidingCallback: () -> Unit) {
    if (fragment.revealLifetime == 0 && fragment.hideLifetime == 0) {
        return
    }
    val offset = editor.caretModel.offset
    val visualPosition = editor.offsetToVisualPosition(offset)
    val frameOwner = WindowManager.getInstance().getFrame(editor.project) ?: return
    val point = editor.visualPositionToXY(visualPosition)
        .apply { move(x + 2 - frameOwner.x, y - (editor.lineHeight.toDouble() * 2.5).toInt() - frameOwner.y)  }
    val component = ArendManipulateImplicitArgumentComponent(fragment, revealingCallback, hidingCallback)
    val flags =
        HintManager.HIDE_BY_ANY_KEY or HintManager.UPDATE_BY_SCROLLING or HintManager.DONT_CONSUME_ESCAPE or HintManager.HIDE_BY_CARET_MOVE

    HintManagerImpl.getInstanceImpl().showEditorHint(
        LocalComponentHint(component), editor,
        Point(
            editor.contentComponent.locationOnScreen.x + point.x,
            editor.contentComponent.locationOnScreen.y + point.y
        ), flags, -1, true
    )
}

private class ArendManipulateImplicitArgumentComponent(private val fragment: RevealableFragment, revealingCallback: () -> Unit, hidingCallback: () -> Unit) :
    JPanel() {

    override fun addMouseListener(l: MouseListener?) {}

    private val myShowIconLabel =
        JLabel(ArendIcons.SHOW).apply { isOpaque = false }

    private val myNotShowIconLabel = JBLabel(ArendIcons.NOT_SHOW)
    
    private fun createBorder(thickness: Int): Border {
        return BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                EditorColorsManager.getInstance().globalScheme.getColor(
                    EditorColors.SELECTED_TEARLINE_COLOR
                ), thickness, true
            ), BorderFactory.createEmptyBorder(5, 5, 5, 5)
        )
    }

    private val selectedBorder = createBorder(2)
    private val defaultBorder = createBorder(1)
    private var revealLifetime = fragment.revealLifetime
    private var endLifetime = fragment.hideLifetime

    init {
        layout = BorderLayout()
        isOpaque = true
        myShowIconLabel.border = defaultBorder
        myNotShowIconLabel.border = defaultBorder
        if (endLifetime > 0) {
            add(myNotShowIconLabel, if (revealLifetime > 0) BorderLayout.WEST else BorderLayout.CENTER)
        }
        if (endLifetime > 0 && revealLifetime > 0) {
            add(JSeparator(JSeparator.VERTICAL), BorderLayout.CENTER)
        }
        if (revealLifetime > 0) {
            add(myShowIconLabel, if (endLifetime > 0) BorderLayout.EAST else BorderLayout.CENTER)
        }
        myShowIconLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                myShowIconLabel.border = selectedBorder
                myShowIconLabel.toolTipText = when (fragment.result) {
                    is ConcreteLambdaParameter -> ArendBundle.message("arend.reveal.lambda.parameter.type")
                    is ConcreteRefExpr -> ArendBundle.message("arend.reveal.implicit.argument")
                    is ConcreteTuple -> ArendBundle.message("arend.reveal.type.of.tuple")
                    is ConcreteImplementation -> ArendBundle.message("arend.reveal.proof")
                } + " (${
                    KeymapUtil.getFirstKeyboardShortcutText(
                        ActionManager.getInstance().getAction("Arend.PrettyPrint.RevealImplicitInformation") as AnAction
                    )
                })"
            }

            override fun mouseExited(e: MouseEvent?) {
                myShowIconLabel.border = defaultBorder
            }

            override fun mouseClicked(e: MouseEvent?) {
                myShowIconLabel.border = defaultBorder
                val timer = Timer(50) { myShowIconLabel.border = selectedBorder }
                revealLifetime -= 1
                revealingCallback()
                if (revealLifetime <= 0) {
                    this@ArendManipulateImplicitArgumentComponent.isVisible = false
                } else {
                    timer.start()
                }
            }
        })
        myNotShowIconLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                myNotShowIconLabel.border = selectedBorder
                myNotShowIconLabel.toolTipText = when (fragment.result) {
                    is ConcreteLambdaParameter -> ArendBundle.message("arend.hide.lambda.parameter.type")
                    is ConcreteRefExpr -> ArendBundle.message("arend.hide.implicit.argument")
                    is ConcreteTuple -> ArendBundle.message("arend.hide.type.of.tuple")
                    is ConcreteImplementation -> ArendBundle.message("arend.hide.proof")
                } + " (${
                    KeymapUtil.getFirstKeyboardShortcutText(
                        ActionManager.getInstance().getAction("Arend.PrettyPrint.HideImplicitInformation") as AnAction
                    )
                })"
            }

            override fun mouseExited(e: MouseEvent?) {
                myNotShowIconLabel.border = defaultBorder
            }

            override fun mouseClicked(e: MouseEvent?) {
                myNotShowIconLabel.border = defaultBorder
                val timer = Timer(50) { myNotShowIconLabel.border = selectedBorder }
                endLifetime -= 1
                hidingCallback()
                if (endLifetime <= 0) {
                    this@ArendManipulateImplicitArgumentComponent.isVisible = false
                } else {
                    timer.start()
                }
            }
        })
    }
}

private class LocalComponentHint constructor(component: JComponent) : LightweightHint(component) {
    override fun show(parentComponent: JComponent, x: Int, y: Int, focusBackComponent: JComponent, hintHint: HintHint) {
        showImpl(parentComponent, x, y, focusBackComponent)
    }

    private fun showImpl(parentComponent: JComponent, x: Int, y: Int, focusBackComponent: JComponent) {
        if (!parentComponent.isShowing) return
        super.show(parentComponent, x, y, focusBackComponent, HintHint(parentComponent, Point(x, y)))
    }
}