package org.arend.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import org.arend.ArendIcons
import org.arend.injection.ConcreteLambdaParameter
import org.arend.injection.ConcreteRefExpr
import org.arend.injection.RevealableFragment
import org.arend.util.ArendBundle
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.Border

fun showExposeArgumentsHint(editor: Editor, fragment: RevealableFragment, callback: () -> Unit) {
    val offset = editor.caretModel.offset
    val visualPosition = editor.offsetToVisualPosition(offset)
    val point = editor.visualPositionToXY(visualPosition)
        .apply { move(x + 2, y - (editor.lineHeight.toDouble() * 2.5).toInt()) }
    val component = ArendExposeImplicitArgumentComponent(fragment, callback)
    val flags =
        HintManager.HIDE_BY_ANY_KEY or HintManager.UPDATE_BY_SCROLLING or HintManager.HIDE_IF_OUT_OF_EDITOR or HintManager.DONT_CONSUME_ESCAPE

    HintManagerImpl.getInstanceImpl().showEditorHint(
        LocalComponentHint(component), editor,
        Point(
            editor.contentComponent.locationOnScreen.x + point.x,
            editor.contentComponent.locationOnScreen.y + point.y
        ), flags, -1, true
    )
}

private class ArendExposeImplicitArgumentComponent(private val fragment: RevealableFragment, callback: () -> Unit) :
    JPanel() {

    override fun addMouseListener(l: MouseListener?) {}

    private val myIconLabel =
        JLabel(ArendIcons.SHOW).apply { isOpaque = false }

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
    private var lifetime = fragment.lifetime

    init {
        layout = BorderLayout()
        isOpaque = true
        myIconLabel.border = defaultBorder
        add(myIconLabel, BorderLayout.CENTER)
        myIconLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                myIconLabel.border = selectedBorder
                myIconLabel.toolTipText = when (fragment.result) {
                    is ConcreteLambdaParameter -> ArendBundle.message("arend.reveal.lambda.parameter.type")
                    is ConcreteRefExpr -> ArendBundle.message("arend.reveal.implicit.argument")
                } + " (${
                    KeymapUtil.getFirstKeyboardShortcutText(
                        ActionManager.getInstance().getAction("Arend.PrettyPrint.RevealImplicitInformation") as AnAction
                    )
                })"
            }

            override fun mouseExited(e: MouseEvent?) {
                myIconLabel.border = defaultBorder
            }

            override fun mouseClicked(e: MouseEvent?) {
                lifetime -= 1
                callback()
                if (lifetime <= 0) {
                    this@ArendExposeImplicitArgumentComponent.isVisible = false
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