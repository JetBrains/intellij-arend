package org.arend.toolWindow.errors

import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.Project
import com.intellij.ui.LanguageTextField
import com.intellij.ui.components.JBLabel
import org.arend.ext.prettyprinting.doc.*
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class JDocBuilder(private val project: Project) : DocVisitor<Void?, JComponent> {
    override fun visitVList(list: VListDoc, params: Void?): JComponent {
        val panel = JPanel()
        val box = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.layout = box
        for (doc in list.docs) {
            panel.add(doc.accept(this, null))
        }
        panel.alignmentX = JLabel.LEFT_ALIGNMENT
        panel.alignmentY = JLabel.TOP_ALIGNMENT
        return panel
    }

    private fun horizontal(list: Collection<Doc>): JComponent {
        val panel = JPanel()
        val box = BoxLayout(panel, BoxLayout.X_AXIS)
        panel.layout = box
        for (doc in list) {
            panel.add(doc.accept(this, null))
        }
        panel.alignmentX = JLabel.LEFT_ALIGNMENT
        panel.alignmentY = JLabel.TOP_ALIGNMENT
        return panel
    }

    override fun visitHList(doc: HListDoc, params: Void?) = horizontal(doc.docs)

    override fun visitText(doc: TextDoc, params: Void?) =
        JBLabel(doc.text).also {
            it.alignmentX = JLabel.LEFT_ALIGNMENT
            it.alignmentY = JLabel.TOP_ALIGNMENT
        }

    override fun visitHang(doc: HangDoc, params: Void?): JComponent =
        if (doc.bottom.isSingleLine) {
            horizontal(listOf(doc.top, doc.bottom))
        } else {
            DocFactory.vList(doc.top, doc.bottom).accept(this, null)
        }

    override fun visitReference(doc: ReferenceDoc, params: Void?): JComponent {
        /*
        val data = (doc.reference as? DataContainer)?.data
        val ref = data as? SmartPsiElementPointer<*> ?:
            (data as? PsiElement ?: doc.reference as? PsiElement)?.let { runReadAction { SmartPointerManager.createPointer(it) } }
        */
        return JBLabel(doc.reference.refName).also {
            it.alignmentX = JLabel.LEFT_ALIGNMENT
            it.alignmentY = JLabel.TOP_ALIGNMENT
        }
    }

    override fun visitCaching(doc: CachingDoc, params: Void?): JComponent {
        /*
        val factory = EditorFactory.getInstance()
        val document = factory.createDocument(doc.string)
        val editor = factory.createViewer(document, project)
        val textField = editor.component
        */
        val textField = LanguageTextField(PlainTextLanguage.INSTANCE, project, doc.string)
        textField.document.setReadOnly(true)
        textField.alignmentX = JLabel.LEFT_ALIGNMENT
        textField.alignmentY = JLabel.TOP_ALIGNMENT
        return textField
    }

    override fun visitTermLine(doc: TermLineDoc, params: Void?): JComponent {
        val textField = LanguageTextField(PlainTextLanguage.INSTANCE, project, doc.text, true)
        textField.document.setReadOnly(true)
        textField.alignmentX = JLabel.LEFT_ALIGNMENT
        textField.alignmentY = JLabel.TOP_ALIGNMENT
        return textField
    }

    override fun visitPattern(doc: PatternDoc, params: Void?): JComponent {
        val textField = LanguageTextField(PlainTextLanguage.INSTANCE, project, doc.text, true)
        textField.document.setReadOnly(true)
        textField.alignmentX = JLabel.LEFT_ALIGNMENT
        textField.alignmentY = JLabel.TOP_ALIGNMENT
        return textField
    }
}