package org.arend.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.PriorityQuestionAction
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.HintHint
import com.intellij.ui.LightweightHint
import org.arend.ArendIcons
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

fun showExposeArgumentsHint(editor: Editor, callback: (Int) -> Unit) {
    val offset = editor.caretModel.offset
    val visualPosition = editor.offsetToVisualPosition(offset)
    val point = editor.visualPositionToXY(visualPosition)
        .apply { move(x + 2, y - (editor.lineHeight.toDouble() * 2.5).toInt()) }
    val component = ArendExposeImplicitArgumentComponent()

    val action: QuestionAction = object : PriorityQuestionAction {
        override fun execute(): Boolean {
            callback(offset)
            return true
        }

        override fun getPriority(): Int {
            return -10
        }
    }

    HintManagerImpl.getInstanceImpl().showQuestionHint(
        editor,
        Point(
            editor.contentComponent.locationOnScreen.x + point.x,
            editor.contentComponent.locationOnScreen.y + point.y
        ),
        offset,
        offset,
        LocalComponentHint(component),
        action,
        HintManager.ABOVE
    )
}

private class ArendExposeImplicitArgumentComponent : JPanel() {

    override fun addMouseListener(l: MouseListener?) {}

    private val myIconLabel =
        JLabel(ArendIcons.SHOW_IMPLICITS).apply { isOpaque = false }

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

    init {
        layout = BorderLayout()
        isOpaque = true
        myIconLabel.border = defaultBorder
        add(myIconLabel, BorderLayout.CENTER)
        myIconLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent?) {
                myIconLabel.border = selectedBorder
                myIconLabel.toolTipText = "Reveal an implicit argument"
            }

            override fun mouseExited(e: MouseEvent?) {
                myIconLabel.border = defaultBorder
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