package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.UIUtil
import okhttp3.internal.toHexString
import org.apache.commons.lang.StringEscapeUtils.escapeHtml
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.*
import org.arend.term.concrete.Concrete
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.*
import javax.swing.border.Border

class ArendSignatureSearchRenderer : ListCellRenderer<SignatureSearchUIEntry> {
    private val panel: JPanel = OpaquePanel(SearchEverywherePsiRenderer.SELayout())
    private val iconPanel: JPanel = JPanel(BorderLayout())
    private val label: JBLabel = JBLabel()
    private val textArea = JEditorPane()

    init {
        iconPanel.add(label, BorderLayout.NORTH)
        panel.add(iconPanel, BorderLayout.WEST)
        panel.add(textArea, BorderLayout.CENTER)
    }

    override fun getListCellRendererComponent(
        list: JList<out SignatureSearchUIEntry>,
        value: SignatureSearchUIEntry,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val textColor = if (isSelected) list.selectionForeground else list.foreground
        val bgColor = getBackgroundColor(isSelected, value)

        textArea.contentType = "text/html"
        textArea.border = BORDER
        textArea.foreground = textColor
        textArea.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        textArea.font = JBTextArea().font
        textArea.background = bgColor

        panel.background = bgColor
        label.background = bgColor
        iconPanel.background = bgColor

        addContent(value, textColor, isSelected)
        return panel
    }

    fun addContent(value: SignatureSearchUIEntry, textColor: Color, isSelected: Boolean) = when (value) {
        is MoreElement -> {
            panel.font = UIUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL))
            textArea.text = buildMoreHTML(textColor)
            panel.border = null
            label.icon = null
        }
        is DefElement -> {
            val (def, type) = value.entry
            val auxiliaryTextColor = if (isSelected) textColor else UIUtil.getInactiveTextColor()
            textArea.text = buildHtml(def, type, auxiliaryTextColor)
            val icon = getIcon(def)
            label.icon = icon
            label.border = generateLabelBorder(textArea, icon)
        }
    }
}

private val BORDER = BorderFactory.createEmptyBorder(3, 0, 5, 0)

private fun buildHtml(
    def: ReferableAdapter<*>,
    type: Concrete.Expression,
    nameColor: Color
): String? {
    val name = def.name ?: return null
    val typeRepresentation = (if (def is FunctionDefinitionAdapter) def.resultType?.text else null) ?: type.toString()
    val locationText = (def.containingFile as ArendFile).moduleLocation?.modulePath?.toString() ?: ""
    return buildDefinitionHTML(name, typeRepresentation, locationText, nameColor)
}

private fun Color.asHex(): String {
    return "#${red.toHexString()}${blue.toHexString()}${green.toHexString()}"
}

private fun buildDefinitionHTML(
    name: String,
    type: String,
    locationText: String,
    secondaryColor: Color
): String {
    val locationRepresentation =
        if (locationText != "") "<span style=\"color: ${secondaryColor.asHex()}\">(in ${escapeHtml(locationText)})</span>" else ""
    return """
           <html>
           <body>
           ${escapeHtml(name)} : ${optionalNewline(type)} <b>${escapeHtml(type).replace("\n", "<br>")}</b> $locationRepresentation
           </body>
           </html>
        """.trimIndent()
}

fun optionalNewline(type: String): String = if (type.contains('\n')) "\n" else ""

private fun buildMoreHTML(nameColor: Color): String = """
       <html>
       <body>
       <span style="font-size: small; color: ${nameColor.asHex()}">... more</span>
       </body>
       </html>
    """.trimIndent()

private fun getBackgroundColor(isSelected: Boolean, element: SignatureSearchUIEntry): Color =
    when (isSelected) {
        true -> UIUtil.getListSelectionBackground(true)
        false -> when (element) {
            is DefElement -> {
                val file = PsiUtilCore.getVirtualFile(element.entry.def)
                if (file != null) {
                    VfsPresentationUtil.getFileBackgroundColor(element.entry.def.project, file)
                        ?: UIUtil.getListBackground()
                } else {
                    UIUtil.getListBackground()
                }
            }
            is MoreElement -> UIUtil.getListBackground()
        }
    }

private fun getIcon(def: PsiReferable): Icon = when (def) {
    is CoClauseDefAdapter -> AllIcons.General.Show_to_implement
    else -> def.getIcon(0)
}

private fun generateLabelBorder(textArea: JEditorPane, icon: Icon): Border =
    BorderFactory.createEmptyBorder(1 + textArea.getFontMetrics(textArea.font).height - icon.iconHeight, 0, 5, 2)
