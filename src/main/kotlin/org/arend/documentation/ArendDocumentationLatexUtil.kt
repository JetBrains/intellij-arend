package org.arend.documentation

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import org.arend.util.ArendBundle
import org.scilab.forge.jlatexmath.ParseException
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JLabel
import javax.swing.UIManager

internal var counterLatexImages = 0
internal const val LATEX_IMAGES_DIR = "latex-images"

internal fun getHtmlLatexCode(title: String, latexCode: String, project: Project, offset: Int, isNewlineLatexCode: Boolean): String {
    try {
        val font = UIManager.getDefaults().getFont("Label.font").size.toFloat()

        val formula = TeXFormula(latexCode)
        val icon: TeXIcon = formula.TeXIconBuilder()
            .setStyle(TeXConstants.STYLE_DISPLAY)
            .setSize(font)
            .build()
        val image = ImageUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)

        val label = JLabel()
        label.setForeground(UIManager.getColor("PopupMenu.foreground"))

        val graphics = image.createGraphics()
        graphics.color = EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.DOCUMENTATION_COLOR)
        graphics.fillRect(0, 0, icon.iconWidth, icon.iconHeight)

        icon.paintIcon(label, graphics, 0, 0)

        val latexImagesDir = File(LATEX_IMAGES_DIR).apply {
            mkdir()
        }
        val file = File(latexImagesDir.path + File.separator + title + ".png")
        ImageIO.write(image, "png", file.getAbsoluteFile())

        return "<img ${if (isNewlineLatexCode) "style=\"margin: 0 auto;display: block;\"" else "style=\"vertical-align: middle;margin: 1;\""} " +
                "src=\"file:///${file.absolutePath}\" title=$title width=\"${icon.iconWidth}\" height=\"${icon.iconHeight}\">"
    } catch (e: Exception) {
        if (e is ParseException) {
            val notificationManager = SingletonNotificationManager(notificationGroup.displayId, NotificationType.WARNING)

            val notificationAction = NotificationAction.createSimpleExpiring(ArendBundle.message("arend.click.to.set.cursor.latex")) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor ?: return@createSimpleExpiring
                val caretModel = editor.caretModel
                caretModel.moveToOffset(offset)
            }

            e.message?.let {
                notificationManager.notify("LaTeX parsing warning", it, project) { notification ->
                    notification.setSuggestionType(true).addAction(notificationAction)
                }
            }
        } else {
            LOG.error(e)
        }
    }
    return ""
}
