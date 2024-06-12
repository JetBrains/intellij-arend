package org.arend.search.proof

import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.UIUtil
import org.arend.highlight.ArendHighlightingColors
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.*

class ArendProofSearchRenderer(val project: Project) : ListCellRenderer<ProofSearchUIEntry>, Disposable {
    private val panel: JPanel = OpaquePanel(SearchEverywherePsiRenderer.SELayout())
    private val iconPanel: JPanel = JPanel(BorderLayout())
    private val label: JBLabel = JBLabel()
    private val textArea = JEditorPane()

    private val selectionFgColor: Color
    private val selectionBgColor: Color

    val editor: EditorEx

    init {
        textArea.contentType = "text/html"
        textArea.border = BORDER
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        textArea.font = JBTextArea().font
        iconPanel.add(label, BorderLayout.NORTH)
        panel.add(iconPanel, BorderLayout.WEST)
        panel.add(textArea, BorderLayout.CENTER)
        editor = EditorFactory.getInstance().createViewer(DocumentImpl(" ", true), project) as EditorEx
        with(editor.settings) {
            isRightMarginShown = false
            isLineNumbersShown = false
            isLineMarkerAreaShown = false
            isRefrainFromScrolling = true
            isCaretRowShown = false
            isUseSoftWraps = true
            setGutterIconsShown(false)
            additionalLinesCount = 0
            additionalColumnsCount = 1
            isFoldingOutlineShown = false
            isVirtualSpace = false
        }
        editor.headerComponent = null
        editor.setCaretEnabled(false)
        editor.setHorizontalScrollbarVisible(false)
        editor.setVerticalScrollbarVisible(false)
        editor.isRendererMode = true
        selectionFgColor =
            editor.colorsScheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR) ?: editor.component.foreground
        selectionBgColor =
            editor.colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR) ?: UIUtil.getListSelectionBackground(
                true
            )
    }

    override fun getListCellRendererComponent(
        list: JList<out ProofSearchUIEntry>,
        value: ProofSearchUIEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        return runReadAction {
            val bgColor = if (isSelected) selectionBgColor else UIUtil.getListBackground()
            val fgColor = if (isSelected) selectionFgColor else list.foreground
            when (value) {
                is DefElement -> {
                    val (def, renderingInfo) = value.entry
                    val (parameterRenders, codomainRender) = renderingInfo
                    val identifierRange: TextRange
                    val parameterRanges: List<TextRange>
                    val typeRange: TextRange
                    val locationRange: TextRange
                    val text = buildString {
                        append("${def.name} : ")
                        identifierRange = TextRange(0, length - 3)
                        val ranges = mutableListOf<TextRange>()
                        for (parameterIndex in parameterRenders.indices) {
                            append("(")
                            val start = length
                            append(parameterRenders[parameterIndex].typeRep)
                            ranges.add(TextRange(start, length))
                            append(")")
                            append(" -> ")
                        }
                        parameterRanges = ranges
                        val start = length
                        append(codomainRender.typeRep)
                        typeRange = TextRange(start, length)
                        val location = getCompleteModuleLocation(def)
                        locationRange = if (location != null) {
                            append(" of $location")
                            TextRange(typeRange.endOffset, length)
                        } else {
                            TextRange(0, 0)
                        }
                    }
                    if (editor.document.text != text) {
                        editor.document.deleteString(0, editor.document.textLength - 1)
                        editor.document.insertString(0, text)
                    }
                    editor.backgroundColor = bgColor

                    setupHighlighting(isSelected, fgColor, text, identifierRange, parameterRanges, parameterRenders, typeRange, codomainRender, locationRange)
                    editor.component
                }
                is MoreElement -> {
                    textArea.background = bgColor
                    textArea.foreground = fgColor
                    panel.font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
                    textArea.text = buildMoreHTML(fgColor)
                    panel.border = null
                    label.icon = null
                    panel
                }
            }
        }
    }

    private fun setupHighlighting(
        isSelected: Boolean,
        fgColor: Color?,
        text: String,
        identifierRange: TextRange,
        parameterRanges: List<TextRange>,
        parameterRenders: List<ProofSearchHighlightingData>,
        typeRange: TextRange,
        codomain: ProofSearchHighlightingData,
        locationRange: TextRange
    ) {
        val allBigRanges = parameterRanges + typeRange
        val allHighlightings = parameterRenders + codomain
        editor.markupModel.removeAllHighlighters()
        if (isSelected) {
            editor.markupModel.addRangeHighlighter(
                0,
                text.length,
                HighlighterLayer.SYNTAX - 1,
                TextAttributes(fgColor, null, null, null, Font.PLAIN),
                HighlighterTargetArea.EXACT_RANGE
            )
            for ((bigRange, data) in allBigRanges.zip(allHighlightings)) {
                for (matchRange in data.match) {
                    val range = matchRange.shiftRight(bigRange.startOffset)
                    editor.markupModel.addRangeHighlighter(
                        range.startOffset,
                        range.endOffset,
                        HighlighterLayer.SYNTAX - 2,
                        TextAttributes(fgColor, null, fgColor, EffectType.BOXED, Font.PLAIN),
                        HighlighterTargetArea.EXACT_RANGE
                    )
                }
            }
        } else {
            for (it in allBigRanges) {
                editor.addHighlighting(it, EditorColors.INJECTED_LANGUAGE_FRAGMENT)
            }
        }

        for ((bigRange, data) in allBigRanges.zip(allHighlightings)) {
            for (kw in data.keywords) {
                editor.addHighlighting(
                    kw.shiftRight(bigRange.startOffset),
                    ArendHighlightingColors.KEYWORD.textAttributesKey
                )
            }
        }
        editor.addHighlighting(identifierRange, ArendHighlightingColors.DECLARATION.textAttributesKey)
        editor.addHighlighting(locationRange, ArendHighlightingColors.LINE_COMMENT.textAttributesKey)
    }

    private fun Editor.addHighlighting(range: TextRange, attributes: TextAttributesKey) {
        markupModel.addRangeHighlighter(
            attributes,
            range.startOffset,
            range.endOffset,
            HighlighterLayer.SYNTAX,
            HighlighterTargetArea.EXACT_RANGE
        )
    }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}

private val BORDER = BorderFactory.createEmptyBorder(3, 0, 5, 0)

private fun Color.asHex(): String {
    return "#${Integer.toHexString(red)}${Integer.toHexString(blue)}${Integer.toHexString(green)}"
}

private fun buildMoreHTML(nameColor: Color): String = """
       <html>
       <body>
       <span style="font-size: small; color: ${nameColor.asHex()}">... more</span>
       </body>
       </html>
    """.trimIndent()