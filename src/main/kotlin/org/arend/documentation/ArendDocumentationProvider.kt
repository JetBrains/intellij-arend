package org.arend.documentation

import com.intellij.ide.IdeEventQueue
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import org.arend.codeInsight.ArendCodeInsightUtils
import org.arend.documentation.ArendKeyword.Companion.isArendKeyword
import org.arend.ext.module.LongName
import org.arend.naming.reference.FieldReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.naming.scope.Scope
import org.arend.psi.ArendFile
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.ext.*
import org.arend.term.abs.Abstract
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.AWTEvent
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.SwingUtilities
import javax.swing.UIManager


class ArendDocumentationProvider : AbstractDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, false)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, true)

    override fun getDocumentationElementForLink(psiManager: PsiManager?, link: String, context: PsiElement?): PsiElement? {
        val longName = link.removePrefix(FULL_PREFIX)
        val scope = ArendDocComment.getScope((context as? ArendDocComment)?.owner ?: context) ?: return null
        val ref = RedirectingReferable.getOriginalReferable(Scope.resolveName(scope, LongName.fromString(longName).toList()))
        return if (ref is PsiReferable && longName.length != link.length) ref.documentation else ref as? PsiElement
    }

    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?): PsiElement? {
        if (contextElement?.isArendKeyword() == true) {
            return contextElement
        }
        return null
    }

    private fun generateDocForKeywords(element: PsiElement): String {
        return buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                }
            }
            wrapTag("body") {
                wrap(DEFINITION_START, DEFINITION_END) {
                    wrapTag("b") {
                        html(element.text)
                    }
                }

                wrap(CONTENT_START, CONTENT_END) {
                    getDescriptionForKeyword(element)
                }
            }
        } }
    }

    private fun generateDoc(element: PsiElement, originalElement: PsiElement?, withDocComments: Boolean): String? {
        val ref = element as? PsiReferable ?: (element as? ArendDocComment)?.owner
        ?: return if (element.isArendKeyword()) generateDocForKeywords(element) else null
        val docCommentInfo = ArendDocCommentInfo(hasLatexCode = false, wasPrevRow = false)
        val html = buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                    val font = UIManager.getDefaults().getFont("Label.font").size
                    append(".row { display: flex;align-items: center; font-size: $font;}")
                    append(".definition { font-size: $font;}")
                    append(".content { font-size: $font;}")
                }
            }

            append("<body ")
            append("style=\"color:${getHtmlRgbFormat(UIManager.getColor("MenuItem.foreground").rgb)};" +
                    "background-color:${getHtmlRgbFormat(
                        EditorColorsManager.getInstance().globalScheme.getColor(
                            EditorColors.DOCUMENTATION_COLOR)?.rgb ?: 0)};\">")
            wrap(DEFINITION_START, DEFINITION_END) {
                generateDefinition(ref)
            }

            wrap(CONTENT_START, CONTENT_END) {
                generateContent(ref, originalElement)
            }

            if (withDocComments) {
                val doc = element as? ArendDocComment ?: ref.documentation
                if (doc != null) {
                    File(LATEX_IMAGES_DIR).deleteRecursively()
                    docCommentInfo.hasLatexCode = hasLatexCode(doc)

                    append(CONTENT_START)
                    generateDocComments(ref, doc, element is ArendDocComment, docCommentInfo)
                    append(CONTENT_END)
                }
            }
            append("</body>")
        } }
        if (docCommentInfo.hasLatexCode) {
            showInCefBrowser(html)
        }
        return html
    }

    private fun showInCefBrowser(html: String) {
        val browser = JBCefBrowser()
        browser.component.preferredSize = Dimension(1, 1)

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(browser.component, null)
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()

        val listener = AWTEventListener { event: AWTEvent ->
            if (event is MouseEvent && popup.isVisible && !Rectangle(popup.locationOnScreen, popup.size).contains(event.locationOnScreen)) {
                popup.closeOk(event)
            }
        }

        IdeEventQueue.getInstance().addDispatcher({ event ->
            if (event is MouseEvent && event.getID() === MouseEvent.MOUSE_MOVED) {
                SwingUtilities.invokeLater { listener.eventDispatched(event) }
            }
            false
        }, null)

        val queryWidth = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val queryHeight = JBCefJSQuery.create(browser as JBCefBrowserBase)

        val cefLoadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                val jsWidth = """
                    var width = document.documentElement.scrollWidth;
                    ${queryWidth.inject("width")}
                """
                val jsHeight = """
                    var height = document.documentElement.scrollHeight;
                    ${queryHeight.inject("height")}
                """
                browser?.executeJavaScript(jsWidth, browser.url, 0)
                browser?.executeJavaScript(jsHeight, browser.url, 0)
            }
        }

        browser.jbCefClient.addLoadHandler(cefLoadHandler, browser.cefBrowser)
        browser.loadHTML(html)

        val response = JBCefJSQuery.Response("")
        queryWidth.addHandler { result ->
            val width = result.toIntOrNull() ?: return@addHandler response
            try {
                popup.width = width + WIDTH_PADDING
            } catch (e: Exception) {
                e.printStackTrace()
            }
            response
        }
        queryHeight.addHandler { result ->
            val height = result.toIntOrNull() ?: return@addHandler response
            try {
                popup.height = height
            } catch (e: Exception) {
                e.printStackTrace()
            }
            response
        }

        invokeLater {
            popup.show(RelativePoint(MouseInfo.getPointerInfo().location))
        }
    }

    private fun StringBuilder.generateDefinition(element: PsiReferable) {
        wrapTag("b") {
            html(element.textRepresentation())
        }
        (element as? ReferableBase<*>)?.alias?.aliasIdentifier?.id?.text?.let {
            html(" $it")
        }

        for (parameter in ArendCodeInsightUtils.getAllParametersForReferable(element, null)?.first ?: emptyList()) {
            html(" $parameter")
        }

        element.psiElementType?.let { html(" : ${it.text}") }
    }

    private fun StringBuilder.generatePrecedence(prec: ArendPrec) {
        append(", ")
        append(prec.firstChild.text.drop(1))
        prec.number?.let { priority ->
            append(" ")
            append(priority.text)
        }
    }

    private fun StringBuilder.generateContent(element: PsiElement, originalElement: PsiElement?) {
        wrapTag("em") {
            getType(element)?.let { append(it) }
        }
        getSuperType(element)?.let { append(it) }

        (element as? ReferableBase<*>)?.prec?.let { generatePrecedence(it) }

        (element as? ReferableBase<*>)?.alias?.let { alias ->
            alias.prec?.let {
                generatePrecedence(it)
                html(" ${alias.aliasIdentifier?.id?.text ?: ""}")
            }
        }

        if (element !is PsiFile) {
            getSourceFileName(element, originalElement)?.let {
                val fileName = it.htmlEscape()
                append(", defined in ")
                wrapTag("b") {
                    append(fileName)
                }
            }
        }
    }

    private fun getType(element: PsiElement): String? = when (element) {
        is ArendDefClass -> if (element.isRecord) "record" else "class"
        is FieldReferable -> "field"
        is ArendDefInstance -> "instance"
        is ArendClassImplement -> "implementation"
        is ArendDefData -> "data"
        is ArendConstructor -> "constructor"
        is ArendDefFunction, is ArendCoClauseDef -> "function"
        is ArendDefModule -> "module"
        is ArendDefMeta -> "meta"
        is ArendLetClause -> "let"
        is ArendDefIdentifier -> if (element.parent is ArendLetClause) "let" else "variable"
        is ArendLevelIdentifier -> "level"
        is PsiFile -> "file"
        else -> null
    }

    private fun getSuperType(element: PsiElement): String? = when (element) {
        is FieldReferable ->
            (element.locatedReferableParent as? ArendDefClass)?.let {
                " of ${if (it.isRecord) "record" else "class"} <b>${it.name ?: return@let null}</b>"
            }
        is ArendConstructor -> (element.locatedReferableParent as? ArendDefData)?.name?.let { " of data <b> $it </b>" }
        else -> null
    }

    private fun getSourceFileName(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is PsiLocatedReferable) {
            val file = element.containingFile.originalFile
            if (file != originalElement?.containingFile?.originalFile) {
                return (file as? ArendFile)?.fullName ?: file.name
            }
        }

        return null
    }

    companion object {
        const val WIDTH_PADDING = 60
    }
}
